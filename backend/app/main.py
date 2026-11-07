from datetime import UTC, datetime

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
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
    allow_origins=settings.BACKEND_CORS_ORIGINS,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

from fastapi.responses import JSONResponse
from sqlalchemy.exc import IntegrityError, SQLAlchemyError


@app.exception_handler(IntegrityError)
async def integrity_error(request, exc):
    return JSONResponse(status_code=409, content={"detail": "Conflicting persisted state"})


@app.exception_handler(SQLAlchemyError)
async def database_error(request, exc):
    return JSONResponse(status_code=503, content={"detail": "Database temporarily unavailable"})


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
    except Exception:
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


@app.get("/ready", tags=["System"])
def readiness():
    from app.modules.ai.service import ai_service
    from app.modules.risk.policy import load_policy

    try:
        with engine.connect() as conn:
            revision = conn.scalar(text("SELECT version_num FROM alembic_version"))
            conn.execute(text("SELECT PostGIS_Version()"))
        if revision != "0003_role_audit":
            return JSONResponse(status_code=503, content={"detail": "Migration required"})
        policy = load_policy()
    except Exception:
        return JSONResponse(status_code=503, content={"detail": "Dependencies unavailable"})
    return {
        "status": "ready",
        "ai_ready": ai_service.is_model_ready(),
        "risk_ready": policy is not None and policy.approved,
        "risk_policy_revision": policy.revision if policy else None,
    }
