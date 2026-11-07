import re
from pathlib import Path

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.errors import error_responses
from app.core.security import get_current_user
from app.db.session import get_db
from app.models.entities import ReportImage, User, UserRole
from app.modules.images.schemas import ImageUploadResponse
from app.modules.images.service import save_uploaded_image

router = APIRouter(responses=error_responses(400, 401, 403, 404, 413, 503))


def find_image(db, image_id):
    if not re.fullmatch(r"[a-f0-9]{32}", image_id):
        raise HTTPException(400, "Invalid image ID")
    keys = [image_id + ext for ext in (".jpg", ".png", ".webp", ".jpeg")] + [image_id]
    row = db.query(ReportImage).filter(ReportImage.object_key.in_(keys)).with_for_update().first()
    if row is None:
        raise HTTPException(404, "Image not found")
    return row


@router.post("", response_model=ImageUploadResponse)
def upload_image(
    file: UploadFile = File(...), current_user: User = Depends(get_current_user), db: Session = Depends(get_db)
):
    metadata = save_uploaded_image(file)
    row = ReportImage(
        owner_id=current_user.id,
        object_key=metadata["filename"],
        mime=metadata["mime"],
        width=metadata["width"],
        height=metadata["height"],
    )
    try:
        db.add(row)
        db.commit()
    except Exception:
        db.rollback()
        (Path(settings.UPLOAD_DIR) / metadata["filename"]).unlink(missing_ok=True)
        raise
    return ImageUploadResponse(**metadata)


@router.get("/{image_id}")
def get_image(image_id: str, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    row = find_image(db, image_id)
    if row.owner_id != current_user.id and current_user.role not in (UserRole.REVIEWER, UserRole.ADMIN):
        raise HTTPException(403, "Private image access denied")
    keys = (
        [row.object_key]
        if Path(row.object_key).suffix
        else [row.object_key + e for e in (".jpg", ".png", ".webp", ".jpeg")]
    )
    for key in keys:
        if not re.fullmatch(r"[a-f0-9]{32}\.(jpg|jpeg|png|webp)", key):
            continue
        path = Path(settings.UPLOAD_DIR) / key
        if path.is_file():
            return FileResponse(
                path,
                media_type=row.mime,
                headers={
                    "Cache-Control": "private, no-store",
                    "X-Content-Type-Options": "nosniff",
                    "Content-Security-Policy": "default-src 'none'",
                },
            )
    raise HTTPException(404, "Image object missing")
