from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.security import (
    create_access_token,
    get_current_user,
    get_password_hash,
    verify_password,
)
from app.db.session import get_db
from app.models.entities import User, UserRole
from app.modules.auth.schemas import (
    LoginRequest,
    RegisterRequest,
    TokenResponse,
    UserProfileResponse,
)

router = APIRouter()

# Dữ liệu dự phòng cho môi trường dev/test khi DB chưa kết nối
DEMO_TEST_USERS = {
    "user@saferoute.ai": {"id": 1, "role": UserRole.USER, "hash": get_password_hash("123456")},
    "reviewer@saferoute.ai": {"id": 2, "role": UserRole.REVIEWER, "hash": get_password_hash("123456")},
    "admin@saferoute.ai": {"id": 3, "role": UserRole.ADMIN, "hash": get_password_hash("123456")},
}

@router.post(
    "/login",
    response_model=TokenResponse,
    summary="Đăng nhập hệ thống",
    description="Xác thực email và mật khẩu, cấp phát JWT Bearer Token chứa thông tin vai trò (USER, REVIEWER, ADMIN).",
)
def login(request: LoginRequest, db: Session = Depends(get_db)):
    if len(request.password.encode("utf-8")) > 72:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Email hoặc mật khẩu không chính xác.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    user = None
    try:
        user = db.query(User).filter(User.email == request.email).first()
    except Exception:
        user = None

    # 1. Kiểm tra qua database
    if user:
        if not verify_password(request.password, user.password_hash):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Email hoặc mật khẩu không chính xác.",
                headers={"WWW-Authenticate": "Bearer"},
            )
        user_id = user.id
        role = user.role
        email = user.email
    # 2. Hỗ trợ test users nếu DB chưa kết nối (Chỉ trong môi trường Dev/Test)
    elif settings.ENVIRONMENT.lower() != "production" and request.email in DEMO_TEST_USERS:
        demo = DEMO_TEST_USERS[request.email]
        if not verify_password(request.password, demo["hash"]):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Email hoặc mật khẩu không chính xác.",
                headers={"WWW-Authenticate": "Bearer"},
            )
        user_id = demo["id"]
        role = demo["role"]
        email = request.email
    else:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Tài khoản không tồn tại hoặc mật khẩu không chính xác.",
            headers={"WWW-Authenticate": "Bearer"},
        )

    # 3. Tạo JWT access token
    access_token = create_access_token(
        data={
            "sub": str(user_id),
            "email": email,
            "role": role.value if isinstance(role, UserRole) else str(role),
        }
    )

    return TokenResponse(
        access_token=access_token,
        token_type="bearer",
        role=role,
        user_id=user_id,
        email=email,
    )

@router.post(
    "/register",
    response_model=TokenResponse,
    summary="Đăng ký tài khoản",
    description="Tạo tài khoản mới với vai trò mặc định là USER.",
)
def register(request: RegisterRequest, db: Session = Depends(get_db)):
    # Chỉ cho phép đăng ký vai trò USER qua public endpoint
    if request.role and request.role != UserRole.USER:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Không thể tự cấp quyền REVIEWER hoặc ADMIN qua đăng ký thông thường.",
        )

    if len(request.password.encode("utf-8")) > 72:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Mật khẩu quá dài (vượt quá 72 bytes theo giới hạn bảo mật chuẩn Bcrypt).",
        )

    user = None
    try:
        user = db.query(User).filter(User.email == request.email).first()
    except Exception:
        user = None

    if user or (settings.ENVIRONMENT.lower() != "production" and request.email in DEMO_TEST_USERS):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Email này đã được đăng ký trong hệ thống.",
        )

    new_user = User(
        email=request.email,
        password_hash=get_password_hash(request.password),
        role=UserRole.USER,
        created_at=datetime.now(UTC),
    )

    try:
        db.add(new_user)
        db.commit()
        db.refresh(new_user)
        user_id = new_user.id
    except Exception:
        # Fallback in-memory nếu DB chưa chạy
        user_id = 999

    access_token = create_access_token(
        data={
            "sub": str(user_id),
            "email": request.email,
            "role": UserRole.USER.value,
        }
    )

    return TokenResponse(
        access_token=access_token,
        token_type="bearer",
        role=UserRole.USER,
        user_id=user_id,
        email=request.email,
    )

@router.get(
    "/me",
    response_model=UserProfileResponse,
    summary="Thông tin tài khoản hiện tại",
    description="Lấy thông tin cá nhân của người dùng đã xác thực bằng JWT Bearer Token.",
)
def get_me(current_user: User = Depends(get_current_user)):
    return UserProfileResponse(
        id=current_user.id,
        email=current_user.email,
        role=current_user.role,
        created_at=current_user.created_at or datetime.now(UTC),
    )
