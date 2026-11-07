import io
import logging

from fastapi import APIRouter, File, HTTPException, UploadFile, status
from PIL import Image, UnidentifiedImageError

from app.core.config import settings
from app.modules.ai.schemas import AiDetectResponse
from app.modules.ai.service import ai_service

logger = logging.getLogger(__name__)

router = APIRouter()

# Thiết lập an toàn cho thư viện PIL để chống tấn công Decompression Bomb
Image.MAX_IMAGE_PIXELS = 25_000_000


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

    # 1. Kiểm tra kích thước file tối đa (tránh DoS / Memory exhaustion)
    max_bytes = settings.MAX_IMAGE_SIZE_MB * 1024 * 1024
    if len(image_bytes) > max_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Kích thước file ({len(image_bytes) / 1024 / 1024:.2f} MB) vượt quá giới hạn cho phép ({settings.MAX_IMAGE_SIZE_MB} MB).",
        )

    # 2. Kiểm tra cấu trúc magic bytes của tệp ảnh
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

    # 3. Kiểm định giải mã hình ảnh thực tế qua PIL (Phát hiện ảnh bị hỏng / corrupt stream / decompression bomb)
    try:
        with Image.open(io.BytesIO(image_bytes)) as img:
            img.verify()
    except UnidentifiedImageError:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Không thể giải mã dữ liệu ảnh: Tệp ảnh bị hỏng hoặc không đúng chuẩn.",
        )
    except getattr(Image, "DecompressionBombError", Exception):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Kích thước giải nén của ảnh vượt ngưỡng an toàn (Decompression Bomb protection).",
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Dữ liệu ảnh không hợp lệ hoặc bị lỗi định dạng: {e!s}",
        )

    # 4. Rào chắn production
    if settings.ENVIRONMENT.lower() == "production" and not ai_service.is_model_ready():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI Inference Engine chưa sẵn sàng trong môi trường production (Model chưa nạp hoặc không hợp lệ).",
        )

    result = ai_service.predict(image_bytes, filename=file.filename or "image.jpg")
    return result
