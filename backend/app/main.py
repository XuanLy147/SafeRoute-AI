import os
from datetime import UTC, datetime

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from sqlalchemy import text

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

# 2. Thư mục uploads cho ảnh tĩnh
os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=settings.UPLOAD_DIR), name="uploads")

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
        db_status = f"unavailable: {str(e)[:50]}"

    return {
        "status": "healthy" if "connected" in db_status else "degraded",
        "service": settings.PROJECT_NAME,
        "version": settings.VERSION,
        "database": db_status,
        "timestamp": datetime.now(UTC).isoformat(),
    }

# 4. Gắn router chính v1
app.include_router(api_router, prefix=settings.API_V1_STR)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
