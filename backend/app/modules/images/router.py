import os
import re

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile, status
from fastapi.responses import FileResponse

from app.core.config import settings
from app.core.security import get_current_user
from app.models.entities import User
from app.modules.images.schemas import ImageUploadResponse
from app.modules.images.service import save_uploaded_image

router = APIRouter()

IMAGE_ID_REGEX = re.compile(r"^[a-fA-F0-9]{32}$")


@router.post(
    "",
    response_model=ImageUploadResponse,
    summary="Tải lên ảnh bằng chứng nguy cơ",
    description="Kiểm tra định dạng (JPEG, PNG, WEBP), magic bytes, giới hạn 5MB và lưu trữ ảnh an toàn với UUID.",
)
def upload_image(
    file: UploadFile = File(..., description="File ảnh bằng chứng"),
    current_user: User = Depends(get_current_user),
):
    metadata = save_uploaded_image(file)
    return ImageUploadResponse(**metadata)


@router.get(
    "/{image_id}",
    summary="Lấy tệp ảnh đã tải lên một cách an toàn",
    description="Phục vụ ảnh theo image_id (UUID 32 ký tự). Áp dụng rào chắn kiểm tra định dạng và bổ sung security headers (nosniff, CSP).",
)
def get_image(image_id: str):
    # 1. Chống Path Traversal bằng regex kiểm tra UUID hex
    if not IMAGE_ID_REGEX.match(image_id):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Mã nhận diện ảnh (image_id) không hợp lệ.",
        )

    # 2. Tìm tệp trong UPLOAD_DIR
    found_path = None
    for ext in [".jpg", ".jpeg", ".png", ".webp"]:
        candidate = os.path.join(settings.UPLOAD_DIR, f"{image_id}{ext}")
        if os.path.exists(candidate) and os.path.isfile(candidate):
            found_path = candidate
            break

    if not found_path:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Không tìm thấy tệp ảnh yêu cầu.",
        )

    headers = {
        "X-Content-Type-Options": "nosniff",
        "Content-Security-Policy": "default-src 'none'",
        "Cache-Control": "public, max-age=86400, immutable",
    }
    return FileResponse(found_path, headers=headers)
