import os
import uuid
from datetime import datetime, timedelta, timezone

from fastapi import Depends, FastAPI, Form, Header, HTTPException, Request, UploadFile
from fastapi.responses import HTMLResponse, RedirectResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from . import storage
from .db import Base, engine, get_session
from .geohash import centroid
from .models import Escalation, LabelClaimRow, ModelManifest, Outbreak, now

app = FastAPI(title="Nirog backend")

# Dev convenience; production schema is managed by Alembic.
if os.environ.get("NIROG_AUTO_CREATE", "1") == "1":
    Base.metadata.create_all(engine)

EXPERT_API_KEY = os.environ.get("NIROG_EXPERT_API_KEY", "")


def require_expert(x_api_key: str = Header(default="")) -> None:
    """Mutating expert endpoints need the key when one is configured."""
    if EXPERT_API_KEY and x_api_key != EXPERT_API_KEY:
        raise HTTPException(401, "bad api key")


# ── escalations: intake, status, expert answer ──────────────────────────────


@app.post("/v1/escalations")
async def escalation_intake(
    request: Request,
    ticketId: str = Form(...),
    scanId: str = Form(...),
    deviceModel: str = Form(""),
    appVersion: str = Form(""),
    session: Session = Depends(get_session),
):
    form = await request.form()
    keys = []
    for name, value in form.multi_items():
        if isinstance(value, UploadFile):
            key = f"escalations/{ticketId}/{name}.jpg"
            storage.put(key, await value.read())
            keys.append(key)
    existing = session.get(Escalation, ticketId)
    if existing:  # idempotent retries from WorkManager
        return {"id": ticketId, "status": existing.status}
    session.add(
        Escalation(
            id=ticketId, scan_id=scanId, device_model=deviceModel,
            app_version=appVersion, image_keys=";".join(keys),
        ),
    )
    session.commit()
    return {"id": ticketId, "status": "PENDING"}


@app.get("/v1/escalations/{ticket_id}")
def escalation_status(ticket_id: str, session: Session = Depends(get_session)):
    t = session.get(Escalation, ticket_id) or _404()
    return {
        "id": t.id,
        "status": t.status,
        "expertDiseaseId": t.expert_disease_id,
        "expertNote": t.expert_note,
        "answeredAt": t.answered_at.isoformat() if t.answered_at else None,
    }


@app.post("/v1/escalations/{ticket_id}/answer", dependencies=[Depends(require_expert)])
def escalation_answer(
    ticket_id: str,
    diseaseId: str = Form(...),
    note: str = Form(""),
    expertName: str = Form(""),
    session: Session = Depends(get_session),
):
    t = session.get(Escalation, ticket_id) or _404()
    t.expert_disease_id = diseaseId
    t.expert_note = note
    t.expert_name = expertName
    t.status = "ANSWERED"
    t.answered_at = now()
    session.commit()
    return {"id": t.id, "status": t.status}


@app.get("/v1/training-samples", dependencies=[Depends(require_expert)])
def training_samples(session: Session = Depends(get_session)):
    """Answered escalations ARE the training set — same rows, exported."""
    rows = session.scalars(select(Escalation).where(Escalation.status == "ANSWERED")).all()
    return [
        {"id": t.id, "imageKeys": t.image_keys.split(";"), "label": t.expert_disease_id,
         "expert": t.expert_name, "answeredAt": t.answered_at.isoformat()}
        for t in rows
    ]


# ── outbreaks: batch intake + aggregation ───────────────────────────────────


@app.post("/v1/outbreaks")
def outbreak_intake(reports: list[dict], session: Session = Depends(get_session)):
    for r in reports:
        gh = str(r["geohash5"])[:5]
        lat, lon = centroid(gh)
        session.add(
            Outbreak(
                geohash5=gh, crop_id=r["cropId"], disease_id=r["diseaseId"],
                confirmed_by=r.get("confirmedBy", "ON_DEVICE"),
                client_created_at=int(r["createdAt"]), lat=lat, lon=lon,
            ),
        )
    session.commit()
    return {"accepted": len(reports)}


@app.get("/v1/outbreaks/aggregate")
def outbreak_aggregate(cropId: str, days: int = 14, session: Session = Depends(get_session)):
    """Counts per geohash-5 cell, split confirmed (expert-verified) vs reported
    (on-device only). District-polygon joins come with district data."""
    since = datetime.now(timezone.utc) - timedelta(days=days)
    rows = session.execute(
        select(Outbreak.geohash5, Outbreak.disease_id, Outbreak.confirmed_by, func.count())
        .where(Outbreak.crop_id == cropId, Outbreak.received_at >= since)
        .group_by(Outbreak.geohash5, Outbreak.disease_id, Outbreak.confirmed_by),
    ).all()
    return [
        {"geohash5": g, "diseaseId": d, "confirmed": cb == "HUMAN", "count": c}
        for g, d, cb, c in rows
    ]


# ── OTA model manifest + label-claim delta sync ─────────────────────────────


@app.get("/v1/models/manifest")
def model_manifest(session: Session = Depends(get_session)):
    rows = session.scalars(select(ModelManifest)).all()
    return [{"name": m.name, "version": m.version, "url": m.url, "sha256": m.sha256} for m in rows]


@app.get("/v1/label-claims")
def label_claims(since: str = "1970-01-01T00:00:00+00:00", session: Session = Depends(get_session)):
    ts = datetime.fromisoformat(since)
    rows = session.scalars(select(LabelClaimRow).where(LabelClaimRow.updated_at > ts)).all()
    return [
        {"productId": r.product_id, "cropId": r.crop_id, "pestId": r.pest_id,
         "doseValue": r.dose_value, "doseUnit": r.dose_unit,
         "dilutionLPerHa": r.dilution_l_per_ha, "phiDays": r.phi_days,
         "sourceNotificationRef": r.source_notification_ref,
         "effectiveFrom": r.effective_from, "effectiveTo": r.effective_to,
         "deleted": r.deleted, "updatedAt": r.updated_at.isoformat()}
        for r in rows
    ]


# ── expert review queue: minimal internal web view ──────────────────────────


@app.get("/review", response_class=HTMLResponse)
def review_queue(session: Session = Depends(get_session)):
    pending = session.scalars(
        select(Escalation).where(Escalation.status == "PENDING").order_by(Escalation.created_at),
    ).all()
    items = "".join(
        f"""<li style="margin-bottom:24px;border:1px solid #ccc;padding:12px">
        <b>{t.id}</b> · {t.created_at:%Y-%m-%d %H:%M} · {t.device_model}<br>
        {"".join(f'<img src="/review/image?key={k}" width="160" style="margin:4px">' for k in t.image_keys.split(";") if k)}
        <form method="post" action="/review/{t.id}">
          <input name="diseaseId" placeholder="disease id" required>
          <input name="note" placeholder="note">
          <input name="expertName" placeholder="your name" required>
          <input name="apiKey" placeholder="api key" type="password">
          <button>Answer</button>
        </form></li>"""
        for t in pending
    )
    return f"<h1>Nirog expert queue ({len(pending)})</h1><ul>{items or '<i>empty</i>'}</ul>"


@app.get("/review/image")
def review_image(key: str):
    if not key.startswith("escalations/") or ".." in key:
        raise HTTPException(400)
    from fastapi.responses import Response

    return Response(storage.get(key), media_type="image/jpeg")


@app.post("/review/{ticket_id}")
def review_answer(
    ticket_id: str,
    diseaseId: str = Form(...),
    note: str = Form(""),
    expertName: str = Form(""),
    apiKey: str = Form(""),
    session: Session = Depends(get_session),
):
    if EXPERT_API_KEY and apiKey != EXPERT_API_KEY:
        raise HTTPException(401, "bad api key")
    escalation_answer(ticket_id, diseaseId, note, expertName, session)
    return RedirectResponse("/review", status_code=303)


def _404():
    raise HTTPException(404, "not found")
