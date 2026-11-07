from datetime import datetime
from typing import Any

from pydantic import BaseModel, Field, model_validator

from app.models.entities import VoteType


class VoteCreateRequest(BaseModel):
    vote: VoteType = Field(
        VoteType.SUPPORT, description="Ý kiến cộng đồng: SUPPORT, OPPOSE, UNSURE (chuẩn hợp đồng TV2/TV3/TV4)"
    )
    vote_type: VoteType | None = Field(None, description="Alias tương thích ngược với Android Retrofit DTO của TV2")
    note: str | None = Field(None, max_length=500, description="Ghi chú đóng góp ý kiến")

    @model_validator(mode="before")
    @classmethod
    def unify_vote_fields(cls, data: Any) -> Any:
        if isinstance(data, dict):
            # Lấy trường vote_type hoặc vote
            raw_val = data.get("vote_type") or data.get("vote")
            if isinstance(raw_val, str):
                val_upper = raw_val.strip().upper()
                if val_upper in ("SUPPORT", "UPVOTE", "UP"):
                    parsed = VoteType.SUPPORT
                elif val_upper in ("OPPOSE", "DOWNVOTE", "DOWN"):
                    parsed = VoteType.OPPOSE
                elif val_upper in ("UNSURE",):
                    parsed = VoteType.UNSURE
                else:
                    parsed = VoteType(val_upper)
                data["vote"] = parsed
                data["vote_type"] = parsed
            elif isinstance(raw_val, VoteType):
                data["vote"] = raw_val
                data["vote_type"] = raw_val
        return data


class VoteResponse(BaseModel):
    id: int
    report_id: int
    user_id: int
    vote: VoteType
    vote_type: VoteType | None = None
    note: str | None = None
    created_at: datetime

    @model_validator(mode="before")
    @classmethod
    def set_vote_type_alias(cls, data: Any) -> Any:
        if isinstance(data, dict):
            if "vote" in data and "vote_type" not in data:
                data["vote_type"] = data["vote"]
        elif hasattr(data, "vote") and not hasattr(data, "vote_type"):
            pass
        return data
