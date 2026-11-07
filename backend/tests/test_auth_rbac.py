from datetime import timedelta

from fastapi.testclient import TestClient

from app.core.security import create_access_token
from app.main import app
from app.models.entities import UserRole

client = TestClient(app)

def test_login_success():
    response = client.post(
        "/api/v1/auth/login",
        json={"email": "user@saferoute.ai", "password": "123456"}
    )
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"
    assert data["role"] == "USER"
    assert data["email"] == "user@saferoute.ai"

def test_login_wrong_password_returns_401():
    response = client.post(
        "/api/v1/auth/login",
        json={"email": "user@saferoute.ai", "password": "wrongpassword"}
    )
    assert response.status_code == 401
    assert "Email hoặc mật khẩu không chính xác" in response.json()["detail"]

def test_login_nonexistent_user_returns_401():
    response = client.post(
        "/api/v1/auth/login",
        json={"email": "nobody@saferoute.ai", "password": "123456"}
    )
    assert response.status_code == 401

def test_get_me_unauthorized_without_token_returns_401():
    response = client.get("/api/v1/auth/me")
    assert response.status_code == 401
    assert "Yêu cầu đăng nhập" in response.json()["detail"]

def test_get_me_with_invalid_token_returns_401():
    response = client.get(
        "/api/v1/auth/me",
        headers={"Authorization": "Bearer invalid.token.signature"}
    )
    assert response.status_code == 401
    assert "Token xác thực không hợp lệ" in response.json()["detail"]

def test_get_me_with_expired_token_returns_401():
    # Tạo token đã hết hạn 10 phút trước
    expired_token = create_access_token(
        data={"sub": "1", "role": "USER", "email": "user@saferoute.ai"},
        expires_delta=timedelta(minutes=-10)
    )
    response = client.get(
        "/api/v1/auth/me",
        headers={"Authorization": f"Bearer {expired_token}"}
    )
    assert response.status_code == 401
    assert "Token đã hết hạn" in response.json()["detail"]

def test_rbac_user_calling_review_queue_returns_403_forbidden():
    """
    Quy tắc an toàn P0: USER tuyệt đối không được truy cập hàng đợi kiểm duyệt.
    Backend phải thực thi chặn đứng với HTTP 403 Forbidden.
    """
    user_token = create_access_token(
        data={"sub": "1", "role": UserRole.USER.value, "email": "user@saferoute.ai"}
    )
    response = client.get(
        "/api/v1/review/queue",
        headers={"Authorization": f"Bearer {user_token}"}
    )
    assert response.status_code == 403
    assert "Truy cập bị từ chối" in response.json()["detail"]

def test_rbac_reviewer_calling_review_queue_returns_200():
    reviewer_token = create_access_token(
        data={"sub": "2", "role": UserRole.REVIEWER.value, "email": "reviewer@saferoute.ai"}
    )
    response = client.get(
        "/api/v1/review/queue",
        headers={"Authorization": f"Bearer {reviewer_token}"}
    )
    assert response.status_code == 200
    assert isinstance(response.json(), list)

def test_rbac_admin_calling_review_queue_returns_200():
    admin_token = create_access_token(
        data={"sub": "3", "role": UserRole.ADMIN.value, "email": "admin@saferoute.ai"}
    )
    response = client.get(
        "/api/v1/review/queue",
        headers={"Authorization": f"Bearer {admin_token}"}
    )
    assert response.status_code == 200
    assert isinstance(response.json(), list)

def test_register_password_longer_than_72_bytes_returns_400():
    """Bcrypt Security: Không âm thầm truncate password > 72 bytes mà phải trả 400 Bad Request."""
    long_password = "a" * 75
    response = client.post(
        "/api/v1/auth/register",
        json={"email": "longpw@saferoute.ai", "password": long_password}
    )
    assert response.status_code == 400
    assert "Mật khẩu quá dài" in response.json()["detail"]

def test_login_password_longer_than_72_bytes_returns_401():
    """Bcrypt Security: Mật khẩu > 72 bytes không được phép đối soát hay đăng nhập thành công."""
    long_password = "a" * 75
    response = client.post(
        "/api/v1/auth/login",
        json={"email": "user@saferoute.ai", "password": long_password}
    )
    assert response.status_code == 401

