import pytest
from fastapi.testclient import TestClient

from app.core.security import create_access_token
from app.main import app
from app.models.entities import ReportStatus, UserRole

client = TestClient(app)


@pytest.fixture
def user_header():
    token = create_access_token(data={"sub": "1", "role": UserRole.USER.value, "email": "user@saferoute.ai"})
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def reviewer_header():
    token = create_access_token(data={"sub": "2", "role": UserRole.REVIEWER.value, "email": "reviewer@saferoute.ai"})
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def created_report_id(user_header):
    res = client.post(
        "/api/v1/reports",
        json={
            "latitude": 10.762622,
            "longitude": 106.660172,
            "category_user": "D40",
            "severity": "HIGH",
            "description": "Test report for voting and review",
        },
        headers=user_header,
    )
    return res.json()["id"]


def test_community_vote_and_duplicate_prevention(user_header, created_report_id):
    """
    Quy tắc nghiệp vụ:
    1. Vote SUPPORT/OPPOSE/UNSURE.
    2. Một user chỉ được vote 1 lần / report -> lần 2 trả 409 Conflict.
    3. Vote KHÔNG làm thay đổi status của report sang VERIFIED.
    """
    report_id = created_report_id

    # Vote lần 1 -> Thành công 201 Created
    vote_res = client.post(
        f"/api/v1/reports/{report_id}/votes",
        json={"vote": "SUPPORT", "note": "Hôm qua đi qua suýt ngã"},
        headers=user_header,
    )
    assert vote_res.status_code == 201
    assert vote_res.json()["vote"] == "SUPPORT"

    # Vote lần 2 cùng user trên cùng report -> 409 Conflict
    dup_res = client.post(
        f"/api/v1/reports/{report_id}/votes",
        json={"vote": "SUPPORT"},
        headers=user_header,
    )
    assert dup_res.status_code == 409
    assert "Không thể vote nhiều lần" in dup_res.json()["detail"]

    # Kiểm tra trạng thái report vẫn là PENDING_VERIFICATION (Vote không tự duyệt)
    detail_res = client.get(f"/api/v1/reports/{report_id}")
    if detail_res.status_code == 200:
        assert detail_res.json()["status"] == ReportStatus.PENDING_VERIFICATION.value


def test_vote_with_tv2_vote_type_contract(created_report_id):
    """
    Nhiệm vụ 2: Kiểm tra tương thích contract TV2 Android (trường vote_type).
    """
    token = create_access_token(data={"sub": "99", "role": UserRole.USER.value, "email": "tv2_user@saferoute.ai"})
    vote_res = client.post(
        f"/api/v1/reports/{created_report_id}/votes",
        json={"vote_type": "SUPPORT", "note": "Gửi từ Android Retrofit của TV2"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert vote_res.status_code == 201
    assert vote_res.json()["vote"] == "SUPPORT"
    assert vote_res.json()["vote_type"] == "SUPPORT"


def test_user_cannot_verify_report_returns_403(user_header, created_report_id):
    """
    USER bypass gọi endpoint phê duyệt -> BẮT BUỘC 403 Forbidden.
    """
    res = client.patch(
        f"/api/v1/reports/{created_report_id}/status",
        json={"status": "VERIFIED", "reason": "Tự duyệt báo cáo của mình"},
        headers=user_header,
    )
    assert res.status_code == 403
    assert "Truy cập bị từ chối" in res.json()["detail"]


def test_reviewer_must_provide_reason(reviewer_header, created_report_id):
    """
    Reviewer duyệt hoặc từ chối mà thiếu reason -> Bị chặn với mã 422 Unprocessable Entity.
    """
    res = client.patch(
        f"/api/v1/reports/{created_report_id}/status",
        json={"status": "VERIFIED", "reason": "   "},
        headers=reviewer_header,
    )
    assert res.status_code == 422
    data = res.json()
    detail_str = str(data.get("detail", ""))
    assert "reason" in detail_str or "String should have at least 5 characters" in detail_str


def test_reviewer_verifies_report_with_audit_log(reviewer_header, created_report_id):
    """
    Reviewer duyệt báo cáo với lý do hợp lệ -> Cập nhật VERIFIED và ghi audit log.
    """
    res = client.patch(
        f"/api/v1/reports/{created_report_id}/status",
        json={"status": "VERIFIED", "reason": "Ảnh rõ ổ gà sâu nguy hiểm, đúng vị trí"},
        headers=reviewer_header,
    )
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "VERIFIED"
    assert data["actor_id"] == 2
    assert "Ảnh rõ ổ gà sâu" in data["reason"]

    # Kiểm tra report detail đã chuyển sang VERIFIED
    detail = client.get(f"/api/v1/reports/{created_report_id}").json()
    assert detail["status"] == "VERIFIED"
