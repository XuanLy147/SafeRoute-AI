import logging

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

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
from app.modules.routing.schemas import (
    RouteCompareRequest,
    RouteCompareResponse,
)
from app.modules.routing.service import RoutingService

logger = logging.getLogger(__name__)

from app.core.errors import error_responses

router = APIRouter(responses=error_responses(400, 404, 429, 502, 503, 504))


def get_risk_scorer(db: Session):
    from app.modules.risk.policy import load_policy
    from app.modules.risk.postgis_adapter import KeyedPostgisRiskAdapter

    policy = load_policy()
    if policy is None:
        return None
    scorer = KeyedPostgisRiskAdapter(
        db, policy.core(), radius_m=policy.radius_meters, medium_min=policy.medium_min, high_min=policy.high_min
    )

    scorer.policy_approved = policy.approved
    return scorer


@router.post(
    "/compare",
    response_model=RouteCompareResponse,
    status_code=status.HTTP_200_OK,
    summary="So sánh các tuyến đường và tích hợp rủi ro mặt đường",
    description=(
        "Nhận tọa độ điểm đi (origin) và điểm đến (destination), gọi Google Routes API v2 (TV5), "
        "giải mã polyline, đối soát không gian với các nguy cơ mặt đường đã ghi nhận trong PostGIS (TV4) "
        "và đề xuất tuyến có ít nguy cơ đã ghi nhận hơn."
    ),
)
def compare_routes(
    request: RouteCompareRequest,
    db: Session = Depends(get_db),
):
    from app.core.config import settings
    from app.modules.risk.policy import load_policy
    try:
        load_policy()  # Preserve validation of any existing configured policy.
    except ValueError:
        raise HTTPException(503, "Risk policy configuration invalid") from None
    production = settings.ENVIRONMENT.lower() == "production"
    scorer = get_risk_scorer(db) if production else None
    adapter = GoogleRoutesAdapter()
    service = RoutingService(adapter=adapter, risk_scorer=scorer)

    try:
        result = service.compare_routes(
            origin=request.origin,
            destination=request.destination,
            travel_mode=request.travel_mode,
        )
        from app.modules.routing.evidence_scoring import assess_comparison
        from app.modules.routing.hazards import attach_route_hazards
        try:
            attach_route_hazards(result.routes, db)
            if not production:
                assess_comparison(result)
        except Exception:
            db.rollback()
            logger.warning("Route hazard assessment unavailable")
            # Never publish a partially mutated or mismatched assessment.
            for route in result.routes:
                route.nearby_hazards = None
                if not production:
                    route.risk = None
                    route.severity_counts = None
                    route.verified_high_count = 0
                    route.unscored_hazards_count = 0
                    route.risk_density_per_km = None
            if not production:
                assess_comparison(result)
        if production and scorer is not None:
            result.risk_policy_revision = scorer.policy.revision
            result.risk_policy_status = "APPROVED" if scorer.policy_approved else "DEMO_UNAPPROVED"
        return RouteCompareResponse.model_validate(result.model_dump())
    except InvalidCoordinatesError:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid or identical coordinates",
        )
    except NoRouteFoundError:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Route provider request failed",
        )
    except RouteServiceQuotaError:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Route provider request failed",
        )
    except RouteServiceTimeoutError:
        raise HTTPException(
            status_code=status.HTTP_504_GATEWAY_TIMEOUT,
            detail="Route provider request failed",
        )
    except RouteServiceConnectionError:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Route provider request failed",
        )
    except RouteServiceAuthError:
        raise HTTPException(502, "Google Routes authentication/configuration unavailable") from None
    except Exception:
        logger.error("Route comparison failed")
        raise HTTPException(502, "Route provider failed") from None
