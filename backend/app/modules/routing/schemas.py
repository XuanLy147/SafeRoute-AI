"""Pydantic schemas for the Routing module (TV5).

Defines standard request/response models for Google Routes candidate normalization,
route comparison, and risk score integration with TV4/TV3/TV1.
"""

from datetime import datetime
from enum import Enum
from typing import Literal

from pydantic import BaseModel, Field, model_validator


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
    coordinates: list[tuple[float, float]] = Field(
        default_factory=list, description="Decoded list of (latitude, longitude) coordinate pairs"
    )
    warnings: list[str] = Field(default_factory=list, description="Navigation warnings from Google Routes")

    def to_geojson(self) -> dict:
        """Export coordinates as GeoJSON LineString [longitude, latitude] for PostGIS / TV4."""
        return {"type": "LineString", "coordinates": [[lng, lat] for lat, lng in self.coordinates]}


class RouteRiskScore(BaseModel):
    """Risk assessment contract provided by TV4 (PostGIS risk scorer)."""

    score: float = Field(..., ge=0.0, allow_inf_nan=False, description="Calculated hazard risk score")
    level: Literal["LOW", "MEDIUM", "HIGH"] | None = Field(None, description="Risk level: 'LOW', 'MEDIUM', 'HIGH'")
    hazards_count: int = Field(..., ge=0, description="Total recorded hazards near this route")
    verified_hazards_count: int = Field(0, ge=0, description="Verified hazards near route")
    pending_hazards_count: int = Field(0, ge=0, description="Pending verification hazards near route")
    reason: str = Field(..., description="Human-readable explanation of recorded hazards")


class RouteHazard(BaseModel):
    id: str
    category: str
    status: Literal["VERIFIED", "PENDING_VERIFICATION"]
    latitude: float
    longitude: float
    severity: str | None = None
    observed_at: datetime | None = None
    source_report_ids: list[str] = Field(default_factory=list)
    score_contribution: float | None = Field(None, ge=0, allow_inf_nan=False)


class EvaluatedRoute(BaseModel):
    """Route candidate combined with risk evaluation."""

    id: str
    summary: str
    distance_meters: int
    duration_seconds: int
    polyline: str
    nearby_hazards: list[RouteHazard] | None = Field(
        default=None, description="Nearby recorded hazards; null means unavailable, [] means none found"
    )
    hazard_radius_meters: float | None = Field(default=None, description="Proximity radius, not proof of same road/lane")
    severity_counts: dict[str, int] | None = None
    verified_high_count: int = 0
    unscored_hazards_count: int = 0
    risk_density_per_km: float | None = Field(None, ge=0, allow_inf_nan=False)
    risk: RouteRiskScore | None = Field(
        default=None, description="Risk assessment from PostGIS hazard data; None if scoring service not configured"
    )


class RouteCompareRequest(BaseModel):
    """Request payload for POST /api/v1/routes/compare."""

    origin: LatLng
    destination: LatLng
    travel_mode: TravelMode = Field(
        default=TravelMode.TWO_WHEELER,
        description="Travel mode: TWO_WHEELER (default for Vietnam), DRIVE, BICYCLE, or WALK",
    )


class RouteCompareResponse(BaseModel):
    """Standardized response schema for POST /api/v1/routes/compare.

    Consumed by Android UI (TV1) and exposed via FastAPI (TV3).
    """

    origin: LatLng
    destination: LatLng
    travel_mode: TravelMode
    assessed_at: datetime | None = None
    risk_diagnostic: str | None = None
    risk_policy_revision: str | None = None
    risk_policy_status: str | None = None
    routes: list[EvaluatedRoute] = Field(
        ..., description="List of real candidate routes. Exactly 1 if Google returns 1 route; 0 if unreachable."
    )
    recommended_route_id: str | None = Field(
        None, description="ID of the recommended route (lowest recorded risk). None if no routes exist."
    )
    recommendation_reason: str | None = Field(
        None, description="Explanation why this route is recommended based on recorded hazards."
    )
    disclaimer: str = Field(
        default="SafeRoute AI không cam kết tuyến đường an toàn tuyệt đối. Luôn quan sát điều kiện giao thông thực tế.",
        description="Legal and safety disclaimer required by SafeRoute AI business rules.",
    )
    risk_assessment_status: Literal["AVAILABLE", "UNAVAILABLE", "PARTIAL", "INVALID"] | None = Field(
        None, description="Semantic status of hazard assessment: 'AVAILABLE', 'UNAVAILABLE', 'PARTIAL', 'INVALID'"
    )
    recommendation_basis: Literal["SAFETY_LOWEST_RISK", "DURATION_ETA_ONLY", "SINGLE_ROUTE"] | None = Field(
        None, description="Basis of recommendation: 'SAFETY_LOWEST_RISK', 'DURATION_ETA_ONLY', 'SINGLE_ROUTE'"
    )

    @model_validator(mode="after")
    def consistent_evidence_assessment(self):
        if self.risk_policy_revision != "recorded-hazards-v1-2026-11-08":
            return self
        from math import isclose
        ids = [r.id for r in self.routes]
        if len(ids) != len(set(ids)) or (self.recommended_route_id is not None and self.recommended_route_id not in ids):
            raise ValueError("Invalid candidate IDs")
        scored = 0
        for r in self.routes:
            hs = r.nearby_hazards
            if hs is None:
                if r.risk is not None:
                    raise ValueError("Score without evidence")
                continue
            levels = {k: sum((h.severity if h.severity in ("LOW", "MEDIUM", "HIGH") else "UNKNOWN") == k for h in hs)
                      for k in ("LOW", "MEDIUM", "HIGH", "UNKNOWN")}
            sources = [id for h in hs for id in h.source_report_ids]
            if len(sources) != len(set(sources)) or any(h.id not in h.source_report_ids for h in hs):
                raise ValueError("Duplicate/missing source reports")
            if r.severity_counts != levels or r.verified_high_count != sum(h.status == "VERIFIED" and h.severity == "HIGH" for h in hs):
                raise ValueError("Severity/evidence mismatch")
            if r.unscored_hazards_count != sum(h.score_contribution is None for h in hs):
                raise ValueError("Incomplete count mismatch")
            if r.risk is None:
                if r.unscored_hazards_count == 0:
                    raise ValueError("Complete evidence without score")
                continue
            scored += 1
            if r.unscored_hazards_count or r.risk.hazards_count != len(hs):
                raise ValueError("Hazard count mismatch")
            verified = sum(h.status == "VERIFIED" for h in hs)
            if r.risk.verified_hazards_count != verified or r.risk.pending_hazards_count != len(hs)-verified:
                raise ValueError("Verification count mismatch")
            if not isclose(r.risk.score, sum(h.score_contribution for h in hs), abs_tol=1e-9):
                raise ValueError("Contribution sum mismatch")
        expected = "AVAILABLE" if scored == len(self.routes) and self.routes else "PARTIAL" if scored else "UNAVAILABLE"
        if self.risk_assessment_status != expected:
            raise ValueError("Assessment availability mismatch")
        if self.recommendation_basis == "SAFETY_LOWEST_RISK" and expected != "AVAILABLE":
            raise ValueError("Incomplete safety comparison")
        return self
