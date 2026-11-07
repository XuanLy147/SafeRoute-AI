from datetime import timedelta

import pytest
from fastapi.testclient import TestClient

from app.core.security import create_access_token
from app.main import app
from app.models.entities import UserRole

client = TestClient(app)


def test_login_success():
    response = client.post("/api/v1/auth/login", json={"email": "user@saferoute.ai", "password": "123456"})
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert data["token_type"] == "bearer"
    assert data["role"] == "USER"
    assert data["email"] == "user@saferoute.ai"


def test_login_wrong_password_returns_401():
    response = client.post("/api/v1/auth/login", json={"email": "user@saferoute.ai", "password": "wrongpassword"})
    assert response.status_code == 401
    assert "Email hoặc mật khẩu không chính xác" in response.json()["detail"]


def test_login_nonexistent_user_returns_401():
    response = client.post("/api/v1/auth/login", json={"email": "nobody@saferoute.ai", "password": "123456"})
    assert response.status_code == 401


def test_get_me_unauthorized_without_token_returns_401():
    response = client.get("/api/v1/auth/me")
    assert response.status_code == 401
    assert "Yêu cầu đăng nhập" in response.json()["detail"]


def test_get_me_with_invalid_token_returns_401():
    response = client.get("/api/v1/auth/me", headers={"Authorization": "Bearer invalid.token.signature"})
    assert response.status_code == 401
    assert "Token xác thực không hợp lệ" in response.json()["detail"]


def test_get_me_with_expired_token_returns_401():
    # Tạo token đã hết hạn 10 phút trước
    expired_token = create_access_token(
        data={"sub": "1", "role": "USER", "email": "user@saferoute.ai"}, expires_delta=timedelta(minutes=-10)
    )
    response = client.get("/api/v1/auth/me", headers={"Authorization": f"Bearer {expired_token}"})
    assert response.status_code == 401
    assert "Token đã hết hạn" in response.json()["detail"]


def test_rbac_user_calling_review_queue_returns_403_forbidden():
    """
    Quy tắc an toàn P0: USER tuyệt đối không được truy cập hàng đợi kiểm duyệt.
    Backend phải thực thi chặn đứng với HTTP 403 Forbidden.
    """
    user_token = create_access_token(data={"sub": "1", "role": UserRole.USER.value, "email": "user@saferoute.ai"})
    response = client.get("/api/v1/review/queue", headers={"Authorization": f"Bearer {user_token}"})
    assert response.status_code == 403
    assert "Truy cập bị từ chối" in response.json()["detail"]


def test_rbac_reviewer_calling_review_queue_returns_200():
    reviewer_token = create_access_token(
        data={"sub": "2", "role": UserRole.REVIEWER.value, "email": "reviewer@saferoute.ai"}
    )
    response = client.get("/api/v1/review/queue", headers={"Authorization": f"Bearer {reviewer_token}"})
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_rbac_admin_calling_review_queue_returns_200():
    admin_token = create_access_token(data={"sub": "3", "role": UserRole.ADMIN.value, "email": "admin@saferoute.ai"})
    response = client.get("/api/v1/review/queue", headers={"Authorization": f"Bearer {admin_token}"})
    assert response.status_code == 200
    assert isinstance(response.json(), list)


def test_register_password_longer_than_72_bytes_returns_400():
    """Bcrypt Security: Không âm thầm truncate password > 72 bytes mà phải trả 400 Bad Request."""
    long_password = "a" * 75
    response = client.post("/api/v1/auth/register", json={"email": "longpw@saferoute.ai", "password": long_password})
    assert response.status_code == 400
    assert "Mật khẩu quá dài" in response.json()["detail"]


def test_login_password_longer_than_72_bytes_returns_401():
    """Bcrypt Security: Mật khẩu > 72 bytes không được phép đối soát hay đăng nhập thành công."""
    long_password = "a" * 75
    response = client.post("/api/v1/auth/login", json={"email": "user@saferoute.ai", "password": long_password})
    assert response.status_code == 401


def bearer(user_id, role="ADMIN"):
    return {"Authorization": "Bearer " + create_access_token(data={"sub": str(user_id), "role": role})}


def test_registration_never_grants_privileged_role():
    for role in ("ADMIN", "REVIEWER"):
        r = client.post("/api/v1/auth/register", json={"email": "new@example.com", "password": "abcdef", "role": role})
        assert r.status_code == 403


@pytest.mark.parametrize("user_id,code", [(None, 401), (1, 403), (2, 403)])
@pytest.mark.parametrize("endpoint", ["users", "role-audit", "change"])
def test_admin_endpoints_reject_guests_users_and_reviewers(user_id, code, endpoint):
    headers = {} if user_id is None else bearer(user_id)
    if endpoint == "change":
        r = client.patch(
            "/api/v1/admin/users/1/role",
            headers=headers,
            json={"role": "REVIEWER", "expected_role": "USER", "reason": "Test assignment"},
        )
    else:
        r = client.get("/api/v1/admin/" + endpoint, headers=headers)
    assert r.status_code == code


def test_admin_role_assignment_is_audited_and_old_jwt_loses_access():
    admin = bearer(3)
    r = client.patch(
        "/api/v1/admin/users/2/role",
        headers=admin,
        json={"role": "USER", "expected_role": "REVIEWER", "reason": "Review permission revoked"},
    )
    assert r.status_code == 200
    assert r.json()["user"]["role"] == "USER"
    assert client.get("/api/v1/review/queue", headers=bearer(2, "REVIEWER")).status_code == 403
    assert client.get("/api/v1/auth/me", headers=bearer(2, "REVIEWER")).json()["role"] == "USER"
    audit = client.get("/api/v1/admin/role-audit?user_id=2", headers=admin).json()
    assert len(audit) == 1
    assert (audit[0]["actor_id"], audit[0]["from_role"], audit[0]["to_role"]) == (3, "REVIEWER", "USER")


@pytest.mark.parametrize("target,role,expected", [(3, "USER", "ADMIN"), (1, "ADMIN", "REVIEWER"), (1, "USER", "USER")])
def test_admin_cannot_self_demote_or_write_stale_role(target, role, expected):
    r = client.patch(
        f"/api/v1/admin/users/{target}/role",
        headers=bearer(3),
        json={"role": role, "expected_role": expected, "reason": "Test change"},
    )
    assert r.status_code == 409


def test_role_reason_unknown_role_and_missing_user():
    for role, reason in [("ADMIN", "   "), ("OWNER", "Unknown role")]:
        assert (
            client.patch(
                "/api/v1/admin/users/1/role",
                headers=bearer(3),
                json={"role": role, "expected_role": "USER", "reason": reason},
            ).status_code
            == 422
        )
    assert (
        client.patch(
            "/api/v1/admin/users/999999/role",
            headers=bearer(3),
            json={"role": "ADMIN", "expected_role": "USER", "reason": "Missing user"},
        ).status_code
        == 404
    )


def test_users_search_is_literal_and_never_exposes_credentials():
    r = client.get("/api/v1/admin/users?q=reviewer&limit=1", headers=bearer(3))
    assert r.status_code == 200 and len(r.json()) == 1
    assert set(r.json()[0]) == {"id", "email", "full_name", "role", "created_at"}
    assert client.get("/api/v1/admin/users?q=%25", headers=bearer(3)).json() == []
    assert client.get("/api/v1/admin/users?limit=101", headers=bearer(3)).status_code == 422


def test_admin_can_grant_admin_and_database_is_role_authority():
    assert (
        client.patch(
            "/api/v1/admin/users/1/role",
            headers=bearer(3),
            json={"role": "ADMIN", "expected_role": "USER", "reason": "Assign administrator"},
        ).status_code
        == 200
    )
    assert client.get("/api/v1/admin/users", headers=bearer(1, "USER")).status_code == 200
