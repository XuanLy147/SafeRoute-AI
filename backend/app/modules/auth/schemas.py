from datetime import datetime

from pydantic import BaseModel, EmailStr, Field

from app.models.entities import UserRole


class LoginRequest(BaseModel):
    email: EmailStr
    password: str = Field(..., min_length=6, description="Mật khẩu tối thiểu 6 ký tự")


class RegisterRequest(BaseModel):
    full_name: str | None = Field(None, max_length=200)
    email: EmailStr
    password: str = Field(..., min_length=6, description="Mật khẩu tối thiểu 6 ký tự")
    role: UserRole | None = UserRole.USER


class TokenResponse(BaseModel):
    full_name: str | None = None
    access_token: str
    token_type: str = "bearer"
    role: UserRole
    user_id: int
    email: str


class UserProfileResponse(BaseModel):
    full_name: str | None = None
    id: int
    email: str
    role: UserRole
    created_at: datetime
