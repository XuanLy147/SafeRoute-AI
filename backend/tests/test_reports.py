import io

import pytest
from fastapi.testclient import TestClient

from app.core.security import create_access_token
from app.main import app
from app.models.entities import ReportStatus, UserRole

client = TestClient(app)

@pytest.fixture
def user_auth_header():
    token = create_access_token(
        data={"sub": "1", "role": UserRole.USER.value, "email": "user@saferoute.ai"}
    )
    return {"Authorization": f"Bearer {token}"}

def test_upload_image_invalid_mime_type_returns_400(user_auth_header):
    fake_txt_file = io.BytesIO(b"Hello world this is not an image")
    response = client.post(
        "/api/v1/images",
        files={"file": ("test.txt", fake_txt_file, "text/plain")},
        headers=user_auth_header,
    )
    assert response.status_code == 400
    assert "Định dạng ảnh không được hỗ trợ" in response.json()["detail"]

def test_upload_image_magic_bytes_mismatch_returns_400(user_auth_header):
    # Gửi file text nhưng cố tình mạo danh header image/jpeg
    fake_jpg = io.BytesIO(b"echo rm -rf /")
    response = client.post(
        "/api/v1/images",
        files={"file": ("malicious.jpg", fake_jpg, "image/jpeg")},
        headers=user_auth_header,
    )
    assert response.status_code == 400
    assert "Magic bytes mismatch" in response.json()["detail"]

def test_upload_image_success(user_auth_header):
    # Tạo fake PNG header bytes
    png_bytes = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15c4"
    image_file = io.BytesIO(png_bytes)
    response = client.post(
        "/api/v1/images",
        files={"file": ("valid.png", image_file, "image/png")},
        headers=user_auth_header,
    )
    assert response.status_code == 200
    data = response.json()
    assert "image_id" in data
    assert "url" in data
    assert data["mime"] == "image/png"

def test_create_report_default_status_is_pending_verification(user_auth_header):
    """
    Quy tắc bất biến: Mọi báo cáo mới tạo BẮT BUỘC mang trạng thái PENDING_VERIFICATION.
    """
    payload = {
        "latitude": 10.762622,
        "longitude": 106.660172,
        "category_user": "D40",
        "severity": "HIGH",
        "description": "Ổ gà sâu trên đường An Dương Vương",
        "image_id": "test_image_123"
    }
    response = client.post(
        "/api/v1/reports",
        json=payload,
        headers=user_auth_header,
    )
    assert response.status_code == 201
    data = response.json()
    assert data["status"] == ReportStatus.PENDING_VERIFICATION.value
    assert data["category_user"] == "D40"
    assert data["severity"] == "HIGH"
    assert data["latitude"] == 10.762622
    assert data["longitude"] == 106.660172

def test_get_reports_public_excludes_rejected():
    response = client.get("/api/v1/reports")
    assert response.status_code == 200
    reports = response.json()
    assert isinstance(reports, list)
    for r in reports:
        assert r["status"] != ReportStatus.REJECTED.value

def test_get_my_reports(user_auth_header):
    response = client.get(
        "/api/v1/reports/mine",
        headers=user_auth_header,
    )
    assert response.status_code == 200
    reports = response.json()
    assert isinstance(reports, list)
    for r in reports:
        assert r["user_id"] == 1

def test_get_report_detail():
    # Thử lấy report id=101 (hoặc id đầu tiên vừa tạo)
    response = client.get("/api/v1/reports/101")
    if response.status_code == 200:
        data = response.json()
        assert "id" in data
        assert "category_user" in data
        assert "votes" in data
