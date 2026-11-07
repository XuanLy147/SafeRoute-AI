import os
import uuid

from fastapi import HTTPException, UploadFile, status

from app.core.config import settings


def save_uploaded_image(file: UploadFile) -> dict:
    """
    Kiểm định MIME type, dung lượng và lưu trữ ảnh an toàn.
    Chống tấn công Path Traversal bằng UUID v4.
    """
    # 1. Kiểm tra MIME type
    content_type = file.content_type or ""
    if content_type.lower() not in settings.ALLOWED_IMAGE_TYPES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                f"Định dạng ảnh không được hỗ trợ: '{content_type}'. "
                f"Hệ thống chỉ chấp nhận: {settings.ALLOWED_IMAGE_TYPES}."
            ),
        )

    # 2. Đọc và kiểm tra kích thước file
    try:
        contents = file.file.read()
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Không thể đọc nội dung file tải lên: {e!s}",
        )

    size_bytes = len(contents)
    max_bytes = settings.MAX_IMAGE_SIZE_MB * 1024 * 1024
    if size_bytes > max_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Kích thước file ({size_bytes / 1024 / 1024:.2f} MB) vượt quá giới hạn cho phép ({settings.MAX_IMAGE_SIZE_MB} MB).",
        )

    if size_bytes == 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="File tải lên không có dữ liệu (0 byte).",
        )

    # 2.5 Kiểm tra magic bytes thực tế để ngăn chặn file thực thi giả mạo đuôi ảnh
    is_valid_magic = False
    ct_lower = content_type.lower()
    if "jpeg" in ct_lower or "jpg" in ct_lower:
        is_valid_magic = contents.startswith(b"\xff\xd8\xff")
    elif "png" in ct_lower:
        is_valid_magic = contents.startswith(b"\x89PNG\r\n\x1a\n")
    elif "webp" in ct_lower:
        is_valid_magic = contents.startswith(b"RIFF") and (len(contents) >= 12 and contents[8:12] == b"WEBP")

    if not is_valid_magic:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Nội dung file không khớp với cấu trúc tệp ảnh hợp lệ (Magic bytes mismatch).",
        )

    # 3. Tạo tên file duy nhất bằng UUID4
    ext = os.path.splitext(file.filename or "")[1].lower()
    if not ext:
        if "png" in content_type:
            ext = ".png"
        elif "webp" in content_type:
            ext = ".webp"
        else:
            ext = ".jpg"

    image_id = uuid.uuid4().hex
    safe_filename = f"{image_id}{ext}"
    target_path = os.path.join(settings.UPLOAD_DIR, safe_filename)

    os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
    with open(target_path, "wb") as f:
        f.write(contents)

    return {
        "image_id": image_id,
        "filename": safe_filename,
        "mime": content_type,
        "size_bytes": size_bytes,
        "url": f"/uploads/{safe_filename}",
    }
