"""Comprehensive unit tests and regression suite for the Routing module (TV5).

Covers:
- Polyline decoding/encoding and GeoJSON [lon, lat] conversion
- Coordinate boundary and identical point validation
- Google Routes API response normalization (2 candidates, 1 candidate, 0 candidates)
- ROUTE01: Single route behavior (no invented alternative)
- Error handling: Auth (401/403), Quota (429), Timeout, Connection error
- RoutingService risk scorer integration, recommendation logic, and disclaimer
- All 23 original test functions from commit 484721d strictly preserved
- Hardened Regression Tests:
  * BUG-01: Correct recommendation reason when risk is unavailable (never claim 0 hazards if unassessed)
  * BUG-02: Partial risk detection; fallback to ETA without claiming comprehensive risk comparison
  * BUG-03: Elimination of sentinel 999.0; correct ordering for scores = 0, 999, and > 999; tie-breaking
  * BUG-04: Strict keyed contract; extra/missing score keys detected and handled; duplicate route IDs
  * BUG-05: Canonical import root `app.modules.routing` verification
"""

import sys
from pathlib import Path
from unittest.mock import MagicMock

import httpx
import pytest

# Ensure canonical `app` package root is always resolvable in PYTHONPATH
backend_dir = Path(__file__).resolve().parent.parent
if str(backend_dir) not in sys.path:
    sys.path.insert(0, str(backend_dir))

from pydantic import ValidationError

from app.modules.routing.adapter import GoogleRoutesAdapter
from app.modules.routing.exceptions import (
    InvalidCoordinatesError,
    NoRouteFoundError,
    RouteServiceAuthError,
    RouteServiceConnectionError,
    RouteServiceQuotaError,
    RouteServiceTimeoutError,
)
from app.modules.routing.fixtures import (
    FIXTURE_GOOGLE_ROUTES_EMPTY,
    FIXTURE_GOOGLE_ROUTES_SINGLE_CANDIDATE,
    FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES,
)
from app.modules.routing.polyline import (
    calculate_bounds,
    decode_polyline,
    encode_polyline,
    to_geojson_linestring,
)
from app.modules.routing.schemas import (
    LatLng,
    RouteCandidate,
    RouteCompareResponse,
    RouteRiskScore,
    TravelMode,
)
from app.modules.routing.service import (
    SAFE_ROUTE_DISCLAIMER,
    RecommendationBasis,
    RiskAssessmentStatus,
    RoutingService,
)

# ==============================================================================
# 1. Polyline & Geometry Tests (Original 484721d)
# ==============================================================================


def test_polyline_decode_and_encode_roundtrip():
    """Verify roundtrip fidelity of polyline encoding and decoding."""
    coords = [
        (10.77250, 106.69800),
        (10.76800, 106.69200),
        (10.76300, 106.68600),
        (10.75990, 106.68230),
    ]
    encoded = encode_polyline(coords)
    assert isinstance(encoded, str)
    assert len(encoded) > 0

    decoded = decode_polyline(encoded)
    assert len(decoded) == len(coords)
    for orig, dec in zip(coords, decoded):
        assert pytest.approx(orig[0], abs=1e-5) == dec[0]
        assert pytest.approx(orig[1], abs=1e-5) == dec[1]


def test_polyline_empty_handling():
    """Verify empty input handling for polyline functions."""
    assert decode_polyline("") == []
    assert encode_polyline([]) == ""


def test_calculate_bounds():
    """Verify bounding box calculation."""
    coords = [(10.1, 106.2), (10.5, 106.8), (10.3, 106.1)]
    min_lat, min_lng, max_lat, max_lng = calculate_bounds(coords)
    assert min_lat == 10.1
    assert max_lat == 10.5
    assert min_lng == 106.1
    assert max_lng == 106.8

    with pytest.raises(ValueError):
        calculate_bounds([])


def test_to_geojson_linestring_lon_lat_order():
    """CRITICAL: Verify GeoJSON format flips (lat, lon) to [lon, lat] for PostGIS."""
    coords = [(10.7725, 106.6980), (10.7599, 106.6823)]
    geojson = to_geojson_linestring(coords)
    assert geojson["type"] == "LineString"
    assert geojson["coordinates"] == [
        [106.6980, 10.7725],
        [106.6823, 10.7599],
    ]


def test_route_candidate_to_geojson():
    """Verify RouteCandidate.to_geojson() exports [lon, lat] LineString for PostGIS ST_GeomFromGeoJSON."""
    candidate = RouteCandidate(
        id="route_1",
        summary="Test",
        distance_meters=1000,
        duration_seconds=120,
        polyline="dummy",
        coordinates=[(10.7725, 106.6980), (10.7599, 106.6823)],
    )
    geojson = candidate.to_geojson()
    assert geojson["type"] == "LineString"
    assert geojson["coordinates"] == [
        [106.6980, 10.7725],
        [106.6823, 10.7599],
    ]


# ==============================================================================
# 2. Coordinate Validation Tests (Original 484721d)
# ==============================================================================


def test_coordinate_validation_out_of_bounds():
    """Verify out-of-bounds coordinates raise InvalidCoordinatesError or ValidationError."""
    with pytest.raises(ValidationError):
        LatLng(latitude=95.0, longitude=106.0)

    with pytest.raises(ValidationError):
        LatLng(latitude=10.0, longitude=190.0)


def test_identical_origin_and_destination():
    """Verify adapter rejects identical origin and destination."""
    adapter = GoogleRoutesAdapter(api_key="test_key")
    point = LatLng(latitude=10.7725, longitude=106.6980)
    with pytest.raises(InvalidCoordinatesError):
        adapter.compute_routes(point, point)


# ==============================================================================
# 3. Google Routes Response Parsing & Normalization Tests (Original 484721d)
# ==============================================================================


def test_parse_routes_two_candidates():
    """Verify normalization of two candidate routes from Google Routes API."""
    adapter = GoogleRoutesAdapter(api_key="test_key")
    candidates = adapter.parse_routes_response(FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES)

    assert len(candidates) == 2

    c1 = candidates[0]
    assert c1.id == "route_1"
    assert c1.summary == "Đ. Trần Hưng Đạo"
    assert c1.distance_meters == 2950
    assert c1.duration_seconds == 450
    assert len(c1.coordinates) > 0
    assert c1.warnings == []

    c2 = candidates[1]
    assert c2.id == "route_2"
    assert c2.summary == "Đ. Võ Văn Kiệt và Đ. Nguyễn Văn Cừ"
    assert c2.distance_meters == 3480
    assert c2.duration_seconds == 510
    assert len(c2.warnings) == 1


def test_parse_routes_single_candidate_route01():
    """ROUTE01: When Google returns only 1 route, adapter must return exactly 1 candidate.

    Never invent an alternative or fictional route.
    """
    adapter = GoogleRoutesAdapter(api_key="test_key")
    candidates = adapter.parse_routes_response(FIXTURE_GOOGLE_ROUTES_SINGLE_CANDIDATE)

    assert len(candidates) == 1
    assert candidates[0].id == "route_1"
    assert candidates[0].summary == "Đ. Trần Hưng Đạo"
    assert candidates[0].distance_meters == 2950
    assert candidates[0].duration_seconds == 450


def test_parse_routes_empty():
    """Verify empty routes list returns empty candidates."""
    adapter = GoogleRoutesAdapter(api_key="test_key")
    candidates = adapter.parse_routes_response(FIXTURE_GOOGLE_ROUTES_EMPTY)
    assert candidates == []


def test_parse_duration_formats():
    """Verify various duration string formats."""
    assert GoogleRoutesAdapter._parse_duration("123s") == 123
    assert GoogleRoutesAdapter._parse_duration("456.7s") == 457
    assert GoogleRoutesAdapter._parse_duration(300) == 300
    assert GoogleRoutesAdapter._parse_duration("invalid") == 0


# ==============================================================================
# 4. Error Handling Tests (Original 484721d)
# ==============================================================================


def test_compute_routes_missing_api_key():
    """Verify missing API key raises RouteServiceAuthError."""
    adapter = GoogleRoutesAdapter(api_key="")
    with pytest.raises(RouteServiceAuthError) as exc_info:
        adapter.compute_routes(
            LatLng(latitude=10.7725, longitude=106.6980),
            LatLng(latitude=10.7599, longitude=106.6823),
        )
    assert "GOOGLE_ROUTES_API_KEY is not configured" in str(exc_info.value)


def test_compute_routes_auth_error_403():
    """Verify HTTP 403 maps to RouteServiceAuthError."""
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.status_code = 403
    mock_response.text = "The provided API key is invalid."
    mock_client.post.return_value = mock_response

    adapter = GoogleRoutesAdapter(api_key="invalid_key", client=mock_client)
    with pytest.raises(RouteServiceAuthError):
        adapter.compute_routes(
            LatLng(latitude=10.7725, longitude=106.6980),
            LatLng(latitude=10.7599, longitude=106.6823),
        )


def test_compute_routes_quota_error_429():
    """Verify HTTP 429 maps to RouteServiceQuotaError."""
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.status_code = 429
    mock_response.text = "Quota exceeded."
    mock_client.post.return_value = mock_response

    adapter = GoogleRoutesAdapter(api_key="valid_key", client=mock_client)
    with pytest.raises(RouteServiceQuotaError):
        adapter.compute_routes(
            LatLng(latitude=10.7725, longitude=106.6980),
            LatLng(latitude=10.7599, longitude=106.6823),
        )


def test_compute_routes_timeout():
    """Verify timeout maps to RouteServiceTimeoutError."""
    mock_client = MagicMock()
    mock_client.post.side_effect = httpx.TimeoutException("Read timed out")

    adapter = GoogleRoutesAdapter(api_key="valid_key", client=mock_client)
    with pytest.raises(RouteServiceTimeoutError):
        adapter.compute_routes(
            LatLng(latitude=10.7725, longitude=106.6980),
            LatLng(latitude=10.7599, longitude=106.6823),
        )


def test_compute_routes_connection_error():
    """Verify network connection failure maps to RouteServiceConnectionError."""
    mock_client = MagicMock()
    mock_client.post.side_effect = httpx.NetworkError("Failed to resolve host")

    adapter = GoogleRoutesAdapter(api_key="valid_key", client=mock_client)
    with pytest.raises(RouteServiceConnectionError):
        adapter.compute_routes(
            LatLng(latitude=10.7725, longitude=106.6980),
            LatLng(latitude=10.7599, longitude=106.6823),
        )


def test_compute_routes_no_route_found_raises_404():
    """Verify 0 candidates from Google raises NoRouteFoundError."""
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.status_code = 200
    mock_response.json.return_value = {"routes": []}
    mock_client.post.return_value = mock_response

    adapter = GoogleRoutesAdapter(api_key="valid_key", client=mock_client)
    with pytest.raises(NoRouteFoundError):
        adapter.compute_routes(
            LatLng(latitude=10.7725, longitude=106.6980),
            LatLng(latitude=10.7599, longitude=106.6823),
        )


# ==============================================================================
# 5. RoutingService Integration Tests (Original 484721d Preserved)
# ==============================================================================


class MockRiskScorer:
    """Mock TV4 Risk Scorer for testing contract integration."""

    def __init__(self, scores: list[RouteRiskScore]):
        self.scores = scores
        self.last_evaluated_candidates: list[RouteCandidate] = []

    def evaluate_routes(self, candidates: list[RouteCandidate]) -> list[RouteRiskScore]:
        self.last_evaluated_candidates = candidates
        return self.scores[: len(candidates)]


def test_service_compare_routes_with_risk_scorer():
    """Verify RoutingService passes candidates to TV4 scorer and recommends route with lowest risk."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1",
            summary="Đ. Trần Hưng Đạo",
            distance_meters=2950,
            duration_seconds=450,
            polyline="c_w`AomfjSb[nd@f^nd@jRbV",
            coordinates=[(10.7725, 106.6980), (10.7599, 106.6823)],
        ),
        RouteCandidate(
            id="route_2",
            summary="Đ. Võ Văn Kiệt",
            distance_meters=3480,
            duration_seconds=510,
            polyline="c_w`AomfjSzT_Xvj@vj@vj@~p@s]j\\",
            coordinates=[(10.7725, 106.6980), (10.7599, 106.6823)],
        ),
    ]

    mock_scorer = MockRiskScorer(
        [
            RouteRiskScore(
                score=4.5,
                level="MEDIUM",
                hazards_count=3,
                verified_hazards_count=2,
                pending_hazards_count=1,
                reason="Ghi nhận 2 ổ gà đã xác minh và 1 gồ ghề chờ xác minh gần tuyến",
            ),
            RouteRiskScore(
                score=0.0,
                level="LOW",
                hazards_count=0,
                verified_hazards_count=0,
                pending_hazards_count=0,
                reason="Không có nguy cơ hư hỏng mặt đường nào được ghi nhận",
            ),
        ]
    )

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
        travel_mode=TravelMode.TWO_WHEELER,
    )

    assert isinstance(response, RouteCompareResponse)
    assert len(response.routes) == 2
    assert response.recommended_route_id == "route_2"
    assert "chưa ghi nhận nguy cơ nào" in response.recommendation_reason
    assert response.disclaimer == SAFE_ROUTE_DISCLAIMER
    assert response.risk_assessment_status == RiskAssessmentStatus.AVAILABLE.value
    assert response.recommendation_basis == RecommendationBasis.SAFETY_LOWEST_RISK.value


def test_service_single_candidate_route01():
    """Original 484721d: When Google returns exactly 1 candidate, response contains exactly 1 route."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1",
            summary="Đ. Trần Hưng Đạo",
            distance_meters=2950,
            duration_seconds=450,
            polyline="c_w`AomfjSb[nd@f^nd@jRbV",
            coordinates=[(10.7725, 106.6980), (10.7599, 106.6823)],
        )
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=None)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 1
    assert response.recommended_route_id == "route_1"
    assert "Tuyến duy nhất" in response.recommendation_reason
    assert response.disclaimer == SAFE_ROUTE_DISCLAIMER


def test_service_without_risk_scorer_no_synthetic_hazards():
    """Original 484721d: When TV4 risk scorer is not configured, no synthetic risk or fake hazard is generated."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1",
            summary="Tuyến 1",
            distance_meters=3000,
            duration_seconds=500,
            polyline="c_w`AomfjSb[nd@f^nd@jRbV",
        ),
        RouteCandidate(
            id="route_2",
            summary="Tuyến 2",
            distance_meters=2800,
            duration_seconds=450,
            polyline="c_w`AomfjSzT_Xvj@vj@vj@~p@s]j\\",
        ),
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=None)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    for r in response.routes:
        assert r.risk is None  # Never invent risk!

    assert response.recommended_route_id == "route_2"
    assert "chưa khả dụng" in response.recommendation_reason


def test_service_risk_scorer_mismatched_length_never_drops_routes():
    """Original 484721d: When Risk Scorer returns fewer items than candidates (e.g. 1 score for 2 routes),

    NO route is silently dropped! Both candidate routes, their IDs and order must be 100% preserved.
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1",
            summary="Tuyến 1",
            distance_meters=3000,
            duration_seconds=500,
            polyline="c_w`AomfjSb[nd@f^nd@jRbV",
        ),
        RouteCandidate(
            id="route_2",
            summary="Tuyến 2",
            distance_meters=2800,
            duration_seconds=450,
            polyline="c_w`AomfjSzT_Xvj@vj@vj@~p@s]j\\",
        ),
    ]

    mock_scorer = MagicMock()
    mock_scorer.evaluate_routes.return_value = [
        RouteRiskScore(
            score=1.5,
            level="LOW",
            hazards_count=1,
            verified_hazards_count=1,
            pending_hazards_count=0,
            reason="1 ổ gà đã xác minh",
        )
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    assert response.routes[0].id == "route_1"
    assert response.routes[0].risk is not None
    assert response.routes[0].risk.score == 1.5

    assert response.routes[1].id == "route_2"
    assert response.routes[1].risk is None


def test_service_risk_scorer_dict_return_type():
    """Original 484721d: Verify Risk Scorer can return a dictionary keyed by candidate ID."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1",
            summary="Tuyến 1",
            distance_meters=3000,
            duration_seconds=500,
            polyline="c_w`AomfjSb[nd@f^nd@jRbV",
        ),
        RouteCandidate(
            id="route_2",
            summary="Tuyến 2",
            distance_meters=2800,
            duration_seconds=450,
            polyline="c_w`AomfjSzT_Xvj@vj@vj@~p@s]j\\",
        ),
    ]

    mock_scorer = MagicMock()
    mock_scorer.evaluate_by_id.return_value = {
        "route_2": RouteRiskScore(
            score=0.5,
            level="LOW",
            hazards_count=1,
            verified_hazards_count=0,
            pending_hazards_count=1,
            reason="1 nguy cơ chờ xác minh",
        ),
        "route_1": RouteRiskScore(
            score=2.5,
            level="MEDIUM",
            hazards_count=2,
            verified_hazards_count=1,
            pending_hazards_count=1,
            reason="2 nguy cơ",
        ),
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    # Verify exact keyed mapping without misassignment:
    assert response.routes[0].id == "route_1"
    assert response.routes[0].risk.score == 2.5
    assert response.routes[1].id == "route_2"
    assert response.routes[1].risk.score == 0.5


def test_service_risk_scorer_exception_handling():
    """Original 484721d: Verify that if the Risk Scorer raises an exception, routes are preserved safely."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1",
            summary="Tuyến 1",
            distance_meters=3000,
            duration_seconds=500,
            polyline="c_w`AomfjSb[nd@f^nd@jRbV",
        )
    ]

    mock_scorer = MagicMock()
    mock_scorer.evaluate_routes.side_effect = RuntimeError("PostGIS connection failed")

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 1
    assert response.routes[0].id == "route_1"
    assert response.routes[0].risk is None


# ==============================================================================
# 6. Additional Hardened Regression Tests (BUG-01 through BUG-05)
# ==============================================================================


def test_regression_bug01_single_route_risk_unavailable():
    """BUG-01: When risk is unavailable on a single route, never falsely claim 0 hazards."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1", summary="Đ. Trần Hưng Đạo", distance_meters=2950, duration_seconds=450, polyline="dummy"
        )
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=None)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 1
    assert response.recommended_route_id == "route_1"
    assert "Tuyến duy nhất" in response.recommendation_reason
    assert "chưa khả dụng" in response.recommendation_reason
    assert "Chưa ghi nhận nguy cơ mặt đường nào trên hệ thống" not in response.recommendation_reason
    assert response.risk_assessment_status == RiskAssessmentStatus.UNAVAILABLE.value
    assert response.recommendation_basis == RecommendationBasis.SINGLE_ROUTE.value


def test_regression_bug01_single_route_risk_zero_hazards():
    """BUG-01 Contrast: When risk is assessed and genuinely has 0 hazards, state zero hazards."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1", summary="Đ. Trần Hưng Đạo", distance_meters=2950, duration_seconds=450, polyline="dummy"
        )
    ]
    mock_scorer = MagicMock()
    mock_scorer.evaluate_by_id.return_value = {
        "route_1": RouteRiskScore(
            score=0.0,
            level="LOW",
            hazards_count=0,
            verified_hazards_count=0,
            pending_hazards_count=0,
            reason="Không có nguy cơ",
        )
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 1
    assert response.routes[0].risk is not None
    assert response.routes[0].risk.score == 0.0
    assert "Chưa ghi nhận nguy cơ mặt đường nào trên hệ thống" in response.recommendation_reason
    assert response.risk_assessment_status == RiskAssessmentStatus.AVAILABLE.value


def test_regression_bug01_multiple_routes_risk_unavailable():
    """BUG-01: When risk is unavailable for multiple routes, recommend by ETA with clear caveat."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_1", summary="Tuyến 1", distance_meters=3000, duration_seconds=500, polyline="p1"),
        RouteCandidate(id="route_2", summary="Tuyến 2", distance_meters=2800, duration_seconds=400, polyline="p2"),
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=None)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    assert response.recommended_route_id == "route_2"
    assert response.recommendation_basis == RecommendationBasis.DURATION_ETA_ONLY.value
    assert response.risk_assessment_status == RiskAssessmentStatus.UNAVAILABLE.value
    assert "chưa khả dụng" in response.recommendation_reason


def test_regression_bug02_partial_risk_never_claims_lowest_risk():
    """BUG-02: When some routes have risk scores and others do not (partial risk),

    the service MUST NOT declare any route as 'lowest risk'. It falls back to ETA
    and explicitly warns the user that risk data is incomplete.
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1", summary="Tuyến Có Điểm Risk", distance_meters=3200, duration_seconds=500, polyline="p1"
        ),
        RouteCandidate(
            id="route_2", summary="Tuyến Thiếu Điểm Risk", distance_meters=2800, duration_seconds=420, polyline="p2"
        ),
    ]

    mock_scorer = MagicMock()
    mock_scorer.evaluate_by_id.return_value = {
        "route_1": RouteRiskScore(
            score=2.0,
            level="MEDIUM",
            hazards_count=2,
            verified_hazards_count=1,
            pending_hazards_count=1,
            reason="2 nguy cơ",
        )
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    assert response.risk_assessment_status == RiskAssessmentStatus.PARTIAL.value
    assert response.recommendation_basis == RecommendationBasis.DURATION_ETA_ONLY.value
    assert "mức nguy cơ đã ghi nhận thấp nhất" not in response.recommendation_reason
    assert "không đầy đủ" in response.recommendation_reason
    assert response.recommended_route_id == "route_2"


def test_regression_bug03_no_sentinel_999_with_scores_above_999():
    """BUG-03: Eliminate magic number 999.0.

    Verify correct ordering when real risk scores are 0.0, 999.0, and 1500.0 (greater than 999).
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_high", summary="High Risk", distance_meters=3000, duration_seconds=500, polyline="p1"),
        RouteCandidate(
            id="route_999", summary="Exact 999 Risk", distance_meters=3000, duration_seconds=500, polyline="p2"
        ),
        RouteCandidate(id="route_zero", summary="Zero Risk", distance_meters=3000, duration_seconds=500, polyline="p3"),
    ]

    mock_scorer = MagicMock()
    mock_scorer.evaluate_by_id.return_value = {
        "route_high": RouteRiskScore(
            score=1500.0,
            level="HIGH",
            hazards_count=100,
            verified_hazards_count=100,
            pending_hazards_count=0,
            reason="Cực nguy hiểm",
        ),
        "route_999": RouteRiskScore(
            score=999.0,
            level="HIGH",
            hazards_count=50,
            verified_hazards_count=50,
            pending_hazards_count=0,
            reason="999 điểm",
        ),
        "route_zero": RouteRiskScore(
            score=0.0, level="LOW", hazards_count=0, verified_hazards_count=0, pending_hazards_count=0, reason="0 điểm"
        ),
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert response.recommended_route_id == "route_zero"
    assert response.risk_assessment_status == RiskAssessmentStatus.AVAILABLE.value

    # route_999 vs route_high (999.0 vs 1500.0)
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_high", summary="High Risk", distance_meters=3000, duration_seconds=500, polyline="p1"),
        RouteCandidate(
            id="route_999", summary="Exact 999 Risk", distance_meters=3000, duration_seconds=500, polyline="p2"
        ),
    ]
    response2 = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )
    assert response2.recommended_route_id == "route_999"


def test_regression_bug03_deterministic_tie_breaking():
    """BUG-03: Two routes with identical risk score tie-break deterministically:

    1. Duration -> 2. Distance -> 3. Route ID.
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_1", summary="Tuyến 1", distance_meters=3000, duration_seconds=600, polyline="p1"),
        RouteCandidate(id="route_2", summary="Tuyến 2", distance_meters=3200, duration_seconds=500, polyline="p2"),
    ]

    mock_scorer = MagicMock()
    mock_scorer.evaluate_by_id.return_value = {
        "route_1": RouteRiskScore(
            score=3.0, level="MEDIUM", hazards_count=2, verified_hazards_count=2, pending_hazards_count=0, reason="3.0"
        ),
        "route_2": RouteRiskScore(
            score=3.0, level="MEDIUM", hazards_count=2, verified_hazards_count=2, pending_hazards_count=0, reason="3.0"
        ),
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert response.recommended_route_id == "route_2"


def test_regression_bug04_extra_risk_keys_detected():
    """BUG-04: Risk Scorer returns dict with extra unknown route IDs not in candidate list.

    Service must detect contract violation, set status INVALID, log error, and not leak extra keys.
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_1", summary="Tuyến 1", distance_meters=3000, duration_seconds=500, polyline="p1"),
        RouteCandidate(id="route_2", summary="Tuyến 2", distance_meters=2800, duration_seconds=450, polyline="p2"),
    ]

    mock_scorer = MagicMock()
    mock_scorer.evaluate_by_id.return_value = {
        "route_1": RouteRiskScore(
            score=1.0, level="LOW", hazards_count=1, verified_hazards_count=1, pending_hazards_count=0, reason="1"
        ),
        "route_2": RouteRiskScore(
            score=2.0, level="MEDIUM", hazards_count=2, verified_hazards_count=2, pending_hazards_count=0, reason="2"
        ),
        "route_UNKNOWN_EXTRA": RouteRiskScore(
            score=0.0, level="LOW", hazards_count=0, verified_hazards_count=0, pending_hazards_count=0, reason="Ghost"
        ),
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    assert [r.id for r in response.routes] == ["route_1", "route_2"]
    # All routes must have risk=None because invalid contract data cannot be trusted
    assert all(r.risk is None for r in response.routes)
    assert response.risk_assessment_status == RiskAssessmentStatus.INVALID.value
    assert response.recommendation_basis == RecommendationBasis.DURATION_ETA_ONLY.value
    assert "không hợp lệ do lỗi định dạng" in response.recommendation_reason


def test_regression_invalid_no_matching_candidate_ids():
    """Verify INVALID state when scorer returns ONLY unknown route IDs (0 valid scores).

    Ensures scored_count==0 does NOT overwrite INVALID to UNAVAILABLE.
    All routes have risk=None and recommendation falls back to ETA.
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_1", summary="Tuyến 1", distance_meters=3000, duration_seconds=500, polyline="p1"),
        RouteCandidate(id="route_2", summary="Tuyến 2", distance_meters=2800, duration_seconds=450, polyline="p2"),
    ]

    mock_scorer = MagicMock()
    # Scorer returns only a ghost route ID, none of the candidate IDs exist
    mock_scorer.evaluate_by_id.return_value = {
        "route_GHOST_1": RouteRiskScore(
            score=0.0, level="LOW", hazards_count=0, verified_hazards_count=0, pending_hazards_count=0, reason="Ghost"
        ),
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    assert [r.id for r in response.routes] == ["route_1", "route_2"]
    assert all(r.risk is None for r in response.routes)
    # MUST stay INVALID, never overwritten to UNAVAILABLE
    assert response.risk_assessment_status == RiskAssessmentStatus.INVALID.value
    assert response.recommendation_basis == RecommendationBasis.DURATION_ETA_ONLY.value
    assert response.recommended_route_id == "route_2"  # shortest duration (450s vs 500s)
    assert "không hợp lệ do lỗi định dạng" in response.recommendation_reason


def test_regression_invalid_single_candidate_route():
    """Verify INVALID state on a single route when scorer returns foreign route IDs.

    Status must be INVALID, route.risk must be None, and reason must explicitly state format error.
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1", summary="Tuyến Duy Nhất", distance_meters=2000, duration_seconds=300, polyline="p1"
        ),
    ]

    mock_scorer = MagicMock()
    mock_scorer.evaluate_by_id.return_value = {
        "route_FOREIGN": RouteRiskScore(
            score=1.0, level="LOW", hazards_count=1, verified_hazards_count=1, pending_hazards_count=0, reason="Foreign"
        ),
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 1
    assert response.routes[0].id == "route_1"
    assert response.routes[0].risk is None
    assert response.risk_assessment_status == RiskAssessmentStatus.INVALID.value
    assert response.recommendation_basis == RecommendationBasis.SINGLE_ROUTE.value
    assert "Tuyến duy nhất" in response.recommendation_reason
    assert "không hợp lệ do lỗi định dạng" in response.recommendation_reason


def test_regression_invalid_score_value_type():
    """Verify INVALID state when scorer returns dict with invalid value types (not RouteRiskScore)."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_1", summary="Tuyến 1", distance_meters=3000, duration_seconds=500, polyline="p1"),
        RouteCandidate(id="route_2", summary="Tuyến 2", distance_meters=2800, duration_seconds=450, polyline="p2"),
    ]

    mock_scorer = MagicMock()
    # String instead of RouteRiskScore object
    mock_scorer.evaluate_by_id.return_value = {
        "route_1": "INVALID_SCORE_STRING",
        "route_2": 12345,
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    assert all(r.risk is None for r in response.routes)
    assert response.risk_assessment_status == RiskAssessmentStatus.INVALID.value
    assert response.recommendation_basis == RecommendationBasis.DURATION_ETA_ONLY.value
    assert "không hợp lệ do lỗi định dạng" in response.recommendation_reason


def test_regression_bug04_duplicate_candidate_ids_raises_value_error():
    """BUG-04: Reject duplicate candidate IDs to prevent key collision and undefined mapping."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1", summary="Tuyến Trùng 1", distance_meters=3000, duration_seconds=500, polyline="p1"
        ),
        RouteCandidate(
            id="route_1", summary="Tuyến Trùng 2", distance_meters=2800, duration_seconds=450, polyline="p2"
        ),
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=None)
    with pytest.raises(ValueError) as excinfo:
        service.compare_routes(
            origin=LatLng(latitude=10.7725, longitude=106.6980),
            destination=LatLng(latitude=10.7599, longitude=106.6823),
        )
    assert "Duplicate candidate route IDs detected" in str(excinfo.value)


def test_regression_canonical_import_root():
    """BUG-05: Verify canonical import root `app.modules.routing` loads cleanly without alias hacks."""
    import app.modules.routing as routing_pkg
    from app.modules.routing.schemas import RouteCandidate, RouteRiskScore

    assert routing_pkg.RouteCandidate is RouteCandidate
    assert routing_pkg.RouteRiskScore is RouteRiskScore


def test_regression_tv4_keyed_facade_order_independence():
    """Verify that TV4 evaluate_by_id keyed dict maps strictly by route ID regardless of dict iteration order."""
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_alpha", summary="Alpha", distance_meters=1000, duration_seconds=100, polyline="p1"),
        RouteCandidate(id="route_beta", summary="Beta", distance_meters=2000, duration_seconds=200, polyline="p2"),
    ]

    # Return dict in reverse order
    mock_scorer = MagicMock()
    del mock_scorer.evaluate_routes
    mock_scorer.evaluate_by_id.return_value = {
        "route_beta": RouteRiskScore(
            score=7.0,
            level="HIGH",
            hazards_count=5,
            verified_hazards_count=5,
            pending_hazards_count=0,
            reason="Beta risk",
        ),
        "route_alpha": RouteRiskScore(
            score=1.0,
            level="LOW",
            hazards_count=1,
            verified_hazards_count=1,
            pending_hazards_count=0,
            reason="Alpha risk",
        ),
    }

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert response.routes[0].id == "route_alpha"
    assert response.routes[0].risk.score == 1.0  # Correctly mapped to alpha
    assert response.routes[1].id == "route_beta"
    assert response.routes[1].risk.score == 7.0  # Correctly mapped to beta
    assert response.recommended_route_id == "route_alpha"


# ==============================================================================
# 7. Regression — List cardinality mismatch & Zero-score-with-hazards (TV5)
# ==============================================================================


def test_regression_list_scorer_extra_items_marked_invalid():
    """List scorer returns 3 scores for 2 candidates → INVALID (not AVAILABLE/PARTIAL).

    Contract rule: extra scores are a contract violation — the scorer claims knowledge of
    routes we did not send.  Service must mark INVALID, preserve both original candidates,
    set risk=None for all, and fall back to DURATION_ETA_ONLY recommendation.

    Uses evaluate_routes (list interface) since service checks evaluate_by_id first,
    then evaluate_routes, then callable — delete evaluate_by_id to force evaluate_routes path.
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_A", summary="Đường A", distance_meters=3000, duration_seconds=600, polyline="pA"),
        RouteCandidate(id="route_B", summary="Đường B", distance_meters=4000, duration_seconds=500, polyline="pB"),
    ]

    mock_scorer = MagicMock()
    # Remove evaluate_by_id to force evaluate_routes path (list interface)
    del mock_scorer.evaluate_by_id
    # evaluate_routes returns 3 items for 2 candidates → contract violation
    mock_scorer.evaluate_routes.return_value = [
        RouteRiskScore(
            score=1.0, level="LOW", hazards_count=1, verified_hazards_count=1, pending_hazards_count=0, reason="A"
        ),
        RouteRiskScore(
            score=2.0, level="LOW", hazards_count=2, verified_hazards_count=2, pending_hazards_count=0, reason="B"
        ),
        RouteRiskScore(
            score=3.0,
            level="MEDIUM",
            hazards_count=3,
            verified_hazards_count=3,
            pending_hazards_count=0,
            reason="EXTRA",
        ),
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    # Must preserve both original candidates
    assert len(response.routes) == 2
    assert response.routes[0].id == "route_A"
    assert response.routes[1].id == "route_B"
    # All risk must be None — extra data is untrustworthy
    assert all(r.risk is None for r in response.routes), "Extra scores must NOT be used; all risk=None"
    # Must be INVALID, not AVAILABLE or PARTIAL
    assert response.risk_assessment_status == RiskAssessmentStatus.INVALID.value
    # Fallback to ETA-only since risk data is unreliable
    assert response.recommendation_basis == RecommendationBasis.DURATION_ETA_ONLY.value
    # Should NOT claim lowest risk
    assert "thấp nhất" not in response.recommendation_reason


def test_regression_list_scorer_exact_count_marked_available():
    """List scorer returns exactly 2 scores for 2 candidates → AVAILABLE.

    Verifies the boundary condition: exact match in list length is valid (not INVALID/PARTIAL).
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(id="route_A", summary="Đường A", distance_meters=3000, duration_seconds=600, polyline="pA"),
        RouteCandidate(id="route_B", summary="Đường B", distance_meters=4000, duration_seconds=500, polyline="pB"),
    ]

    mock_scorer = MagicMock()
    # Remove evaluate_by_id to force evaluate_routes path (list interface)
    del mock_scorer.evaluate_by_id
    mock_scorer.evaluate_routes.return_value = [
        RouteRiskScore(
            score=1.0, level="LOW", hazards_count=1, verified_hazards_count=1, pending_hazards_count=0, reason="A risk"
        ),
        RouteRiskScore(
            score=5.0, level="HIGH", hazards_count=5, verified_hazards_count=5, pending_hazards_count=0, reason="B risk"
        ),
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    assert response.risk_assessment_status == RiskAssessmentStatus.AVAILABLE.value
    assert response.recommendation_basis == RecommendationBasis.SAFETY_LOWEST_RISK.value
    # route_A has lower risk → should be recommended
    assert response.recommended_route_id == "route_A"


def test_regression_zero_score_with_pending_hazards_single_route():
    """score=0.0 with hazards_count>0 (pending excluded by policy) → must NOT say 'Chưa ghi nhận nguy cơ'.

    Business rule: score=0 ≠ no hazards.  When pending_multiplier=0 is applied,
    pending reports bring score to 0 but reports still exist.  Service must clearly state
    'score is zero per current policy' and reference the hazard count.
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_1", summary="Tuyến Duy Nhất", distance_meters=2000, duration_seconds=400, polyline="p1"
        ),
    ]

    mock_scorer = MagicMock()
    del mock_scorer.evaluate_by_id
    # score=0 but hazards present (pending multiplier = 0 excluded them)
    mock_scorer.evaluate_routes.return_value = [
        RouteRiskScore(
            score=0.0,
            level="LOW",
            hazards_count=3,
            verified_hazards_count=0,
            pending_hazards_count=3,
            reason="All pending",
        ),
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 1
    assert response.risk_assessment_status == RiskAssessmentStatus.AVAILABLE.value
    # Must NOT claim zero hazards when there are actual reports
    assert "Chưa ghi nhận nguy cơ mặt đường nào" not in response.recommendation_reason, (
        "Must not claim 'no hazards recorded' when hazards_count > 0"
    )
    # Must acknowledge score=0 is a policy result, not absence of data
    assert "bằng 0 theo chính sách" in response.recommendation_reason, (
        "Must explain that score=0 is due to current scoring policy, not absence of hazards"
    )
    # Must reference hazard count so the user understands reports exist
    assert "3" in response.recommendation_reason or "báo cáo" in response.recommendation_reason


def test_regression_zero_score_with_pending_hazards_multiple_routes():
    """Two routes both score=0.0: one truly has no hazards, one has pending-only hazards.

    Service must correctly distinguish:
    - route_A (score=0, hazards=0): "Chưa ghi nhận nguy cơ"
    - route_B (score=0, hazards=5): "bằng 0 theo chính sách"

    Tie-break by duration → route_B is faster (300s vs 400s);
    but route_A also has score=0, so the recommended route is determined
    by policy (both tied at score=0 → shorter duration wins → route_B recommended).
    Assert the recommendation reason for route_B does NOT claim zero hazards.
    """
    mock_adapter = MagicMock()
    mock_adapter.compute_routes.return_value = [
        RouteCandidate(
            id="route_A", summary="Tuyến A - Không nguy cơ", distance_meters=3000, duration_seconds=400, polyline="pA"
        ),
        RouteCandidate(
            id="route_B", summary="Tuyến B - Pending only", distance_meters=2500, duration_seconds=300, polyline="pB"
        ),
    ]

    mock_scorer = MagicMock()
    del mock_scorer.evaluate_by_id
    mock_scorer.evaluate_routes.return_value = [
        RouteRiskScore(
            score=0.0,
            level="LOW",
            hazards_count=0,
            verified_hazards_count=0,
            pending_hazards_count=0,
            reason="A no hazard",
        ),
        RouteRiskScore(
            score=0.0,
            level="LOW",
            hazards_count=5,
            verified_hazards_count=0,
            pending_hazards_count=5,
            reason="B all pending",
        ),
    ]

    service = RoutingService(adapter=mock_adapter, risk_scorer=mock_scorer)
    response = service.compare_routes(
        origin=LatLng(latitude=10.7725, longitude=106.6980),
        destination=LatLng(latitude=10.7599, longitude=106.6823),
    )

    assert len(response.routes) == 2
    assert response.risk_assessment_status == RiskAssessmentStatus.AVAILABLE.value

    # Find route_A and route_B in response
    route_map = {r.id: r for r in response.routes}
    assert "route_A" in route_map
    assert "route_B" in route_map

    # route_A truly has no hazards; its risk.hazards_count must be 0
    assert route_map["route_A"].risk.score == 0.0
    assert route_map["route_A"].risk.hazards_count == 0

    # route_B has pending hazards; score=0 by policy
    assert route_map["route_B"].risk.score == 0.0
    assert route_map["route_B"].risk.hazards_count == 5

    # Recommended route is route_B (tied on score=0, shorter duration=300s wins)
    assert response.recommended_route_id == "route_B"

    # The recommendation reason must NOT claim 'no hazards' for route_B
    # (route_B has 5 pending reports; score=0 is due to pending multiplier policy)
    reason = response.recommendation_reason
    assert "Chưa ghi nhận nguy cơ mặt đường nào" not in reason, (
        "route_B has 5 pending hazards — must not claim no hazards recorded"
    )
    assert "bằng 0 theo chính sách" in reason, "Must explain score=0 is due to scoring policy, not absence of hazards"


def test_eta_rounds_up_and_disclaimer_does_not_claim_risk_comparison():
    adapter = MagicMock()
    adapter.compute_routes.return_value = [
        RouteCandidate(id="r1", summary="A", duration_seconds=361, distance_meters=2000, polyline="abc"),
        RouteCandidate(id="r2", summary="B", duration_seconds=500, distance_meters=2500, polyline="def"),
    ]
    response = RoutingService(adapter=adapter, risk_scorer=None).compare_routes(
        origin=LatLng(latitude=10.75, longitude=106.67),
        destination=LatLng(latitude=10.76, longitude=106.68),
    )
    assert "7 ph\u00fat" in response.recommendation_reason
    assert "6 ph\u00fat" not in response.recommendation_reason
    assert "d\u1ef1a tr\u00ean c\u00e1c nguy c\u01a1" not in response.disclaimer
