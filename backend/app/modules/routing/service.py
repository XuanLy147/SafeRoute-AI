"""Routing Service (TV5).

Orchestrates Google Routes API candidate fetching, interfaces with
TV4 Risk Scorer (PostGIS hazard analysis), and constructs the final
RouteCompareResponse for TV3 API endpoint and TV1 Android client.
"""

from typing import Callable, Dict, List, Optional, Protocol, Union
import logging

from .adapter import GoogleRoutesAdapter
from .schemas import (
    EvaluatedRoute,
    LatLng,
    RouteCandidate,
    RouteCompareRequest,
    RouteCompareResponse,
    RouteRiskScore,
    TravelMode,
)

logger = logging.getLogger(__name__)

SAFE_ROUTE_DISCLAIMER = (
    "Tuyến đường được đề xuất dựa trên các nguy cơ mặt đường đã ghi nhận trong hệ thống. "
    "SafeRoute AI không cam kết tuyến đường an toàn tuyệt đối."
)


class RiskScorerProtocol(Protocol):
    """Protocol for TV4 Risk Scorer integration.

    TV4 implements risk calculation using PostGIS ST_DWithin buffer queries
    against recorded and verified road hazard reports.
    """

    def evaluate_routes(
        self, candidates: List[RouteCandidate]
    ) -> Union[List[RouteRiskScore], Dict[str, RouteRiskScore]]:
        ...


class RoutingService:
    """Service to compare candidate routes and integrate risk evaluations."""

    def __init__(
        self,
        adapter: Optional[GoogleRoutesAdapter] = None,
        risk_scorer: Optional[RiskScorerProtocol] = None,
    ):
        """Initialize RoutingService.

        Args:
            adapter: GoogleRoutesAdapter instance.
            risk_scorer: Optional TV4 risk scorer implementation.
        """
        self.adapter = adapter or GoogleRoutesAdapter()
        self.risk_scorer = risk_scorer

    def compare_routes(
        self,
        origin: LatLng,
        destination: LatLng,
        travel_mode: TravelMode = TravelMode.TWO_WHEELER,
    ) -> RouteCompareResponse:
        """Fetch candidate routes and evaluate risks against recorded hazards.

        Business rules strictly applied:
        - Exactly the number of routes returned by Google is passed through (0 -> 404, 1 -> 1, N -> N).
        - No invented routes or fake hazards.
        - Preserves all candidate IDs, counts, and exact ordering.
        - Recommended route is the one with the lowest recorded risk score.
        - Legal disclaimer always included.
        """
        # 1. Fetch real candidates from Google Routes API
        candidates: List[RouteCandidate] = self.adapter.compute_routes(
            origin=origin,
            destination=destination,
            travel_mode=travel_mode,
        )

        # 2. Risk scoring (via TV4 scorer if provided)
        # BUGFIX (TV4 Contract Gate): Do NOT use zip() which silently drops candidates
        # when the risk scorer returns fewer items than candidates or raises an error.
        risk_map: Dict[str, Optional[RouteRiskScore]] = {}
        if self.risk_scorer is not None and candidates:
            try:
                raw_risk = self.risk_scorer.evaluate_routes(candidates)
                if isinstance(raw_risk, dict):
                    risk_map = raw_risk
                elif isinstance(raw_risk, (list, tuple)):
                    if len(raw_risk) != len(candidates):
                        logger.warning(
                            "Risk scorer returned %d scores for %d candidates. Missing candidates mapped to None.",
                            len(raw_risk),
                            len(candidates),
                        )
                    for idx, candidate in enumerate(candidates):
                        risk_map[candidate.id] = raw_risk[idx] if idx < len(raw_risk) else None
                else:
                    logger.warning("Unrecognized risk scorer return type: %s", type(raw_risk))
            except Exception as exc:
                logger.error("Error executing risk scorer: %s. Preserving routes without synthetic risk.", exc)
                risk_map = {}

        # Build evaluated routes strictly preserving candidates order, IDs, and count
        evaluated_routes: List[EvaluatedRoute] = []
        for candidate in candidates:
            risk = risk_map.get(candidate.id)
            evaluated_routes.append(
                EvaluatedRoute(
                    id=candidate.id,
                    summary=candidate.summary,
                    distance_meters=candidate.distance_meters,
                    duration_seconds=candidate.duration_seconds,
                    polyline=candidate.polyline,
                    risk=risk,
                )
            )

        # 3. Determine recommended route and explanation
        recommended_route_id: Optional[str] = None
        recommendation_reason: Optional[str] = None

        if len(evaluated_routes) == 1:
            # Single candidate from Google
            single = evaluated_routes[0]
            recommended_route_id = single.id
            if single.risk and single.risk.hazards_count > 0:
                recommendation_reason = (
                    f"Tuyến duy nhất tìm thấy ({single.summary}). "
                    f"Ghi nhận {single.risk.hazards_count} nguy cơ trên lộ trình."
                )
            else:
                recommendation_reason = (
                    f"Tuyến duy nhất tìm thấy ({single.summary}). "
                    "Chưa ghi nhận nguy cơ mặt đường nào trên lộ trình này."
                )
        elif len(evaluated_routes) > 1:
            # Multiple candidates: pick route with lowest risk score
            # In case of tie or no risk scorer, pick shortest duration
            has_risks = any(r.risk is not None for r in evaluated_routes)
            if has_risks:
                best_route = min(
                    evaluated_routes,
                    key=lambda r: (
                        r.risk.score if r.risk else 999.0,
                        r.duration_seconds,
                        r.distance_meters,
                    ),
                )
                recommended_route_id = best_route.id
                if best_route.risk and best_route.risk.hazards_count == 0:
                    recommendation_reason = (
                        f"Tuyến '{best_route.summary}' được đề xuất vì chưa ghi nhận nguy cơ nào "
                        f"trên hệ thống (so với các tuyến khác)."
                    )
                elif best_route.risk:
                    recommendation_reason = (
                        f"Tuyến '{best_route.summary}' được đề xuất vì có mức nguy cơ đã ghi nhận thấp nhất "
                        f"(điểm nguy cơ: {best_route.risk.score:.1f}, {best_route.risk.hazards_count} nguy cơ)."
                    )
            else:
                # No risk data available: recommend shortest route
                best_route = min(evaluated_routes, key=lambda r: r.duration_seconds)
                recommended_route_id = best_route.id
                recommendation_reason = (
                    f"Tuyến '{best_route.summary}' có thời gian di chuyển dự kiến ngắn nhất "
                    f"({best_route.duration_seconds // 60} phút). Dữ liệu nguy cơ mặt đường đang chờ cập nhật."
                )

        return RouteCompareResponse(
            origin=origin,
            destination=destination,
            travel_mode=travel_mode,
            routes=evaluated_routes,
            recommended_route_id=recommended_route_id,
            recommendation_reason=recommendation_reason,
            disclaimer=SAFE_ROUTE_DISCLAIMER,
        )

