"""Google Routes API v2 response fixtures (TV5).

Marked clearly as MOCK_FOR_DEV / TEST FIXTURES.
Used for offline development, reproducible unit tests, and CI/CD pipelines
without requiring live Google Cloud billing.
"""

from typing import Any, Dict

# Origin: Ben Thanh Market (10.7725, 106.6980)
# Destination: Saigon University (10.7599, 106.6823)

FIXTURE_ORIGIN = {"latitude": 10.7725, "longitude": 106.6980}
FIXTURE_DESTINATION = {"latitude": 10.7599, "longitude": 106.6823}

# 1. Realistic Google Routes API response with TWO candidates (MOCK_FOR_DEV)
FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES: Dict[str, Any] = {
    "_fixture_label": "MOCK_FOR_DEV_TWO_CANDIDATES",
    "routes": [
        {
            "distanceMeters": 2950,
            "duration": "450s",
            "description": "Đ. Trần Hưng Đạo",
            "polyline": {
                "encodedPolyline": "c_w`AomfjSb[nd@f^nd@jRbV"
            },
            "warnings": []
        },
        {
            "distanceMeters": 3480,
            "duration": "510s",
            "description": "Đ. Võ Văn Kiệt và Đ. Nguyễn Văn Cừ",
            "polyline": {
                "encodedPolyline": "c_w`AomfjSzT_Xvj@vj@vj@~p@s]j\\"
            },
            "warnings": ["Đoạn đường có thể đông vào giờ cao điểm"]
        }
    ]
}

# 2. Realistic Google Routes API response with SINGLE candidate (MOCK_FOR_DEV)
# Per ROUTE01 rule: when Google returns only 1 route, API/UI must return 1 route only.
FIXTURE_GOOGLE_ROUTES_SINGLE_CANDIDATE: Dict[str, Any] = {
    "_fixture_label": "MOCK_FOR_DEV_SINGLE_CANDIDATE",
    "routes": [
        {
            "distanceMeters": 2950,
            "duration": "450s",
            "description": "Đ. Trần Hưng Đạo",
            "polyline": {
                "encodedPolyline": "c_w`AomfjSb[nd@f^nd@jRbV"
            },
            "warnings": []
        }
    ]
}

# 3. Google Routes API response when NO route is found (MOCK_FOR_DEV)
FIXTURE_GOOGLE_ROUTES_EMPTY: Dict[str, Any] = {
    "_fixture_label": "MOCK_FOR_DEV_EMPTY",
    "routes": []
}

# 4. Google Routes API 401/403 Error response (MOCK_FOR_DEV)
FIXTURE_GOOGLE_ROUTES_AUTH_ERROR: Dict[str, Any] = {
    "_fixture_label": "MOCK_FOR_DEV_AUTH_ERROR",
    "error": {
        "code": 403,
        "message": "The provided API key is invalid or Google Routes API is not enabled on this project.",
        "status": "PERMISSION_DENIED"
    }
}

# 5. Google Routes API 429 Rate Limit Error response (MOCK_FOR_DEV)
FIXTURE_GOOGLE_ROUTES_QUOTA_ERROR: Dict[str, Any] = {
    "_fixture_label": "MOCK_FOR_DEV_QUOTA_ERROR",
    "error": {
        "code": 429,
        "message": "Resource has been exhausted (e.g. check quota).",
        "status": "RESOURCE_EXHAUSTED"
    }
}

