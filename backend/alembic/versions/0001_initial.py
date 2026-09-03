"""initial schema + PostGIS point on outbreak

Revision ID: 0001
Revises:
Create Date: 2026-09-03
"""

from alembic import op

from app.db import Base

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    bind = op.get_bind()
    # ponytail: migration 0001 reuses the model metadata instead of hand-copied
    # DDL; future migrations must be written out explicitly as models drift.
    Base.metadata.create_all(bind)
    if bind.dialect.name == "postgresql":
        op.execute("CREATE EXTENSION IF NOT EXISTS postgis")
        op.execute("ALTER TABLE outbreak ADD COLUMN IF NOT EXISTS geom geometry(Point, 4326)")


def downgrade() -> None:
    Base.metadata.drop_all(op.get_bind())
