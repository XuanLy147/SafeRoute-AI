
from pydantic import BaseModel, Field


class DetectionItem(BaseModel):
    class_code: str = Field(..., description="Mã lớp hư hỏng: D00, D10, D20, D40, Repair")
    class_name: str = Field(..., description="Tên loại hư hỏng tiếng Anh/Việt")
    confidence: float = Field(..., ge=0.0, le=1.0, description="Độ tin cậy của mô hình YOLOv12s (0.0 - 1.0)")
    bbox: list[float] = Field(..., description="Tọa độ Bounding Box [ymin, xmin, ymax, xmax] normalized hoặc pixel")

class AiDetectResponse(BaseModel):
    model_version: str = Field("SafeRoute-YOLOv12s-v1.1", description="Phiên bản mô hình AI đã nạp")
    inference_time_ms: float = Field(..., description="Thời gian suy luận (milliseconds)")
    device: str = Field("cpu", description="Thiết bị chạy suy luận (cpu/cuda)")
    detections: list[DetectionItem] = Field(default_factory=list, description="Danh sách các vùng hư hỏng phát hiện được. Cho phép rỗng [] nếu đường bình thường.")
