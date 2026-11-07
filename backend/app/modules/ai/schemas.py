from math import isfinite
from typing import Literal

from pydantic import BaseModel, Field, field_validator


class DetectionItem(BaseModel):
    class_code: Literal["D00", "D10", "D20", "D40", "Repair"] = Field(
        ..., description="Mã lớp hư hỏng: D00, D10, D20, D40, Repair"
    )
    class_name: str | None = Field(None, description="Tên loại hư hỏng tiếng Anh/Việt")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Độ tin cậy của mô hình YOLOv12s (0.0 - 1.0)")
    bbox: list[float] = Field(
        ..., min_length=4, max_length=4, description="Normalized [xmin, ymin, xmax, ymax] in [0,1]"
    )

    @field_validator("bbox")
    @classmethod
    def validate_bbox(cls, value):
        if any(not isfinite(v) or not 0 <= v <= 1 for v in value) or value[0] >= value[2] or value[1] >= value[3]:
            raise ValueError("Invalid normalized xyxy box")
        return value


class AiDetectResponse(BaseModel):
    model_version: str = Field("SafeRoute-YOLOv12s-v1.1", description="Phiên bản mô hình AI đã nạp")
    inference_time_ms: float = Field(..., description="Thời gian suy luận (milliseconds)")
    device: str = Field("cpu", description="Thiết bị chạy suy luận (cpu/cuda)")
    detections: list[DetectionItem] = Field(
        default_factory=list,
        description="Danh sách các vùng hư hỏng phát hiện được. Cho phép rỗng [] nếu đường bình thường.",
    )
