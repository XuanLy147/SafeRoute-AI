from fastapi import APIRouter, Depends, File, UploadFile

from app.core.security import get_current_user
from app.models.entities import User
from app.modules.images.schemas import ImageUploadResponse
from app.modules.images.service import save_uploaded_image

router = APIRouter()

@router.post(
    "",
    response_model=ImageUploadResponse,
    summary="Tải lên ảnh bằng chứng nguy cơ",
    description="Kiểm tra định dạng (JPEG, PNG, WEBP), giới hạn 5MB và lưu trữ ảnh an toàn với UUID.",
)
def upload_image(
    file: UploadFile = File(..., description="File ảnh bằng chứng"),
    current_user: User = Depends(get_current_user),
):
    metadata = save_uploaded_image(file)
    return ImageUploadResponse(**metadata)
