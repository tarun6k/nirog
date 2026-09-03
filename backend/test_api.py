import os
import tempfile

os.environ["NIROG_DB_URL"] = f"sqlite:///{tempfile.mkdtemp()}/test.db"
os.environ["NIROG_UPLOAD_DIR"] = tempfile.mkdtemp()
os.environ["NIROG_AUTO_CREATE"] = "1"

from fastapi.testclient import TestClient  # noqa: E402

from app.main import app  # noqa: E402

client = TestClient(app)


def test_escalation_roundtrip():
    r = client.post(
        "/v1/escalations",
        data={"ticketId": "t1", "scanId": "s1", "deviceModel": "TestPhone"},
        files={"image0": ("a.jpg", b"\xff\xd8fake", "image/jpeg")},
    )
    assert r.status_code == 200 and r.json()["status"] == "PENDING"

    # idempotent retry (WorkManager re-runs)
    assert client.post("/v1/escalations", data={"ticketId": "t1", "scanId": "s1"}).json()["id"] == "t1"

    r = client.post("/v1/escalations/t1/answer", data={"diseaseId": "yellow_rust", "expertName": "Dr V"})
    assert r.json()["status"] == "ANSWERED"

    r = client.get("/v1/escalations/t1")
    assert r.json()["expertDiseaseId"] == "yellow_rust"

    samples = client.get("/v1/training-samples").json()
    assert samples[0]["label"] == "yellow_rust" and samples[0]["imageKeys"]


def test_outbreak_aggregate():
    reports = [
        {"geohash5": "ttnfv", "cropId": "wheat", "diseaseId": "yellow_rust", "createdAt": 1},
        {"geohash5": "ttnfv", "cropId": "wheat", "diseaseId": "yellow_rust", "createdAt": 2},
        {"geohash5": "u4pru", "cropId": "wheat", "diseaseId": "aphid", "createdAt": 3},
    ]
    assert client.post("/v1/outbreaks", json=reports).json()["accepted"] == 3
    agg = client.get("/v1/outbreaks/aggregate", params={"cropId": "wheat"}).json()
    counts = {(a["geohash5"], a["diseaseId"]): a["count"] for a in agg}
    assert counts[("ttnfv", "yellow_rust")] == 2
    assert counts[("u4pru", "aphid")] == 1


def test_label_claim_delta_and_manifest():
    assert client.get("/v1/label-claims").json() == []
    assert client.get("/v1/models/manifest").json() == []

    from app.db import SessionLocal
    from app.models import LabelClaimRow

    with SessionLocal() as s:
        s.add(LabelClaimRow(
            product_id="P1", crop_id="wheat", pest_id="yellow_rust",
            dose_value=2.0, dose_unit="ML_PER_L", dilution_l_per_ha=500.0, phi_days=7,
            source_notification_ref="SO 1(E)", effective_from="2025-01-01",
        ))
        s.add(LabelClaimRow(
            product_id="P2", crop_id="wheat", pest_id="yellow_rust",
            dose_value=1.0, dose_unit="ML_PER_L", dilution_l_per_ha=500.0, phi_days=14,
            source_notification_ref="SO 2(E)", effective_from="2024-01-01", deleted=True,
        ))
        s.commit()

    rows = client.get("/v1/label-claims").json()
    assert {r["productId"]: r["deleted"] for r in rows} == {"P1": False, "P2": True}

    # a `since` after every updatedAt returns nothing
    latest = max(r["updatedAt"] for r in rows)
    assert client.get("/v1/label-claims", params={"since": latest}).json() == []


def test_review_queue_renders():
    assert "Nirog expert queue" in client.get("/review").text
