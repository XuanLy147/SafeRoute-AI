"""Routing Module (TV5 - SafeRoute AI).

Provides Google Routes API v2 integration, polyline decoding, candidate normalization,
and route risk comparison orchestration.
"""

from backend.app.modules.routing.adapter import GoogleRoutesAdapter
from backend.app.modules.routing.exceptions import (
    InvalidCoordinatesError,
    NoRouteFoundError,
    RouteServiceAuthError,
    RouteServiceConnectionError,
    RouteServiceError,
    RouteServiceQuotaError,
    RouteServiceTimeoutError,
    RoutingError,
)
from backend.app.modules.routing.fixtures import (
    FIXTURE_DESTINATION,
    FIXTURE_GOOGLE_ROUTES_AUTH_ERROR,
    FIXTURE_GOOGLE_ROUTES_EMPTY,
    FIXTURE_GOOGLE_ROUTES_QUOTA_ERROR,
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
    EvaluatedRoute,
    LatLng,
    RouteCandidate,
    RouteCompareRequest,
    RouteCompareResponse,
    RouteRiskScore,
    TravelMode,
)
from backend.app.modules.routing.service import (
    RiskScorerProtocol,
    RoutingService,
    SAFE_ROUTE_DISCLAIMER,
)

__all__ = [
    # Schemas
    "LatLng",
    "RouteCandidate",
    "RouteRiskScore",
    "EvaluatedRoute",
    "RouteCompareRequest",
    "RouteCompareResponse",
    "TravelMode",
    "SAFE_ROUTE_DISCLAIMER",
    # Service & Adapter
    "GoogleRoutesAdapter",
    "RoutingService",
    "RiskScorerProtocol",
    # Polyline utilities
    "decode_polyline",
    "encode_polyline",
    "calculate_bounds",
    "to_geojson_linestring",
    # Exceptions
    "RoutingError",
    "InvalidCoordinatesError",
    "NoRouteFoundError",
    "RouteServiceAuthError",
    "RouteServiceQuotaError",
    "RouteServiceTimeoutError",
    "RouteServiceConnectionError",
    "RouteServiceError",
    # Fixtures
    "FIXTURE_ORIGIN",
    "FIXTURE_DESTINATION",
    "FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES",
    "FIXTURE_GOOGLE_ROUTES_SINGLE_CANDIDATE",
    "FIXTURE_GOOGLE_ROUTES_EMPTY",
    "FIXTURE_GOOGLE_ROUTES_AUTH_ERROR",
    "FIXTURE_GOOGLE_ROUTES_QUOTA_ERROR",
]

