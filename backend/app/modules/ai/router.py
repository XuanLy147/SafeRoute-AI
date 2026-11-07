from fastapi import APIRouter, File, HTTPException, UploadFile, status

from app.core.config import settings
from app.modules.ai.schemas import AiDetectResponse
from app.modules.ai.service import ai_service

router = APIRouter()

@router.post(
    "/detect",
    response_model=AiDetectResponse,
    summary="Phát hiện hư hỏng mặt đường bằng AI YOLOv12s",
    description="Nhận ảnh mặt đường và trả về danh sách các vùng phát hiện (D00, D10, D20, D40, Repair). Cho phép trả về detections=[] khi mặt đường bình thường.",
)
def detect_road_hazards(
    file: UploadFile = File(..., description="Ảnh mặt đường cần nhận diện"),
):
    content_type = file.content_type or ""
    if content_type.lower() not in settings.ALLOWED_IMAGE_TYPES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Định dạng file không hợp lệ: '{content_type}'. Chỉ chấp nhận {settings.ALLOWED_IMAGE_TYPES}.",
        )

    try:
        image_bytes = file.file.read()
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Không thể đọc file ảnh: {e!s}",
        )

    if len(image_bytes) == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="File ảnh rỗng (0 bytes).",
        )

    # Kiểm tra cấu trúc magic bytes của tệp ảnh
    ct_lower = content_type.lower()
    is_valid_magic = False
    if "jpeg" in ct_lower or "jpg" in ct_lower:
        is_valid_magic = image_bytes.startswith(b"\xff\xd8\xff")
    elif "png" in ct_lower:
        is_valid_magic = image_bytes.startswith(b"\x89PNG\r\n\x1a\n")
    elif "webp" in ct_lower:
        is_valid_magic = image_bytes.startswith(b"RIFF") and (len(image_bytes) >= 12 and image_bytes[8:12] == b"WEBP")

    if not is_valid_magic:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Nội dung file không khớp với cấu trúc tệp ảnh hợp lệ (Magic bytes mismatch).",
        )

    # Rào chắn production
    if settings.ENVIRONMENT.lower() == "production" and not ai_service.is_model_ready():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI Inference Engine chưa sẵn sàng trong môi trường production (Model chưa nạp hoặc không hợp lệ).",
        )

    result = ai_service.predict(image_bytes, filename=file.filename or "image.jpg")
    return result
