"""Routing Module (TV5 - SafeRoute AI).

Provides Google Routes API v2 integration, polyline decoding, candidate normalization,
and route risk comparison orchestration.
"""

from .adapter import GoogleRoutesAdapter
from .exceptions import (
    InvalidCoordinatesError,
    NoRouteFoundError,
    RouteServiceAuthError,
    RouteServiceConnectionError,
    RouteServiceError,
    RouteServiceQuotaError,
    RouteServiceTimeoutError,
    RoutingError,
)
from .fixtures import (
    FIXTURE_DESTINATION,
    FIXTURE_GOOGLE_ROUTES_AUTH_ERROR,
    FIXTURE_GOOGLE_ROUTES_EMPTY,
    FIXTURE_GOOGLE_ROUTES_QUOTA_ERROR,
    FIXTURE_GOOGLE_ROUTES_SINGLE_CANDIDATE,
    FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES,
    FIXTURE_ORIGIN,
)
from .polyline import (
    calculate_bounds,
    decode_polyline,
    encode_polyline,
    to_geojson_linestring,
)
from .schemas import (
    EvaluatedRoute,
    LatLng,
    RouteCandidate,
    RouteCompareRequest,
    RouteCompareResponse,
    RouteRiskScore,
    TravelMode,
)
from .service import (
    SAFE_ROUTE_DISCLAIMER,
    RecommendationBasis,
    RiskAssessmentStatus,
    RiskScorerProtocol,
    RoutingService,
)

__all__ = [
    "FIXTURE_DESTINATION",
    "FIXTURE_GOOGLE_ROUTES_AUTH_ERROR",
    "FIXTURE_GOOGLE_ROUTES_EMPTY",
    "FIXTURE_GOOGLE_ROUTES_QUOTA_ERROR",
    "FIXTURE_GOOGLE_ROUTES_SINGLE_CANDIDATE",
    "FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES",
    "FIXTURE_ORIGIN",
    "SAFE_ROUTE_DISCLAIMER",
    "EvaluatedRoute",
    "GoogleRoutesAdapter",
    "InvalidCoordinatesError",
    "LatLng",
    "NoRouteFoundError",
    "RecommendationBasis",
    "RiskAssessmentStatus",
    "RiskScorerProtocol",
    "RouteCandidate",
    "RouteCompareRequest",
    "RouteCompareResponse",
    "RouteRiskScore",
    "RouteServiceAuthError",
    "RouteServiceConnectionError",
    "RouteServiceError",
    "RouteServiceQuotaError",
    "RouteServiceTimeoutError",
    "RoutingError",
    "RoutingService",
    "TravelMode",
    "calculate_bounds",
    "decode_polyline",
    "encode_polyline",
    "to_geojson_linestring",
]
