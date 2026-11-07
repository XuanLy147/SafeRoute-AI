"""Pydantic schemas for the Routing module (TV5).

Defines standard request/response models for Google Routes candidate normalization,
route comparison, and risk score integration with TV4/TV3/TV1.
"""

from enum import Enum
from typing import List, Optional, Tuple
from pydantic import BaseModel, Field, field_validator


class TravelMode(str, Enum):
    """Supported travel modes for Google Routes API."""
    TWO_WHEELER = "TWO_WHEELER"
    DRIVE = "DRIVE"
    BICYCLE = "BICYCLE"
    WALK = "WALK"


class LatLng(BaseModel):
    """Geographic coordinate representation (WGS84)."""
    latitude: float = Field(..., ge=-90.0, le=90.0, description="Latitude in degrees [-90, 90]")
    longitude: float = Field(..., ge=-180.0, le=180.0, description="Longitude in degrees [-180, 180]")

    def to_google_location(self) -> dict:
        """Format as Google Routes API v2 location payload."""
        return {
            "location": {
                "latLng": {
                    "latitude": self.latitude,
                    "longitude": self.longitude,
                }
            }
        }


class RouteCandidate(BaseModel):
    """Normalized route candidate returned by Google Routes API adapter."""
    id: str = Field(..., description="Unique identifier for the route within comparison session (e.g. route_1)")
    summary: str = Field("", description="Route name / description from Google Routes (e.g. 'Via Võ Văn Kiệt')")
    distance_meters: int = Field(..., ge=0, description="Total route distance in meters")
    duration_seconds: int = Field(..., ge=0, description="Estimated travel duration in seconds")
    polyline: str = Field(..., description="Google encoded polyline string")
    coordinates: List[Tuple[float, float]] = Field(
        default_factory=list,
        description="Decoded list of (latitude, longitude) coordinate pairs"
    )
    warnings: List[str] = Field(default_factory=list, description="Navigation warnings from Google Routes")

    def to_geojson(self) -> dict:
        """Export coordinates as GeoJSON LineString [longitude, latitude] for PostGIS / TV4."""
        return {
            "type": "LineString",
            "coordinates": [[lng, lat] for lat, lng in self.coordinates]
        }


class RouteRiskScore(BaseModel):
    """Risk assessment contract provided by TV4 (PostGIS risk scorer)."""
    score: float = Field(..., ge=0.0, description="Calculated hazard risk score")
    level: str = Field(..., description="Risk level: 'LOW', 'MEDIUM', 'HIGH'")
    hazards_count: int = Field(..., ge=0, description="Total recorded hazards near this route")
    verified_hazards_count: int = Field(0, ge=0, description="Verified hazards near route")
    pending_hazards_count: int = Field(0, ge=0, description="Pending verification hazards near route")
    reason: str = Field(..., description="Human-readable explanation of recorded hazards")


class EvaluatedRoute(BaseModel):
    """Route candidate combined with risk evaluation."""
    id: str
    summary: str
    distance_meters: int
    duration_seconds: int
    polyline: str
    risk: Optional[RouteRiskScore] = Field(
        default=None,
        description="Risk assessment from PostGIS hazard data; None if scoring service not configured"
    )


class RouteCompareRequest(BaseModel):
    """Request payload for POST /api/v1/routes/compare."""
    origin: LatLng
    destination: LatLng
    travel_mode: TravelMode = Field(
        default=TravelMode.TWO_WHEELER,
        description="Travel mode: TWO_WHEELER (default for Vietnam), DRIVE, BICYCLE, or WALK"
    )


class RouteCompareResponse(BaseModel):
    """Standardized response schema for POST /api/v1/routes/compare.

    Consumed by Android UI (TV1) and exposed via FastAPI (TV3).
    """
    origin: LatLng
    destination: LatLng
    travel_mode: TravelMode
    routes: List[EvaluatedRoute] = Field(
        ...,
        description="List of real candidate routes. Exactly 1 if Google returns 1 route; 0 if unreachable."
    )
    recommended_route_id: Optional[str] = Field(
        None,
        description="ID of the recommended route (lowest recorded risk). None if no routes exist."
    )
    recommendation_reason: Optional[str] = Field(
        None,
        description="Explanation why this route is recommended based on recorded hazards."
    )
    disclaimer: str = Field(
        default="Tuyến đường được đề xuất dựa trên các nguy cơ mặt đường đã ghi nhận trong hệ thống. SafeRoute AI không cam kết tuyến đường an toàn tuyệt đối.",
        description="Legal and safety disclaimer required by SafeRoute AI business rules."
    )

