"""Real PostgreSQL/PostGIS HTTP integration, with explicit upstream fault injection."""

import io
import json
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient
from PIL import Image
from sqlalchemy import func, select
from sqlalchemy.exc import OperationalError

from app.core.config import settings
from app.core.security import create_access_token
from app.db.session import get_db
from app.main import app
from app.models.entities import AiDetection, Report, ReportImage, ReviewLog, User, UserRole
from app.modules.routing.adapter import GoogleRoutesAdapter
from tests.test_risk_postgis_live import insert_reports, route

DRAFT = dict(
    revision="DEMO-2026-11-07",
    approved=False,
    severity_weights={"LOW": 1, "MEDIUM": 2, "HIGH": 4},
    pending_multiplier=0.5,
    radius_meters=25,
    medium_min=2,
    high_min=6,
)


@pytest.fixture
def http(db, monkeypatch, tmp_path):
    monkeypatch.setattr(settings, "UPLOAD_DIR", str(tmp_path))
    previous = app.dependency_overrides.copy()

    def override():
        try:
            yield db
        except Exception:
            db.rollback()
            raise

    app.dependency_overrides[get_db] = override
    with TestClient(app) as client:
        yield client
    app.dependency_overrides.clear()
    app.dependency_overrides.update(previous)


def account(http, email="integration@example.com"):
    res = http.post(
        "/api/v1/auth/register", json={"email": email, "password": "fixture-password", "full_name": "Test User"}
    )
    assert res.status_code == 200, res.text
    return res.json(), {"Authorization": "Bearer " + res.json()["access_token"]}


def png(fmt="PNG"):
    out = io.BytesIO()
    Image.new("RGB", (24, 24), (80, 80, 80)).save(out, format=fmt)
    return out.getvalue()


@pytest.mark.parametrize(
    "fmt,mime,suffix", [("PNG", "image/png", ".png"), ("JPEG", "image/jpeg", ".jpg"), ("WEBP", "image/webp", ".webp")]
)
def test_private_image_format_ownership_and_content(http, db, fmt, mime, suffix):
    user, headers = account(http)
    content = png(fmt)
    uploaded = http.post("/api/v1/images", headers=headers, files={"file": ("wrong.exe", content, mime)})
    assert uploaded.status_code == 200
    data = uploaded.json()
    row = db.scalar(select(ReportImage).where(ReportImage.owner_id == user["user_id"]))
    assert row.object_key.endswith(suffix) and row.mime == mime
    got = http.get(data["url"], headers=headers)
    assert got.status_code == 200 and got.content == content
    assert got.headers["content-type"] == mime
    assert got.headers["cache-control"] == "private, no-store"
    assert http.get(data["url"]).status_code == 401
    _, other = account(http, "stranger@example.com")
    assert http.get(data["url"], headers=other).status_code == 403
    assert http.get("/uploads/" + row.object_key, headers=headers).status_code == 404
    result = http.post(
        "/api/v1/reports",
        headers=other,
        json={"image_id": data["image_id"], "latitude": 10, "longitude": 106, "category_user": "D40"},
    )
    assert result.status_code == 403


def test_auth_db_roles_deleted_user_and_forged_role(http, db):
    user, headers = account(http)
    forged = create_access_token({"sub": str(user["user_id"]), "role": "ADMIN"})
    assert http.get("/api/v1/review/queue", headers={"Authorization": "Bearer " + forged}).status_code == 403
    db.get(User, user["user_id"]).role = UserRole.REVIEWER
    db.flush()
    assert http.get("/api/v1/review/queue", headers=headers).status_code == 200
    db.get(User, user["user_id"]).role = UserRole.USER
    db.flush()
    assert http.get("/api/v1/review/queue", headers=headers).status_code == 403
    db.delete(db.get(User, user["user_id"]))
    db.flush()
    assert http.get("/api/v1/auth/me", headers=headers).status_code == 401


@pytest.mark.parametrize(
    "path,method,payload",
    [
        ("/api/v1/auth/me", "get", None),
        ("/api/v1/auth/login", "post", {"email": "a@example.com", "password": "test-password"}),
        ("/api/v1/auth/register", "post", {"email": "a@example.com", "password": "test-password"}),
        ("/api/v1/reports", "get", None),
    ],
)
def test_database_failure_never_returns_fake_success(http, path, method, payload):
    broken = Mock()
    failure = OperationalError("synthetic", {}, RuntimeError("secret-db-password"))
    broken.get.side_effect = failure
    broken.query.side_effect = failure
    app.dependency_overrides[get_db] = lambda: broken
    headers = {"Authorization": "Bearer " + create_access_token({"sub": "1", "role": "ADMIN"})}
    kwargs = {"headers": headers}
    if payload is not None:
        kwargs["json"] = payload
    result = getattr(http, method)(path, **kwargs)
    assert result.status_code == 503
    assert "secret-db-password" not in result.text


def test_report_confirmed_detections_audit_and_transaction(http, db, monkeypatch):
    user, headers = account(http)
    detection = {"class_code": "D40", "confidence": 0.8, "bbox": [0.1, 0.2, 0.6, 0.7]}
    payload = {"latitude": 10.005, "longitude": 106, "category_user": "Repair", "ai_detections": [detection]}
    created = http.post("/api/v1/reports", headers=headers, json=payload)
    assert created.status_code == 201
    id_ = created.json()["id"]
    assert db.scalar(select(func.count()).select_from(AiDetection)) == 1
    detail = http.get(f"/api/v1/reports/{id_}").json()
    assert detail["ai_detections"] == [detection]
    assert detail["model_version"] == "USER_CONFIRMED"
    assert detail["created_at"] == detail["observed_at"]
    for vote in ("OPPOSE", "SUPPORT"):
        response = http.post(f"/api/v1/reports/{id_}/votes", headers=headers, json={"vote_type": vote})
        assert response.status_code == (201 if vote == "OPPOSE" else 409)
    assert db.get(Report, id_).status.value == "PENDING_VERIFICATION"
    db.get(User, user["user_id"]).role = UserRole.REVIEWER
    db.flush()
    response = http.patch(
        f"/api/v1/reports/{id_}/status", headers=headers, json={"status": "REJECTED", "reason": "Test rejection"}
    )
    assert response.status_code == 200
    assert (
        http.patch(
            f"/api/v1/reports/{id_}/status", headers=headers, json={"status": "VERIFIED", "reason": "Repeat review"}
        ).status_code
        == 409
    )
    assert http.get("/api/v1/reports?status=REJECTED").json() == []
    detail = http.get(f"/api/v1/reports/{id_}").json()
    assert detail["audit_logs"][0]["new_status"] == "REJECTED"
    assert db.scalar(select(func.count()).select_from(ReviewLog)) == 1


def test_http_real_postgis_keyed_routes(http, db, monkeypatch):
    insert_reports(db)
    monkeypatch.setattr(settings, "RISK_POLICY_JSON", json.dumps(DRAFT))
    from app.modules.routing.polyline import encode_polyline
    candidates = [route("far", 106.01), route("near", 106)]
    for candidate in candidates:
        candidate.polyline = encode_polyline(candidate.coordinates)
    monkeypatch.setattr(GoogleRoutesAdapter, "compute_routes", lambda *a, **kw: candidates)
    result = http.post(
        "/api/v1/routes/compare",
        json={"origin": {"latitude": 10, "longitude": 106}, "destination": {"latitude": 10.01, "longitude": 106}},
    )
    assert result.status_code == 200, result.text
    data = result.json()
    assert [(r["id"], r["risk"]["score"]) for r in data["routes"]] == [("far", 0), ("near", 6)]
    assert data["risk_assessment_status"] == "AVAILABLE"
    assert data["recommended_route_id"] == "far"
    monkeypatch.setattr(settings, "RISK_POLICY_JSON", None)
    result = http.post(
        "/api/v1/routes/compare",
        json={"origin": {"latitude": 10, "longitude": 106}, "destination": {"latitude": 10.01, "longitude": 106}},
    )
    assert result.json()["risk_assessment_status"] == "AVAILABLE"
    assert result.json()["risk_policy_status"] == "EXPERIMENTAL"
    near = result.json()["routes"][1]
    assert len(near["nearby_hazards"]) == near["risk"]["hazards_count"] == 1
    assert near["nearby_hazards"][0]["source_report_ids"]
    assert near["severity_counts"]["HIGH"] == near["verified_high_count"] == 1
    monkeypatch.setattr(settings, "RISK_POLICY_JSON", '{"revision":"invalid"}')
    assert (
        http.post(
            "/api/v1/routes/compare",
            json={"origin": {"latitude": 10, "longitude": 106}, "destination": {"latitude": 10.01, "longitude": 106}},
        ).status_code
        == 503
    )


def test_draft_policy_cannot_enable_production(monkeypatch):
    from app.modules.risk.policy import load_policy

    monkeypatch.setattr(settings, "RISK_POLICY_JSON", json.dumps(DRAFT))
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")
    with pytest.raises(ValueError):
        load_policy()


def test_truncated_image_and_storage_failure(http, monkeypatch):
    _, headers = account(http)
    for content, mime in [(png()[:-12], "image/png"), (png(), "image/jpeg")]:
        result = http.post("/api/v1/images", headers=headers, files={"file": ("x.png", content, mime)})
        assert result.status_code == 400
    from pathlib import Path

    monkeypatch.setattr(Path, "mkdir", Mock(side_effect=OSError("private path")))
    result = http.post("/api/v1/images", headers=headers, files={"file": ("x.png", png(), "image/png")})
    assert result.status_code == 503 and "private path" not in result.text


def test_failed_report_commit_rolls_back_all_rows(http, db, monkeypatch):
    _, headers = account(http)
    before = db.scalar(select(func.count()).select_from(Report))
    monkeypatch.setattr(db, "commit", Mock(side_effect=OperationalError("write", {}, RuntimeError("fault"))))
    result = http.post(
        "/api/v1/reports", headers=headers, json={"latitude": 10, "longitude": 106, "category_user": "D40"}
    )
    assert result.status_code == 503
    assert db.scalar(select(func.count()).select_from(Report)) == before


def test_review_audit_is_atomic_on_commit_failure(http, db, monkeypatch):
    user, headers = account(http)
    created = http.post(
        "/api/v1/reports", headers=headers, json={"latitude": 10, "longitude": 106, "category_user": "D40"}
    )
    assert created.status_code == 201
    id_ = created.json()["id"]
    db.get(User, user["user_id"]).role = UserRole.REVIEWER
    db.commit()
    monkeypatch.setattr(db, "commit", Mock(side_effect=OperationalError("write", {}, RuntimeError("fault"))))
    result = http.patch(
        f"/api/v1/reports/{id_}/status", headers=headers, json={"status": "VERIFIED", "reason": "Synthetic review"}
    )
    assert result.status_code == 503
    db.expire_all()
    assert db.get(Report, id_).status.value == "PENDING_VERIFICATION"
    assert db.scalar(select(func.count()).select_from(ReviewLog).where(ReviewLog.report_id == id_)) == 0


def test_actual_commits_survive_new_http_client_and_db_connection(monkeypatch, tmp_path):
    import os

    from sqlalchemy.engine import make_url

    from app.db.session import SessionLocal, engine

    url = os.environ.get("SAFEROUTE_TEST_DATABASE_URL", "")
    assert (make_url(url).database or "").startswith("saferoute_test_")
    assert get_db not in app.dependency_overrides
    monkeypatch.setattr(settings, "UPLOAD_DIR", str(tmp_path))
    with TestClient(app) as client:
        user, headers = account(client, "restart-check@example.com")
        uploaded = client.post("/api/v1/images", headers=headers, files={"file": ("road.png", png(), "image/png")})
        assert uploaded.status_code == 200
        report = client.post(
            "/api/v1/reports",
            headers=headers,
            json={
                "latitude": 20,
                "longitude": 105,
                "category_user": "D10",
                "image_id": uploaded.json()["image_id"],
                "description": "restart-proof synthetic record",
            },
        )
        assert report.status_code == 201
        id_ = report.json()["id"]
        assert (
            client.post(f"/api/v1/reports/{id_}/votes", headers=headers, json={"vote_type": "UNSURE"}).status_code
            == 201
        )
    with SessionLocal.begin() as session:
        session.get(User, user["user_id"]).role = UserRole.REVIEWER
    with TestClient(app) as client:
        assert (
            client.patch(
                f"/api/v1/reports/{id_}/status",
                headers=headers,
                json={"status": "VERIFIED", "reason": "Durability fixture"},
            ).status_code
            == 200
        )
    engine.dispose()
    with SessionLocal() as session:
        assert session.get(Report, id_).status.value == "VERIFIED"
        assert session.scalar(select(ReviewLog).where(ReviewLog.report_id == id_)) is not None
    with TestClient(app) as restarted:
        login = restarted.post(
            "/api/v1/auth/login", json={"email": "restart-check@example.com", "password": "fixture-password"}
        )
        assert login.status_code == 200
        detail = restarted.get(f"/api/v1/reports/{id_}").json()
        assert detail["votes"]["unsure"] == 1 and detail["status"] == "VERIFIED"
        assert (
            restarted.get(
                detail["image_url"], headers={"Authorization": "Bearer " + login.json()["access_token"]}
            ).status_code
            == 200
        )


def test_map_bounds_and_rejected_filter(http, db):
    insert_reports(db)
    near = http.get("/api/v1/reports?west=105.99&east=106.01&south=10&north=10.01")
    assert near.status_code == 200
    assert len(near.json()) == 2
    assert all(r["status"] != "REJECTED" for r in near.json())
    assert http.get("/api/v1/reports?west=100&east=101&south=10&north=11").json() == []
    assert http.get("/api/v1/reports?west=106").status_code == 422
    assert len(http.get("/api/v1/reports?limit=1").json()) == 1


@pytest.mark.parametrize("value", [float("inf"), float("nan"), -1])
def test_invalid_score_cannot_become_available(value):
    from app.modules.routing.schemas import LatLng, RouteRiskScore
    from app.modules.routing.service import RoutingService

    provider = Mock()
    provider.compute_routes.return_value = [route("one", 106)]
    scorer = Mock(spec=["evaluate_by_id"])
    scorer.evaluate_by_id.return_value = {
        "one": RouteRiskScore.model_construct(
            score=value,
            level="LOW",
            hazards_count=0,
            verified_hazards_count=0,
            pending_hazards_count=0,
            reason="invalid",
        )
    }
    result = RoutingService(provider, scorer).compare_routes(
        LatLng(latitude=10, longitude=106), LatLng(latitude=10.01, longitude=106)
    )
    assert result.risk_assessment_status == "INVALID"
    assert result.routes[0].risk is None

def test_http_experimental_scoring_does_not_override_production_gate(http, monkeypatch):
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")
    monkeypatch.setattr(settings, "RISK_POLICY_JSON", None)
    from app.modules.routing.polyline import encode_polyline
    candidate=route("only",106)
    candidate.polyline=encode_polyline(candidate.coordinates)
    monkeypatch.setattr(GoogleRoutesAdapter,"compute_routes",lambda *a,**kw:[candidate])
    result=http.post("/api/v1/routes/compare",json={
        "origin":{"latitude":10,"longitude":106},"destination":{"latitude":10.01,"longitude":106}})
    assert result.status_code==200
    assert result.json()["risk_assessment_status"]=="UNAVAILABLE"
    assert result.json()["risk_policy_revision"] is None


def test_http_review_changes_next_assessment_consistently(http, db, monkeypatch):
    insert_reports(db)
    from app.models.entities import ReportStatus
    from app.modules.routing.polyline import encode_polyline
    candidate=route("only",106)
    candidate.polyline=encode_polyline(candidate.coordinates)
    monkeypatch.setattr(GoogleRoutesAdapter,"compute_routes",lambda *a,**kw:[candidate])
    payload={"origin":{"latitude":10,"longitude":106},"destination":{"latitude":10.01,"longitude":106}}
    first=http.post("/api/v1/routes/compare",json=payload).json()["routes"][0]
    assert first["risk"]["score"]==6
    for report in db.scalars(select(Report)):
        report.status=ReportStatus.REJECTED
    db.flush()
    second=http.post("/api/v1/routes/compare",json=payload).json()["routes"][0]
    assert second["nearby_hazards"]==[]
    assert second["risk"]["score"]==0
    assert second["risk"]["hazards_count"]==0
    assert sum(second["severity_counts"].values())==0
