"""Google Routes API adapter (TV5).

Connects to Google Routes API v2 (https://routes.googleapis.com/directions/v2:computeRoutes)
with field mask, handles real calls, decodes geometry, and normalizes candidate routes.
"""

import os
from typing import Any

import httpx

from .exceptions import (
    InvalidCoordinatesError,
    NoRouteFoundError,
    RouteServiceAuthError,
    RouteServiceConnectionError,
    RouteServiceError,
    RouteServiceQuotaError,
    RouteServiceTimeoutError,
)
from .polyline import decode_polyline
from .schemas import LatLng, RouteCandidate, TravelMode

GOOGLE_ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
DEFAULT_FIELD_MASK = (
    "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.description,routes.warnings"
)


class GoogleRoutesAdapter:
    """Adapter for Google Routes API v2."""

    def __init__(
        self,
        api_key: str | None = None,
        timeout_seconds: float = 10.0,
        client: httpx.Client | None = None,
    ):
        """Initialize adapter.

        Args:
            api_key: Google Cloud API Key. If None, reads from GOOGLE_ROUTES_API_KEY env var.
            timeout_seconds: Request timeout in seconds.
            client: Optional httpx.Client (for connection pooling or unit test injection).
        """
        self.api_key = api_key or os.environ.get("GOOGLE_ROUTES_API_KEY", "")
        self.timeout_seconds = timeout_seconds
        self._client = client

    def _get_client(self) -> httpx.Client:
        if self._client is not None:
            return self._client
        return httpx.Client(timeout=self.timeout_seconds)

    def _validate_coordinates(self, origin: LatLng, destination: LatLng) -> None:
        """Validate geographical coordinates."""
        if not (-90.0 <= origin.latitude <= 90.0 and -180.0 <= origin.longitude <= 180.0):
            raise InvalidCoordinatesError(f"Origin coordinates invalid: ({origin.latitude}, {origin.longitude})")
        if not (-90.0 <= destination.latitude <= 90.0 and -180.0 <= destination.longitude <= 180.0):
            raise InvalidCoordinatesError(
                f"Destination coordinates invalid: ({destination.latitude}, {destination.longitude})"
            )
        if origin.latitude == destination.latitude and origin.longitude == destination.longitude:
            raise InvalidCoordinatesError("Origin and destination cannot be identical")

    def _build_request_payload(
        self,
        origin: LatLng,
        destination: LatLng,
        travel_mode: TravelMode = TravelMode.TWO_WHEELER,
    ) -> dict[str, Any]:
        """Build Google Routes API v2 request body."""
        payload = {
            "origin": origin.to_google_location(),
            "destination": destination.to_google_location(),
            "travelMode": travel_mode.value,
            "routingPreference": "TRAFFIC_AWARE",
            "computeAlternativeRoutes": True,
            "routeModifiers": {
                "avoidTolls": False,
                "avoidHighways": False,
                "avoidFerries": False,
            },
            "languageCode": "vi-VN",
            "units": "METRIC",
        }

        if travel_mode in (TravelMode.WALK, TravelMode.BICYCLE):
            payload.pop("routingPreference")
            payload.pop("routeModifiers")
        return payload

    @staticmethod
    def _parse_duration(duration_raw: Any) -> int:
        """Parse Google duration string (e.g. '450s' or '3600.5s') to integer seconds."""
        if isinstance(duration_raw, (int, float)):
            return int(duration_raw)
        if isinstance(duration_raw, str):
            clean = duration_raw.rstrip("s")
            try:
                return round(float(clean))
            except ValueError:
                pass
        return 0

    def parse_routes_response(self, response_data: dict[str, Any]) -> list[RouteCandidate]:
        """Normalize raw Google Routes API JSON into a list of RouteCandidate models.

        Args:
            response_data: Parsed JSON dictionary from Google Routes API.

        Returns:
            List of RouteCandidate objects.
        """
        raw_routes = response_data.get("routes", [])
        if not raw_routes:
            return []

        candidates: list[RouteCandidate] = []
        for idx, r in enumerate(raw_routes, start=1):
            route_id = f"route_{idx}"
            summary = r.get("description", "").strip() or f"Tuyến {idx}"
            distance_meters = int(r.get("distanceMeters", 0))
            duration_seconds = self._parse_duration(r.get("duration", 0))

            polyline_obj = r.get("polyline", {})
            encoded_polyline = polyline_obj.get("encodedPolyline", "")

            # Decode polyline into list of (lat, lon) coordinates
            coordinates = decode_polyline(encoded_polyline) if encoded_polyline else []

            # Extract warnings
            raw_warnings = r.get("warnings", [])
            warnings = [w if isinstance(w, str) else str(w) for w in raw_warnings]

            candidate = RouteCandidate(
                id=route_id,
                summary=summary,
                distance_meters=distance_meters,
                duration_seconds=duration_seconds,
                polyline=encoded_polyline,
                coordinates=coordinates,
                warnings=warnings,
            )
            candidates.append(candidate)

        return candidates

    def compute_routes(
        self,
        origin: LatLng,
        destination: LatLng,
        travel_mode: TravelMode = TravelMode.TWO_WHEELER,
    ) -> list[RouteCandidate]:
        """Fetch and normalize real route candidates from Google Routes API.

        Args:
            origin: Starting coordinate.
            destination: Target coordinate.
            travel_mode: TWO_WHEELER (default), DRIVE, BICYCLE, or WALK.

        Returns:
            List of normalized RouteCandidate objects. Exactly 1 if Google returns 1 candidate.

        Raises:
            InvalidCoordinatesError: Invalid lat/long coordinates.
            NoRouteFoundError: No routes exist between points.
            RouteServiceAuthError: Invalid API key or permission denied.
            RouteServiceQuotaError: Quota exceeded.
            RouteServiceTimeoutError: Timeout.
            RouteServiceConnectionError: Network failure.
        """
        self._validate_coordinates(origin, destination)

        if not self.api_key:
            raise RouteServiceAuthError(
                "GOOGLE_ROUTES_API_KEY is not configured. Set the environment variable or pass api_key."
            )

        headers = {
            "Content-Type": "application/json",
            "X-Goog-Api-Key": self.api_key,
            "X-Goog-FieldMask": DEFAULT_FIELD_MASK,
        }
        payload = self._build_request_payload(origin, destination, travel_mode)

        client = self._get_client()
        should_close = self._client is None

        try:
            response = client.post(
                GOOGLE_ROUTES_URL,
                headers=headers,
                json=payload,
                timeout=self.timeout_seconds,
            )
        except httpx.TimeoutException as exc:
            raise RouteServiceTimeoutError(f"Google Routes API timed out after {self.timeout_seconds}s: {exc}") from exc
        except httpx.NetworkError as exc:
            raise RouteServiceConnectionError(f"Network error connecting to Google Routes API: {exc}") from exc
        except Exception as exc:
            raise RouteServiceConnectionError(f"Unexpected connection error: {exc}") from exc
        finally:
            if should_close:
                client.close()

        # Handle HTTP status codes
        if response.status_code in (401, 403):
            raise RouteServiceAuthError(
                f"Google Routes API authorization failed (HTTP {response.status_code}): {response.text}"
            )
        if response.status_code == 429:
            raise RouteServiceQuotaError(f"Google Routes API quota exceeded (HTTP 429): {response.text}")
        if response.status_code == 400:
            raise InvalidCoordinatesError(f"Google Routes API rejected parameters (HTTP 400): {response.text}")
        if response.status_code >= 500:
            raise RouteServiceError(
                f"Google Routes API upstream server error (HTTP {response.status_code}): {response.text}",
                status_code=response.status_code,
            )
        if response.status_code != 200:
            raise RouteServiceError(
                f"Google Routes API unexpected status (HTTP {response.status_code}): {response.text}",
                status_code=response.status_code,
            )

        data = response.json()
        candidates = self.parse_routes_response(data)

        if not candidates:
            raise NoRouteFoundError(
                f"No route found between ({origin.latitude}, {origin.longitude}) and ({destination.latitude}, {destination.longitude})"
            )

        return candidates
