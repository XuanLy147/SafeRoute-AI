from datetime import UTC, datetime, timedelta

import bcrypt
from fastapi import Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.session import get_db
from app.models.entities import User, UserRole


# 1. Cấu hình băm mật khẩu Bcrypt trực tiếp (Không âm thầm cắt ngắn >72 bytes)
def verify_password(plain_password: str, hashed_password: str) -> bool:
    try:
        pw_bytes = plain_password.encode("utf-8")
        if len(pw_bytes) > 72:
            return False
        hash_bytes = hashed_password.encode("utf-8")
        return bcrypt.checkpw(pw_bytes, hash_bytes)
    except Exception:
        return False


def get_password_hash(password: str) -> str:
    pw_bytes = password.encode("utf-8")
    if len(pw_bytes) > 72:
        raise ValueError("Mật khẩu không được dài quá 72 bytes theo tiêu chuẩn an toàn Bcrypt.")
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(pw_bytes, salt).decode("utf-8")


# 2. Tạo và giải mã JWT token
def create_access_token(data: dict, expires_delta: timedelta | None = None) -> str:
    to_encode = data.copy()
    now = datetime.now(UTC)
    if expires_delta:
        expire = now + expires_delta
    else:
        expire = now + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire, "iat": now})
    encoded_jwt = jwt.encode(to_encode, settings.SECRET_KEY, algorithm=settings.ALGORITHM)
    return encoded_jwt


def decode_access_token(token: str) -> dict:
    try:
        payload = jwt.decode(
            token,
            settings.SECRET_KEY,
            algorithms=[settings.ALGORITHM],
            options={"require_exp": True, "require_sub": True},
        )
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token đã hết hạn. Vui lòng đăng nhập lại.",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except JWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Token xác thực không hợp lệ.",
            headers={"WWW-Authenticate": "Bearer"},
        )


# 3. OAuth2 Bearer scheme
oauth2_scheme = OAuth2PasswordBearer(tokenUrl=f"{settings.API_V1_STR}/auth/login", auto_error=False)


# 4. Dependency lấy User hiện tại
def get_current_user(
    token: str | None = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> User:
    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Yêu cầu đăng nhập để truy cập tài nguyên này (thiếu Bearer token).",
            headers={"WWW-Authenticate": "Bearer"},
        )

    payload = decode_access_token(token)
    user_id_str: str | None = payload.get("sub")
    if not isinstance(user_id_str, str) or not user_id_str.isdecimal():
        raise HTTPException(status_code=401, detail="Invalid token subject")
    user = db.get(User, int(user_id_str))
    if user is None:
        raise HTTPException(status_code=401, detail="User no longer exists")

    return user


# 5. Dependency kiểm tra quyền RBAC (Server-side RBAC)
def require_role(allowed_roles: list[UserRole | str]):
    """
    Factory dependency kiểm tra quyền nghiêm ngặt tại Backend.
    Nếu quyền của User không nằm trong danh sách cho phép, chặn đứng với HTTP 403 Forbidden.
    """
    allowed_values = [r.value if isinstance(r, UserRole) else str(r) for r in allowed_roles]

    def role_checker(current_user: User = Depends(get_current_user)) -> User:
        user_role_val = current_user.role.value if isinstance(current_user.role, UserRole) else str(current_user.role)
        if user_role_val not in allowed_values:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail=(
                    f"Truy cập bị từ chối: Thao tác yêu cầu quyền thuộc {allowed_values}. "
                    f"Vai trò hiện tại của bạn là '{user_role_val}'."
                ),
            )
        return current_user

    return role_checker
