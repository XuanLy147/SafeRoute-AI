import os
import re
from datetime import UTC, datetime

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
from starlette.responses import PlainTextResponse, Response
from starlette.staticfiles import StaticFiles

from app.api.v1.api import api_router
from app.core.config import settings
from app.db.session import engine

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    description=(
        "SafeRoute AI Backend API — Hackathon 24H COME FOR FOOD.\n"
        "Hỗ trợ báo cáo nguy cơ đường bộ (YOLOv12s RDD2022) và tìm kiếm tuyến đường an toàn ít nguy cơ (Google Routes + PostGIS)."
    ),
    openapi_url="/api/v1/openapi.json",
    docs_url="/docs",
    redoc_url="/redoc",
)

# 1. Cấu hình CORS (hỗ trợ Android Emulator 10.0.2.2 và thiết bị thật LAN/Cloud)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 2. Thư mục uploads cho ảnh tĩnh kèm rào chắn an toàn (SecureStaticFiles)
SAFE_IMAGE_FILENAME_REGEX = re.compile(r"^[a-fA-F0-9]{32}\.(jpg|jpeg|png|webp)$")


class SecureStaticFiles(StaticFiles):
    """
    Static files handler bảo mật cho thư mục uploads:
    - Chặn Directory Traversal, hidden files, directory listing (html=False).
    - Chỉ cho phép phục vụ tệp hình ảnh có định dạng UUID chuẩn do hệ thống cấp.
    - Bổ sung Security Headers: nosniff, CSP default-src 'none', immutable cache.
    """

    def __init__(self, *args, **kwargs):
        super().__init__(*args, html=False, **kwargs)

    async def get_response(self, path: str, scope) -> Response:
        clean_path = path.strip().lstrip("/\\")
        if not SAFE_IMAGE_FILENAME_REGEX.match(clean_path):
            return PlainTextResponse("Not Found", status_code=404)

        response = await super().get_response(clean_path, scope)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Content-Security-Policy"] = "default-src 'none'"
        response.headers["Cache-Control"] = "public, max-age=86400, immutable"
        return response


os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
app.mount("/uploads", SecureStaticFiles(directory=settings.UPLOAD_DIR), name="uploads")

# 3. Endpoint GET /health (Task T03 - P0)
@app.get("/health", tags=["System"])
def health_check():
    """
    Kiểm tra trạng thái hoạt động của hệ thống backend và cơ sở dữ liệu PostGIS.
    Không làm sập app nếu DB tạm thời chưa sẵn sàng.
    """
    db_status = "offline"
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1;"))
            db_status = "connected"
    except Exception as e:
        print(f"[WARN] Database health check unready: {e}")
        db_status = "offline"

    return {
        "status": "ok",
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "database": db_status,
        "timestamp": datetime.now(UTC).isoformat(),
    }

# 4. Gắn router chính v1
app.include_router(api_router, prefix=settings.API_V1_STR)
