from datetime import datetime, timezone

from sqlalchemy import BigInteger, Boolean, DateTime, Float, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from .db import Base


def now() -> datetime:
    return datetime.now(timezone.utc)


class Escalation(Base):
    """An escalated scan. Once answered, (images, expert label) IS a training
    sample — the review queue and the dataset pipeline are the same thing."""

    __tablename__ = "escalation"

    id: Mapped[str] = mapped_column(String(64), primary_key=True)
    scan_id: Mapped[str] = mapped_column(String(64), index=True)
    device_model: Mapped[str] = mapped_column(String(128), default="")
    app_version: Mapped[str] = mapped_column(String(64), default="")
    image_keys: Mapped[str] = mapped_column(Text, default="")  # ';'-joined storage keys
    status: Mapped[str] = mapped_column(String(24), default="PENDING", index=True)
    expert_disease_id: Mapped[str | None] = mapped_column(String(64), nullable=True)
    expert_note: Mapped[str | None] = mapped_column(Text, nullable=True)
    expert_name: Mapped[str | None] = mapped_column(String(128), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)
    answered_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)


class Outbreak(Base):
    """Geohash-5 cell reports. Centroid lat/lon are derived from the geohash —
    the client never sends finer location. A PostGIS geometry column is added by
    migration 0001 (production only) for future district-polygon joins."""

    __tablename__ = "outbreak"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    geohash5: Mapped[str] = mapped_column(String(5), index=True)
    crop_id: Mapped[str] = mapped_column(String(64), index=True)
    disease_id: Mapped[str] = mapped_column(String(64), index=True)
    confirmed_by: Mapped[str] = mapped_column(String(24))
    client_created_at: Mapped[int] = mapped_column(BigInteger)  # epoch millis from device
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    received_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class ModelManifest(Base):
    """OTA model registry: the app polls this to replace models without a release."""

    __tablename__ = "model_manifest"

    name: Mapped[str] = mapped_column(String(128), primary_key=True)
    version: Mapped[int] = mapped_column(Integer, default=1)
    url: Mapped[str] = mapped_column(Text)
    sha256: Mapped[str] = mapped_column(String(64))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now)


class LabelClaimRow(Base):
    """Server-side label-claim registry for delta sync. Same no-invention rule as
    the app: every row carries its gazette notification reference."""

    __tablename__ = "label_claim"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    product_id: Mapped[str] = mapped_column(String(64), index=True)
    crop_id: Mapped[str] = mapped_column(String(64))
    pest_id: Mapped[str] = mapped_column(String(64))
    dose_value: Mapped[float] = mapped_column(Float)
    dose_unit: Mapped[str] = mapped_column(String(16))
    dilution_l_per_ha: Mapped[float] = mapped_column(Float)
    phi_days: Mapped[int] = mapped_column(Integer)
    source_notification_ref: Mapped[str] = mapped_column(String(128))
    effective_from: Mapped[str] = mapped_column(String(10))  # ISO date
    effective_to: Mapped[str | None] = mapped_column(String(10), nullable=True)
    deleted: Mapped[bool] = mapped_column(Boolean, default=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now, onupdate=now, index=True)
