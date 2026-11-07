"""Comprehensive unit tests for the Routing module (TV5).

Covers:
- Polyline decoding/encoding and GeoJSON [lon, lat] conversion
- Coordinate boundary and identical point validation
- Google Routes API response normalization (2 candidates, 1 candidate, 0 candidates)
- ROUTE01: Single route behavior (no invented alternative)
- Error handling: Auth (401/403), Quota (429), Timeout, Connection error
- RoutingService risk scorer integration, recommendation logic, and disclaimer
"""

import os
from typing import List
from unittest.mock import MagicMock, patch
import httpx
import pytest

from backend.app.modules.routing.adapter import GoogleRoutesAdapter
from backend.app.modules.routing.exceptions import (
    InvalidCoordinatesError,
    NoRouteFoundError,
    RouteServiceAuthError,
    RouteServiceConnectionError,
    RouteServiceQuotaError,
    RouteServiceTimeoutError,
)
from backend.app.modules.routing.fixtures import (
    FIXTURE_DESTINATION,
    FIXTURE_GOOGLE_ROUTES_EMPTY,
    FIXTURE_GOOGLE_ROUTES_SINGLE_CANDIDATE,
    FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES,
    FIXTURE_ORIGIN,
)
from backend.app.modules.routing.polyline import (
    calculate_bounds,
    decode_polyline,
    encode_polyline,
    to_geojson_linestring,
)
from backend.app.modules.routing.schemas import (
    LatLng,
    RouteCandidate,
    RouteCompareResponse,
    RouteRiskScore,
    TravelMode,
)
from backend.app.modules.routing.service import (
    RoutingService,
    SAFE_ROUTE_DISCLAIMER,
)


# ==============================================================================
# 1. Polyline & Geometry Tests
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


# ==============================================================================
# 2. Coordinate Validation Tests
# ==============================================================================

def test_coordinate_validation_out_of_bounds():
    """Verify out-of-bounds coordinates raise InvalidCoordinatesError or ValidationError."""
    with pytest.raises(Exception):
        LatLng(latitude=95.0, longitude=106.0)

    with pytest.raises(Exception):
        LatLng(latitude=10.0, longitude=190.0)


def test_identical_origin_and_destination():
    """Verify adapter rejects identical origin and destination."""
    adapter = GoogleRoutesAdapter(api_key="test_key")
    point = LatLng(latitude=10.7725, longitude=106.6980)
    with pytest.raises(InvalidCoordinatesError):
        adapter.compute_routes(point, point)


# ==============================================================================
# 3. Google Routes Response Parsing & Normalization Tests
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
# 4. Error Handling Tests (Auth, Quota, Timeout, Network)
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
# 5. RoutingService & TV4 Risk Scorer Integration Tests
# ==============================================================================

class MockRiskScorer:
    """Mock TV4 Risk Scorer for testing contract integration."""

    def __init__(self, scores: List[RouteRiskScore]):
        self.scores = scores
        self.last_evaluated_candidates: List[RouteCandidate] = []

    def evaluate_routes(self, candidates: List[RouteCandidate]) -> List[RouteRiskScore]:
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

    # Candidate 1 has 3 hazards (score 4.5); Candidate 2 has 0 hazards (score 0.0)
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
    # Verify candidate 2 is recommended because its risk score is 0.0 vs 4.5
    assert response.recommended_route_id == "route_2"
    assert "chưa ghi nhận nguy cơ nào" in response.recommendation_reason
    assert response.disclaimer == SAFE_ROUTE_DISCLAIMER

    # Verify candidates were properly delivered to TV4 scorer
    assert len(mock_scorer.last_evaluated_candidates) == 2


def test_service_single_candidate_route01():
    """ROUTE01: When Google returns exactly 1 candidate, response contains exactly 1 route."""
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
    """Verify that when TV4 risk scorer is not configured, no synthetic risk or fake hazard is generated."""
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

    # Recommends shortest duration as fallback
    assert response.recommended_route_id == "route_2"
    assert "chờ cập nhật" in response.recommendation_reason

