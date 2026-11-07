"""Routing Service (TV5).

Orchestrates Google Routes API candidate fetching, interfaces with
TV4 Risk Scorer (PostGIS hazard analysis), and constructs the final
RouteCompareResponse for TV3 API endpoint and TV1 Android client.
"""

import logging
from enum import Enum
from typing import Protocol

from .adapter import GoogleRoutesAdapter
from .exceptions import NoRouteFoundError
from .schemas import (
    EvaluatedRoute,
    LatLng,
    RouteCandidate,
    RouteCompareResponse,
    RouteRiskScore,
    TravelMode,
)

logger = logging.getLogger(__name__)

SAFE_ROUTE_DISCLAIMER = (
    "Tuyến đường được đề xuất dựa trên các nguy cơ mặt đường đã ghi nhận trong hệ thống. "
    "SafeRoute AI không cam kết tuyến đường an toàn tuyệt đối."
)


class RiskAssessmentStatus(str, Enum):
    """Semantic status of hazard assessment for a set of candidate routes."""
    AVAILABLE = "AVAILABLE"          # All candidate routes have valid, complete risk scores
    UNAVAILABLE = "UNAVAILABLE"      # Scorer not configured or call failed; no risk data
    PARTIAL = "PARTIAL"              # Some candidates have risk, some are missing
    INVALID = "INVALID"              # Scorer returned unexpected keys or schema violations


class RecommendationBasis(str, Enum):
    """Basis for selecting the recommended route."""
    SAFETY_LOWEST_RISK = "SAFETY_LOWEST_RISK"
    DURATION_ETA_ONLY = "DURATION_ETA_ONLY"
    SINGLE_ROUTE = "SINGLE_ROUTE"


class RiskScorerProtocol(Protocol):
    """Protocol for TV4 Risk Scorer integration.

    TV4 implements risk calculation using PostGIS ST_DWithin buffer queries
    against recorded and verified road hazard reports.
    """

    def evaluate_by_id(
        self, candidates: list[RouteCandidate]
    ) -> dict[str, RouteRiskScore]:
        ...

    def evaluate_routes(
        self, candidates: list[RouteCandidate]
    ) -> list[RouteRiskScore] | dict[str, RouteRiskScore]:
        ...


class RoutingService:
    """Service to compare candidate routes and integrate risk evaluations."""

    def __init__(
        self,
        adapter: GoogleRoutesAdapter | None = None,
        risk_scorer: RiskScorerProtocol | None = None,
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
        - No sentinel 999.0; explicit risk availability semantics.
        - Recommended route is selected by lowest risk score only when all routes are assessed.
        - Legal disclaimer always included.
        """
        # 1. Fetch real candidates from Google Routes API
        candidates: list[RouteCandidate] = self.adapter.compute_routes(
            origin=origin,
            destination=destination,
            travel_mode=travel_mode,
        )

        if not candidates:
            raise NoRouteFoundError(
                f"No route found between ({origin.latitude}, {origin.longitude}) "
                f"and ({destination.latitude}, {destination.longitude})"
            )

        # Validate unique candidate IDs
        candidate_ids = [c.id for c in candidates]
        if len(candidate_ids) != len(set(candidate_ids)):
            raise ValueError(f"Duplicate candidate route IDs detected: {candidate_ids}")

        # 2. Risk scoring via TV4 scorer
        risk_map: dict[str, RouteRiskScore | None] = {c.id: None for c in candidates}
        assessment_status = RiskAssessmentStatus.UNAVAILABLE

        if self.risk_scorer is not None:
            try:
                raw_risk = None
                # Prefer evaluate_by_id (TV4 keyed facade) if it returns a valid dict/list
                if hasattr(self.risk_scorer, "evaluate_by_id") and callable(self.risk_scorer.evaluate_by_id):
                    res = self.risk_scorer.evaluate_by_id(candidates)
                    if isinstance(res, (dict, list, tuple)):
                        raw_risk = res

                # Fallback to evaluate_routes (e.g. positional protocol or mock where evaluate_routes is configured)
                if raw_risk is None and hasattr(self.risk_scorer, "evaluate_routes") and callable(self.risk_scorer.evaluate_routes):
                    res = self.risk_scorer.evaluate_routes(candidates)
                    if isinstance(res, (dict, list, tuple)):
                        raw_risk = res

                # Fallback to direct callable
                if raw_risk is None and callable(self.risk_scorer):
                    res = self.risk_scorer(candidates)
                    if isinstance(res, (dict, list, tuple)):
                        raw_risk = res

                if isinstance(raw_risk, dict):
                    returned_keys = set(raw_risk.keys())
                    expected_keys = set(candidate_ids)
                    extra_keys = returned_keys - expected_keys
                    missing_keys = expected_keys - returned_keys

                    if extra_keys:
                        # BUG-04 fix: Detect extra keys contract violation
                        logger.error(
                            "Contract violation: Risk scorer returned extra route IDs not in candidate list: %s",
                            sorted(extra_keys),
                        )
                        assessment_status = RiskAssessmentStatus.INVALID
                    elif missing_keys:
                        # Missing keys -> partial risk
                        logger.warning(
                            "Risk scorer missing scores for route IDs: %s",
                            sorted(missing_keys),
                        )
                        assessment_status = RiskAssessmentStatus.PARTIAL
                    else:
                        assessment_status = RiskAssessmentStatus.AVAILABLE

                    # Validate values are RouteRiskScore instances
                    for c in candidates:
                        score_obj = raw_risk.get(c.id)
                        if score_obj is not None and not isinstance(score_obj, RouteRiskScore):
                            logger.error(
                                "Contract violation: Risk score for %s is not a RouteRiskScore instance (got %s)",
                                c.id,
                                type(score_obj),
                            )
                            assessment_status = RiskAssessmentStatus.INVALID
                            score_obj = None
                        risk_map[c.id] = score_obj

                elif isinstance(raw_risk, (list, tuple)):
                    if len(raw_risk) != len(candidates):
                        logger.warning(
                            "Risk scorer returned %d scores for %d candidates",
                            len(raw_risk),
                            len(candidates),
                        )
                        assessment_status = RiskAssessmentStatus.PARTIAL
                    else:
                        assessment_status = RiskAssessmentStatus.AVAILABLE

                    for idx, c in enumerate(candidates):
                        score_obj = raw_risk[idx] if idx < len(raw_risk) else None
                        if score_obj is not None and not isinstance(score_obj, RouteRiskScore):
                            logger.error(
                                "Contract violation: Risk score at index %d is not a RouteRiskScore instance (got %s)",
                                idx,
                                type(score_obj),
                            )
                            assessment_status = RiskAssessmentStatus.INVALID
                            score_obj = None
                        risk_map[c.id] = score_obj
                else:
                    logger.warning("Risk scorer returned unrecognized type: %s", type(raw_risk))
                    assessment_status = RiskAssessmentStatus.INVALID

            except Exception as exc:  # noqa: BLE001 (Intentional: shield routing response from arbitrary scorer failures)
                logger.error("Risk scorer execution failed: %s. Preserving routes without synthetic risk.", exc)
                assessment_status = RiskAssessmentStatus.UNAVAILABLE
                risk_map = {c.id: None for c in candidates}

        # 3. Build evaluated_routes strictly preserving candidate order, IDs, and count
        evaluated_routes: list[EvaluatedRoute] = []
        for c in candidates:
            evaluated_routes.append(
                EvaluatedRoute(
                    id=c.id,
                    summary=c.summary,
                    distance_meters=c.distance_meters,
                    duration_seconds=c.duration_seconds,
                    polyline=c.polyline,
                    risk=risk_map.get(c.id),
                )
            )

        # Consolidate status based on actual assigned risk objects
        if assessment_status != RiskAssessmentStatus.INVALID:
            scored_count = sum(1 for r in evaluated_routes if r.risk is not None)
            if scored_count == 0:
                assessment_status = RiskAssessmentStatus.UNAVAILABLE
            elif scored_count == len(evaluated_routes):
                assessment_status = RiskAssessmentStatus.AVAILABLE
            else:
                assessment_status = RiskAssessmentStatus.PARTIAL
        else:
            # When assessment_status is INVALID (contract violation):
            # Risk data is untrusted / corrupted.
            # Strictly clear all risk scores so no invalid risk score is exposed as valid!
            for r in evaluated_routes:
                r.risk = None

        # 4. Recommendation Logic (No sentinel 999, explicit availability semantics)
        recommended_route_id: str | None = None
        recommendation_reason: str | None = None
        recommendation_basis: RecommendationBasis | None = None

        if len(evaluated_routes) == 1:
            single = evaluated_routes[0]
            recommended_route_id = single.id
            recommendation_basis = RecommendationBasis.SINGLE_ROUTE
            if assessment_status == RiskAssessmentStatus.INVALID:
                recommendation_reason = (
                    f"Tuyến duy nhất tìm thấy ({single.summary}). "
                    "Lưu ý: Dữ liệu nguy cơ không hợp lệ do lỗi định dạng từ hệ thống đánh giá."
                )
            elif single.risk is not None:
                if single.risk.hazards_count > 0:
                    recommendation_reason = (
                        f"Tuyến duy nhất tìm thấy ({single.summary}). "
                        f"Ghi nhận {single.risk.hazards_count} nguy cơ trên lộ trình."
                    )
                else:
                    recommendation_reason = (
                        f"Tuyến duy nhất tìm thấy ({single.summary}). "
                        "Chưa ghi nhận nguy cơ mặt đường nào trên hệ thống."
                    )
            else:
                # BUG-01 Fix: When risk is None, do NOT say "Chưa ghi nhận nguy cơ"
                recommendation_reason = (
                    f"Tuyến duy nhất tìm thấy ({single.summary}). "
                    "Dữ liệu đánh giá nguy cơ mặt đường hiện chưa khả dụng."
                )

        elif len(evaluated_routes) > 1:
            if assessment_status == RiskAssessmentStatus.AVAILABLE:
                # BUG-03 Fix: All routes have valid risk scores -> compare strictly by risk score
                # Deterministic tie-breaking: (risk.score, duration_seconds, distance_meters, id)
                best_route = min(
                    evaluated_routes,
                    key=lambda r: (
                        r.risk.score,
                        r.duration_seconds,
                        r.distance_meters,
                        r.id,
                    ),
                )
                recommended_route_id = best_route.id
                recommendation_basis = RecommendationBasis.SAFETY_LOWEST_RISK

                mins = max(1, best_route.duration_seconds // 60)
                if best_route.risk.hazards_count == 0 or best_route.risk.score == 0.0:
                    recommendation_reason = (
                        f"Tuyến '{best_route.summary}' được đề xuất vì chưa ghi nhận nguy cơ nào "
                        f"trên hệ thống (thời gian di chuyển: {mins} phút)."
                    )
                else:
                    recommendation_reason = (
                        f"Tuyến '{best_route.summary}' được đề xuất vì có mức nguy cơ đã ghi nhận thấp nhất "
                        f"(điểm nguy cơ: {best_route.risk.score:.1f}, {best_route.risk.hazards_count} nguy cơ)."
                    )

            elif assessment_status == RiskAssessmentStatus.PARTIAL:
                # BUG-02 Fix: Partial risk -> Cannot compare risk across all routes!
                # Recommend shortest duration (ETA), stating caveat clearly
                best_route = min(
                    evaluated_routes,
                    key=lambda r: (r.duration_seconds, r.distance_meters, r.id),
                )
                recommended_route_id = best_route.id
                recommendation_basis = RecommendationBasis.DURATION_ETA_ONLY
                mins = max(1, best_route.duration_seconds // 60)
                recommendation_reason = (
                    f"Tuyến '{best_route.summary}' có thời gian di chuyển ngắn nhất ({mins} phút). "
                    "Lưu ý: Dữ liệu nguy cơ mặt đường chỉ có cho một số tuyến (không đầy đủ), "
                    "hệ thống không thể so sánh mức độ nguy cơ toàn diện."
                )

            else:  # UNAVAILABLE or INVALID
                # BUG-01 / BUG-04 Fix: Scorer unavailable or contract invalid
                best_route = min(
                    evaluated_routes,
                    key=lambda r: (r.duration_seconds, r.distance_meters, r.id),
                )
                recommended_route_id = best_route.id
                recommendation_basis = RecommendationBasis.DURATION_ETA_ONLY
                mins = max(1, best_route.duration_seconds // 60)
                if assessment_status == RiskAssessmentStatus.INVALID:
                    recommendation_reason = (
                        f"Tuyến '{best_route.summary}' có thời gian di chuyển ngắn nhất ({mins} phút). "
                        "Lưu ý: Dữ liệu nguy cơ không hợp lệ do lỗi định dạng từ hệ thống đánh giá."
                    )
                else:
                    recommendation_reason = (
                        f"Tuyến '{best_route.summary}' được đề xuất dựa trên thời gian di chuyển dự kiến ngắn nhất "
                        f"({mins} phút). Dữ liệu đánh giá nguy cơ mặt đường hiện chưa khả dụng."
                    )

        return RouteCompareResponse(
            origin=origin,
            destination=destination,
            travel_mode=travel_mode,
            routes=evaluated_routes,
            recommended_route_id=recommended_route_id,
            recommendation_reason=recommendation_reason,
            disclaimer=SAFE_ROUTE_DISCLAIMER,
            risk_assessment_status=assessment_status.value,
            recommendation_basis=recommendation_basis.value if recommendation_basis else None,
        )
