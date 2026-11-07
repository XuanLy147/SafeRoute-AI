import pytest
from app.models.entities import (
    UserRole,
    ReportStatus,
    VoteType,
    User,
    Report,
    ReportImage,
    AiPrediction,
    AiDetection,
    CommunityVote,
    ReviewLog,
    RiskWeight,
)
from app.db.geo import make_point

def test_enums_defined_correctly():
    assert UserRole.USER.value == "USER"
    assert UserRole.REVIEWER.value == "REVIEWER"
    assert UserRole.ADMIN.value == "ADMIN"

    assert ReportStatus.PENDING_VERIFICATION.value == "PENDING_VERIFICATION"
    assert ReportStatus.VERIFIED.value == "VERIFIED"
    assert ReportStatus.REJECTED.value == "REJECTED"

    assert VoteType.SUPPORT.value == "SUPPORT"
    assert VoteType.OPPOSE.value == "OPPOSE"
    assert VoteType.UNSURE.value == "UNSURE"

def test_user_model_instantiation():
    user = User(
        email="test@saferoute.ai",
        password_hash="fakehash",
        role=UserRole.USER,
    )
    assert user.email == "test@saferoute.ai"
    assert user.role == UserRole.USER

def test_make_point_lon_lat_order():
    # Lat: 10.762622, Lon: 106.660172
    lat = 10.762622
    lon = 106.660172
    pt = make_point(longitude=lon, latitude=lat)
    assert "POINT(106.660172 10.762622)" in str(pt)

def test_risk_weight_model():
    rw = RiskWeight(
        category="D40",
        weight=10.0,
        severity="HIGH",
        verification_weight=1.0,
    )
    assert rw.category == "D40"
    assert rw.weight == 10.0
    assert rw.severity == "HIGH"
