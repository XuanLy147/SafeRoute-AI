from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.models.entities import UserRole


class UserSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    email: str
    full_name: str | None
    role: UserRole
    created_at: datetime


class RoleChangeRequest(BaseModel):
    role: UserRole
    expected_role: UserRole
    reason: str = Field(min_length=3, max_length=500)

    @field_validator("reason")
    @classmethod
    def reason_not_blank(cls, value):
        value = value.strip()
        if len(value) < 3:
            raise ValueError("A meaningful reason is required")
        return value


class RoleChangeResponse(BaseModel):
    user: UserSummary
    audit_id: int


class RoleAuditResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    actor_id: int
    user_id: int
    from_role: UserRole
    to_role: UserRole
    reason: str
    created_at: datetime
