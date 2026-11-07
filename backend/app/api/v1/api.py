from fastapi import APIRouter
from pydantic import BaseModel

api_router = APIRouter()

class PingResponse(BaseModel):
    message: str = "SafeRoute AI API v1 ready"

@api_router.get("/ping", response_model=PingResponse, tags=["General"])
def ping():
    return PingResponse()

# Các router con sẽ được gắn vào đây khi hiện thực:
# from app.modules.auth.router import router as auth_router
# from app.modules.reports.router import router as reports_router
# from app.modules.votes.router import router as votes_router
# from app.modules.review.router import router as review_router
# from app.modules.ai.router import router as ai_router
# from app.modules.routing.router import router as routing_router

# api_router.include_router(auth_router, prefix="/auth", tags=["Auth"])
# api_router.include_router(reports_router, prefix="/reports", tags=["Reports"])
# api_router.include_router(votes_router, prefix="/reports", tags=["Votes"])
# api_router.include_router(review_router, prefix="/review", tags=["Review"])
# api_router.include_router(ai_router, prefix="/ai", tags=["AI"])
# api_router.include_router(routing_router, prefix="/routes", tags=["Routing"])
