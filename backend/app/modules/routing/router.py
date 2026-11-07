import logging
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.config import settings
from app.db.session import get_db
from app.modules.routing.adapter import GoogleRoutesAdapter
from app.modules.routing.exceptions import (
    InvalidCoordinatesError,
    NoRouteFoundError,
    RouteServiceAuthError,
    RouteServiceConnectionError,
    RouteServiceQuotaError,
    RouteServiceTimeoutError,
)
from app.modules.routing.fixtures import FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES
from app.modules.routing.schemas import (
    RouteCompareRequest,
    RouteCompareResponse,
)
from app.modules.routing.service import RoutingService

logger = logging.getLogger(__name__)

router = APIRouter()


def get_risk_scorer(db: Session) -> Any:
    """
    Khởi tạo PostgisRiskAdapter của TV4 nếu có cấu hình PostGIS.
    Nếu DB không hỗ trợ PostGIS hoặc đang chạy SQLite test, trả về None (graceful fallback).
    """
    try:
        from app.modules.risk.postgis_adapter import PostgisRiskAdapter
        from app.modules.risk.scoring import (
            DEFAULT_SEVERITY_WEIGHTS,
            RiskPolicy,
        )

        policy = RiskPolicy(
            weights=DEFAULT_SEVERITY_WEIGHTS,
            verification_weight=1.0,
            unverified_weight=0.5,
        )
        return PostgisRiskAdapter(
            session=db,
            policy=policy,
            radius_m=50.0,
            medium_min=3.0,
            high_min=7.0,
        )
    except Exception as e:
        logger.info(f"PostGIS Risk Scorer chua kha dung hoac offline ({e}). Chuyen sang danh gia theo thoi gian di chuyen.")
        return None


@router.post(
    "/compare",
    response_model=RouteCompareResponse,
    status_code=status.HTTP_200_OK,
    summary="So sánh các tuyến đường và tích hợp rủi ro mặt đường",
    description=(
        "Nhận tọa độ điểm đi (origin) và điểm đến (destination), gọi Google Routes API v2 (TV5), "
        "giải mã polyline, đối soát không gian với các nguy cơ mặt đường đã ghi nhận trong PostGIS (TV4) "
        "và đề xuất tuyến đường an toàn nhất."
    ),
)
def compare_routes(
    request: RouteCompareRequest,
    db: Session = Depends(get_db),
):
    adapter = GoogleRoutesAdapter()
    risk_scorer = get_risk_scorer(db)
    service = RoutingService(adapter=adapter, risk_scorer=risk_scorer)

    try:
        return service.compare_routes(
            origin=request.origin,
            destination=request.destination,
            travel_mode=request.travel_mode,
        )
    except InvalidCoordinatesError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Tọa độ không hợp lệ: {e!s}",
        )
    except NoRouteFoundError as e:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Không tìm thấy tuyến đường khả dụng: {e!s}",
        )
    except RouteServiceQuotaError as e:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Dịch vụ định tuyến vượt quá hạn ngạch (Quota Exceeded): {e!s}",
        )
    except RouteServiceTimeoutError as e:
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail=f"Yêu cầu định tuyến hết thời gian phản hồi: {e!s}",
        )
    except RouteServiceConnectionError as e:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Không thể kết nối đến máy chủ định tuyến Google Routes: {e!s}",
        )
    except RouteServiceAuthError as e:
        # Nếu đang trong môi trường phát triển / kiểm thử và chưa nạp API key thật
        if settings.ENVIRONMENT.lower() != "production" and not adapter.api_key:
            logger.warning("GOOGLE_ROUTES_API_KEY chua duoc cau hinh. Kich hoat MOCK_FOR_DEV fixture.")
            mock_candidates = adapter.parse_routes_response(FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES)

            class MockAdapter:
                def compute_routes(self, *args, **kwargs):
                    return mock_candidates

            mock_service = RoutingService(adapter=MockAdapter(), risk_scorer=risk_scorer)
            return mock_service.compare_routes(
                origin=request.origin,
                destination=request.destination,
                travel_mode=request.travel_mode,
            )
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Lỗi xác thực dịch vụ Google Routes: {e!s}",
        )
    except Exception as e:
        logger.exception(f"Lỗi không xác định khi so sánh tuyến: {e}")
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Đã xảy ra lỗi nội bộ trong quá trình tính toán lộ trình.",
        )
