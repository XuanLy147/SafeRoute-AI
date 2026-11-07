"""Legacy HTTP suites now run against explicitly selected real PostGIS fixtures."""

import os

import pytest
from sqlalchemy import create_engine
from sqlalchemy.engine import make_url
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import get_password_hash
from app.db.session import get_db
from app.main import app
from app.models.entities import User, UserRole


@pytest.fixture(autouse=True)
def legacy_database(request, monkeypatch, tmp_path):
    if request.module.__name__.split(".")[-1] not in {"test_auth_rbac", "test_reports", "test_votes_review"}:
        yield
        return
    url = os.environ.get("SAFEROUTE_TEST_DATABASE_URL")
    if not url or not (make_url(url).database or "").startswith("saferoute_test_"):
        pytest.fail("HTTP suites require disposable PostGIS; run scripts/qa/verify_postgis.py")
    engine = create_engine(url)
    monkeypatch.setattr(settings, "UPLOAD_DIR", str(tmp_path))
    with engine.connect() as conn:
        transaction = conn.begin()
        db = Session(bind=conn, join_transaction_mode="create_savepoint")
        for id_, email, role in [
            (1, "user", UserRole.USER),
            (2, "reviewer", UserRole.REVIEWER),
            (3, "admin", UserRole.ADMIN),
            (99, "tv2_user", UserRole.USER),
        ]:
            db.add(User(id=id_, email=email + "@saferoute.ai", password_hash=get_password_hash("123456"), role=role))
        db.flush()

        def override():
            try:
                yield db
            except Exception:
                db.rollback()
                raise

        previous = app.dependency_overrides.copy()
        app.dependency_overrides[get_db] = override
        try:
            yield
        finally:
            app.dependency_overrides.clear()
            app.dependency_overrides.update(previous)
            db.close()
            transaction.rollback()
    engine.dispose()


from tests.test_risk_postgis_live import db  # noqa: F401
