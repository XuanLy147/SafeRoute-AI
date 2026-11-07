"""Route-specific exceptions for Google Routes API integration (TV5)."""


class RoutingError(Exception):
    """Base exception for all routing errors."""

    def __init__(self, message: str, status_code: int = 500, details: dict | None = None):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.details = details or {}


class RouteServiceError(RoutingError):
    """Generic error when upstream Google Routes API returns an error response."""


class InvalidCoordinatesError(RoutingError):
    """Raised when origin or destination coordinates are out of valid bounds."""

    def __init__(self, message: str = "Invalid coordinates provided"):
        super().__init__(message, status_code=400)


class NoRouteFoundError(RoutingError):
    """Raised when Google Routes API returns no routes between origin and destination."""

    def __init__(self, message: str = "No route found between the specified origin and destination"):
        super().__init__(message, status_code=404)


class RouteServiceAuthError(RoutingError):
    """Raised when Google Routes API key is missing, invalid, or unauthorized (HTTP 401/403)."""

    def __init__(self, message: str = "Google Routes API authentication failed. Check API key and service enablement."):
        super().__init__(message, status_code=502)


class RouteServiceQuotaError(RoutingError):
    """Raised when Google Routes API rate limit or quota is exceeded (HTTP 429)."""

    def __init__(self, message: str = "Google Routes API quota or rate limit exceeded."):
        super().__init__(message, status_code=429)


class RouteServiceTimeoutError(RoutingError):
    """Raised when Google Routes API request times out."""

    def __init__(self, message: str = "Request to Google Routes API timed out."):
        super().__init__(message, status_code=504)


class RouteServiceConnectionError(RoutingError):
    """Raised when network connection to Google Routes API fails."""

    def __init__(self, message: str = "Could not connect to Google Routes API."):
        super().__init__(message, status_code=502)
