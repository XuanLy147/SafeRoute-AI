from datetime import datetime

from pydantic import BaseModel, Field

from app.models.entities import ReportStatus


class ReviewStatusPatchRequest(BaseModel):
    status: ReportStatus = Field(..., description="Trạng thái phê duyệt mới: VERIFIED hoặc REJECTED")
    reason: str = Field(..., min_length=5, description="Lý do xác minh hoặc từ chối (bắt buộc tối thiểu 5 ký tự)")


class ReviewResponse(BaseModel):
    report_id: int
    status: ReportStatus
    actor_id: int
    reason: str
    created_at: datetime
