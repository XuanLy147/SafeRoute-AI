"""Bounded image validation and private local-volume storage."""

import io
import uuid
import warnings
from pathlib import Path

from fastapi import HTTPException, UploadFile
from PIL import Image

from app.core.config import settings

FORMATS = {"JPEG": ("image/jpeg", ".jpg"), "PNG": ("image/png", ".png"), "WEBP": ("image/webp", ".webp")}


def validate_image(file: UploadFile):
    limit = settings.MAX_IMAGE_SIZE_MB * 1024 * 1024
    try:
        contents = file.file.read(limit + 1)
    except OSError:
        raise HTTPException(400, "Cannot read image upload") from None
    if len(contents) > limit:
        raise HTTPException(413, "Image exceeds byte limit")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(io.BytesIO(contents)) as img:
                fmt, width, height = img.format, img.width, img.height
                if width * height > 25_000_000:
                    raise HTTPException(413, "Image exceeds pixel limit")
                if fmt not in FORMATS or FORMATS[fmt][0] != file.content_type:
                    raise HTTPException(400, "Image content and MIME do not match")
                img.verify()
            with Image.open(io.BytesIO(contents)) as img:
                img.load()
    except HTTPException:
        raise
    except (Image.DecompressionBombError, Image.DecompressionBombWarning):
        raise HTTPException(413, "Image exceeds pixel limit") from None
    except Exception:
        raise HTTPException(400, "Invalid or truncated image") from None
    return contents, fmt, width, height


def image_url(object_key: str) -> str:
    return f"/api/v1/images/{Path(object_key).stem}"


def save_uploaded_image(file: UploadFile) -> dict:
    contents, fmt, width, height = validate_image(file)
    mime, ext = FORMATS[fmt]
    image_id = uuid.uuid4().hex
    filename = image_id + ext
    try:
        directory = Path(settings.UPLOAD_DIR)
        directory.mkdir(parents=True, exist_ok=True)
        with (directory / filename).open("xb") as target:
            target.write(contents)
    except OSError:
        raise HTTPException(503, "Image storage unavailable") from None
    return dict(
        image_id=image_id,
        filename=filename,
        mime=mime,
        width=width,
        height=height,
        size_bytes=len(contents),
        url=image_url(filename),
    )
