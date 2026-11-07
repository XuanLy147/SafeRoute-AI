from fastapi import APIRouter
from pydantic import BaseModel

from app.modules.ai.router import router as ai_router
from app.modules.auth.router import router as auth_router
from app.modules.images.router import router as images_router
from app.modules.reports.router import router as reports_router
from app.modules.review.router import review_report_status
from app.modules.review.router import router as review_router
from app.modules.review.schemas import ReviewResponse
from app.modules.routing.router import router as routing_router
from app.modules.votes.router import router as votes_router

api_router = APIRouter()


class PingResponse(BaseModel):
    message: str = "SafeRoute AI API v1 ready"


@api_router.get("/ping", response_model=PingResponse, tags=["General"])
def ping():
    return PingResponse()


# 1. Gắn Auth router: /api/v1/auth/login, /api/v1/auth/register, /api/v1/auth/me
api_router.include_router(auth_router, prefix="/auth", tags=["Auth"])

# 2. Gắn Images router: /api/v1/images
api_router.include_router(images_router, prefix="/images", tags=["Images"])

# 3. Gắn Reports router: /api/v1/reports, /api/v1/reports/mine, /api/v1/reports/{id}
api_router.include_router(reports_router, prefix="/reports", tags=["Reports"])

# 4. Gắn Community Votes router: /api/v1/reports/{id}/votes
api_router.include_router(votes_router, prefix="/reports", tags=["Community Votes"])

# 5. Gắn Review Queue: /api/v1/review/queue
api_router.include_router(review_router, prefix="/review", tags=["Review & Audit"])

# Hỗ trợ thêm alias đường dẫn theo contract: PATCH /api/v1/reports/{id}/status
api_router.add_api_route(
    "/reports/{report_id}/status",
    review_report_status,
    methods=["PATCH"],
    response_model=ReviewResponse,
    tags=["Review & Audit"],
    summary="Xét duyệt báo cáo (Alias endpoint: PATCH /api/v1/reports/{id}/status)",
)

# 6. Gắn AI Inference router: /api/v1/ai/detect
api_router.include_router(ai_router, prefix="/ai", tags=["AI Inference"])

# 7. Gắn Routing router: /api/v1/routes/compare (TV5 + TV4)
api_router.include_router(routing_router, prefix="/routes", tags=["Routing"])

from app.modules.admin.router import router as admin_router

api_router.include_router(admin_router, prefix="/admin", tags=["Administration"])

from app.modules.moderation.router import router as moderation_router

api_router.include_router(moderation_router, tags=["Moderation views"])
