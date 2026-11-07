from datetime import datetime

from pydantic import BaseModel, Field, model_validator

from app.models.entities import ReportStatus


class ReportCreateRequest(BaseModel):
    image_id: str | None = Field(None, description="UUID của ảnh đã tải lên qua POST /api/v1/images")
    latitude: float = Field(..., ge=-90.0, le=90.0, description="Vĩ độ (WGS84), ví dụ: 10.762622")
    longitude: float = Field(..., ge=-180.0, le=180.0, description="Kinh độ (WGS84), ví dụ: 106.660172")
    category_user: str = Field(..., description="Loại hư hỏng do người dùng xác nhận (D00, D10, D20, D40, Repair, hoặc thủ công)")
    severity: str = Field("MEDIUM", description="Mức độ nguy cơ: LOW, MEDIUM, HIGH")
    description: str | None = Field(None, description="Mô tả chi tiết tình trạng đường")

class VotesSummary(BaseModel):
    support: int = 0
    oppose: int = 0
    unsure: int = 0
    total: int = 0

class ReportItemResponse(BaseModel):
    id: int
    user_id: int
    category_user: str
    status: ReportStatus
    severity: str
    latitude: float
    longitude: float
    observed_at: datetime
    description: str | None = None
    image_url: str | None = None
    votes: VotesSummary = VotesSummary()
    # Các trường tương thích trực tiếp với Android Retrofit DTO của TV2
    support_votes: int = 0
    oppose_votes: int = 0
    unsure_votes: int = 0

    @model_validator(mode="after")
    def sync_vote_counts(self):
        if self.votes:
            if self.support_votes == 0 and self.votes.support > 0:
                self.support_votes = self.votes.support
            if self.oppose_votes == 0 and self.votes.oppose > 0:
                self.oppose_votes = self.votes.oppose
            if self.unsure_votes == 0 and self.votes.unsure > 0:
                self.unsure_votes = self.votes.unsure
        return self

class ReportDetailResponse(ReportItemResponse):
    images: list[dict[str, str]] = []
    ai_predictions: list[dict[str, object]] = []
    review_logs: list[dict[str, object]] = []
