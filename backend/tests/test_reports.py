import io

import pytest
from fastapi.testclient import TestClient

from app.core.security import create_access_token
from app.main import app
from app.models.entities import ReportStatus, UserRole

client = TestClient(app)

VALID_PNG_BYTES = (
    b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\n\x00\x00\x00\n\x08\x02\x00\x00\x00"
    b"\x02PX\xea\x00\x00\x00\x16IDATx\x9cc\xfc\xff\xff?\x03n\xc0\x84G\x8ea\xe4J\x03\x00"
    b"\xa5\xe3\x03\x11\xc7z\x1cU\x00\x00\x00\x00IEND\xaeB`\x82"
)


@pytest.fixture
def user_auth_header():
    token = create_access_token(data={"sub": "1", "role": UserRole.USER.value, "email": "user@saferoute.ai"})
    return {"Authorization": f"Bearer {token}"}


def test_upload_image_invalid_mime_type_returns_400(user_auth_header):
    fake_txt_file = io.BytesIO(b"Hello world this is not an image")
    response = client.post(
        "/api/v1/images",
        files={"file": ("test.txt", fake_txt_file, "text/plain")},
        headers=user_auth_header,
    )
    assert response.status_code == 400
    assert "Invalid or truncated image" in response.json()["detail"]


def test_upload_image_magic_bytes_mismatch_returns_400(user_auth_header):
    # Gửi file text nhưng cố tình mạo danh header image/jpeg
    fake_jpg = io.BytesIO(b"echo rm -rf /")
    response = client.post(
        "/api/v1/images",
        files={"file": ("malicious.jpg", fake_jpg, "image/jpeg")},
        headers=user_auth_header,
    )
    assert response.status_code == 400
    assert "Invalid or truncated image" in response.json()["detail"]


def test_upload_image_success(user_auth_header):
    image_file = io.BytesIO(VALID_PNG_BYTES)
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
    uploaded = client.post(
        "/api/v1/images", files={"file": ("fixture.png", VALID_PNG_BYTES, "image/png")}, headers=user_auth_header
    )
    assert uploaded.status_code == 200
    payload = {
        "latitude": 10.762622,
        "longitude": 106.660172,
        "category_user": "D40",
        "severity": "HIGH",
        "description": "Ổ gà sâu trên đường An Dương Vương",
        "image_id": uploaded.json()["image_id"],
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


def test_secure_static_uploads_blocks_invalid_filenames():
    """Bảo mật Static Uploads: Chặn truy cập file lạ hoặc directory traversal, trả 404."""
    # Thử truy cập file không đúng định dạng UUID
    res = client.get("/uploads/arbitrary_file.txt")
    assert res.status_code == 404

    # Thử directory traversal
    res_traversal = client.get("/uploads/../main.py")
    assert res_traversal.status_code in (404, 400)


def test_get_image_api_endpoint_and_security_headers(user_auth_header):
    """Bảo mật Static Uploads: Tải và đọc ảnh qua endpoint an toàn có nosniff và CSP."""
    image_file = io.BytesIO(VALID_PNG_BYTES)
    upload_res = client.post(
        "/api/v1/images",
        files={"file": ("sec_test.png", image_file, "image/png")},
        headers=user_auth_header,
    )
    assert upload_res.status_code == 200
    image_id = upload_res.json()["image_id"]

    # 1. Đọc qua GET /api/v1/images/{image_id}
    api_get_res = client.get(f"/api/v1/images/{image_id}", headers=user_auth_header)
    assert api_get_res.status_code == 200
    assert api_get_res.headers.get("x-content-type-options") == "nosniff"

    # 2. Đọc qua /uploads/{image_id}.png
    static_get_res = client.get(f"/uploads/{image_id}.png")
    assert static_get_res.status_code == 404
    assert client.get(f"/api/v1/images/{image_id}").status_code == 401
    assert api_get_res.headers["cache-control"] == "private, no-store"
    assert "default-src 'none'" in api_get_res.headers.get("content-security-policy", "")
