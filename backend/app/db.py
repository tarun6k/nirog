import os

from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

# Postgres+PostGIS in production; tests override with sqlite via NIROG_DB_URL.
DB_URL = os.environ.get("NIROG_DB_URL", "postgresql+psycopg2://nirog:nirog@localhost/nirog")

engine = create_engine(DB_URL)
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


def get_session():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()
