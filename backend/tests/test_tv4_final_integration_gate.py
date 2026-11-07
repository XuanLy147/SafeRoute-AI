"""Final integration gates. Real PostGIS fixture; no replacement public API."""
import base64
import sys
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient
from sqlalchemy import select

from app.core.config import settings
from app.core.security import get_password_hash
from app.db.session import get_db
from app.main import app
from app.models.entities import User, UserRole, Report, ReportImage, CommunityVote, ReviewLog
from app.modules.risk.postgis_adapter import KeyedPostgisRiskAdapter
from app.modules.risk.scoring import RiskPolicy
from app.modules.routing.schemas import LatLng, RouteCandidate, RouteRiskScore
from app.modules.routing.service import RoutingService
from tests.test_risk_postgis_live import db, insert_reports, route  # real DB fixture


@pytest.fixture
def http(db, monkeypatch, tmp_path):
    monkeypatch.setattr(settings, "UPLOAD_DIR", str(tmp_path))
    previous = app.dependency_overrides.copy()
    app.dependency_overrides[get_db] = lambda: db
    with TestClient(app) as client:
        yield client
    app.dependency_overrides.clear()
    app.dependency_overrides.update(previous)


def test_http_auth_image_report_vote_review_persisted(http, db):
    credentials={"email":"tv4-gate@example.com","password":"synthetic-test-password"}
    registration=http.post("/api/v1/auth/register",json=credentials)
    assert registration.status_code == 200
    user_id=registration.json()["user_id"]
    assert db.get(User,user_id).email == credentials["email"]
    login=http.post("/api/v1/auth/login",json=credentials)
    assert login.status_code == 200
    headers={"Authorization":"Bearer "+login.json()["access_token"]}
    png=base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN1sAAAAASUVORK5CYII=")
    uploaded=http.post("/api/v1/images",headers=headers,files={"file":("fixture.png",png,"image/png")})
    assert uploaded.status_code == 200
    created=http.post("/api/v1/reports",headers=headers,json={"latitude":10.005,"longitude":106,
        "category_user":"D40","severity":"HIGH","image_id":uploaded.json()["image_id"]})
    assert created.status_code == 201
    id_=created.json()["id"]
    persisted=db.get(Report,id_)
    assert persisted is not None and persisted.status.value == "PENDING_VERIFICATION"
    assert db.scalar(select(ReportImage).where(ReportImage.report_id==id_)) is not None
    voted=http.post(f"/api/v1/reports/{id_}/votes",headers=headers,json={"vote":"SUPPORT"})
    assert voted.status_code in (200,201)
    assert db.scalar(select(CommunityVote).where(CommunityVote.report_id==id_)) is not None
    decision={"status":"VERIFIED","reason":"Synthetic reviewer verification"}
    assert http.patch(f"/api/v1/reports/{id_}/status",headers=headers,json=decision).status_code == 403
    reviewer=User(email="tv4-reviewer@example.com",password_hash=get_password_hash("synthetic-review-password"),role=UserRole.REVIEWER)
    db.add(reviewer); db.flush()
    review_login=http.post("/api/v1/auth/login",json={"email":reviewer.email,"password":"synthetic-review-password"})
    assert review_login.status_code == 200
    reviewed=http.patch(f"/api/v1/reports/{id_}/status",headers={"Authorization":"Bearer "+review_login.json()["access_token"]},json=decision)
    assert reviewed.status_code == 200
    db.expire_all()
    assert db.get(Report,id_).status.value == "VERIFIED"
    assert db.scalar(select(ReviewLog).where(ReviewLog.report_id==id_)) is not None
    detail=http.get(f"/api/v1/reports/{id_}")
    assert detail.status_code == 200 and detail.json()["status"] == "VERIFIED"
    # Uploaded PNG must not turn into an invented .jpg URL in the report DTO.
    assert created.json()["image_url"] == uploaded.json()["url"]


def test_real_db_routes_to_service_response(db):
    insert_reports(db)
    google=Mock(); google.compute_routes.return_value=[route("near",106),route("far",106.01)]
    scorer=KeyedPostgisRiskAdapter(db,RiskPolicy("DRAFT-25m-0.5-2-6",{"HIGH":4},.5),radius_m=25,medium_min=2,high_min=6)
    response=RoutingService(google,scorer).compare_routes(LatLng(latitude=10,longitude=106),LatLng(latitude=10.01,longitude=106))
    assert [(r.id,r.risk.score) for r in response.routes]==[("near",6),("far",0)]
    assert response.risk_assessment_status == "AVAILABLE"
    assert response.recommended_route_id == "far"


def test_routing_public_endpoint_is_wired(http):
    assert "/api/v1/routes/compare" in http.get("/api/v1/openapi.json").json()["paths"], "BLOCKED: routing endpoint absent"


def score(value=1,count=1):
    return RouteRiskScore(score=value,level="LOW",hazards_count=count,reason="MOCK_FOR_DEV")


def compare(raw=None,error=False):
    google=Mock();google.compute_routes.return_value=[route("a",106),route("b",106.01)]
    scorer=Mock(spec=["evaluate_by_id"])
    if error: scorer.evaluate_by_id.side_effect=RuntimeError("synthetic DB outage")
    else: scorer.evaluate_by_id.return_value=raw
    return RoutingService(google,scorer).compare_routes(LatLng(latitude=10,longitude=106),LatLng(latitude=10.01,longitude=106))


@pytest.mark.parametrize("raw,expected",[
    ({"b":score(2),"a":score(1)},"AVAILABLE"),
    ({"a":score()},"PARTIAL"),
    ({},"UNAVAILABLE"),
    ({"a":score(),"b":score(),"extra":score()},"INVALID"),
    ({"extra":score()},"INVALID"),
    ([score()],"PARTIAL"),
    ([score(),score(),score()],"INVALID"),
])
def test_risk_status_contract(raw,expected):
    result=compare(raw)
    assert [r.id for r in result.routes]==["a","b"]
    assert result.risk_assessment_status == expected
    if expected != "AVAILABLE": assert result.recommendation_basis != "SAFETY_LOWEST_RISK"


def test_db_error_is_unavailable():
    result=compare(error=True)
    assert result.risk_assessment_status == "UNAVAILABLE"
    assert all(r.risk is None for r in result.routes)
    assert result.recommendation_basis == "DURATION_ETA_ONLY"


def test_zero_weighted_score_does_not_claim_zero_hazards():
    result=compare({"a":score(0,2),"b":score(1,1)})
    assert "chưa ghi nhận nguy cơ nào" not in result.recommendation_reason.lower()


def test_ai_inference_exception_not_successful_empty(monkeypatch):
    from app.modules.ai.service import YoloInferenceService
    # Fault injection into a ready model; not a live YOLO accuracy test.
    service=object.__new__(YoloInferenceService)
    service.model=Mock();service.model.predict.side_effect=RuntimeError("synthetic inference failure")
    service.checksum_valid=True;service.model_version="synthetic-ready";service.device="cpu"
    fake_image=Mock();fake_image.open.return_value.convert.return_value=object()
    monkeypatch.setitem(sys.modules,"PIL",SimpleNamespace(Image=fake_image))
    with pytest.raises(HTTPException): service.predict(b"fixture")
