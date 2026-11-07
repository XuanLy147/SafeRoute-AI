from fastapi.testclient import TestClient

from app.main import app
from app.modules.routing.fixtures import FIXTURE_DESTINATION, FIXTURE_ORIGIN

client = TestClient(app)


def test_routing_compare_success(monkeypatch):
    from app.modules.routing.adapter import GoogleRoutesAdapter
    from app.modules.routing.fixtures import FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES

    candidates = GoogleRoutesAdapter().parse_routes_response(FIXTURE_GOOGLE_ROUTES_TWO_CANDIDATES)
    monkeypatch.setattr(GoogleRoutesAdapter, "compute_routes", lambda *a, **kw: candidates)
    """Kiểm tra gọi POST /api/v1/routes/compare trả về 200 OK kèm danh sách tuyến và đề xuất an toàn."""
    payload = {
        "origin": FIXTURE_ORIGIN,
        "destination": FIXTURE_DESTINATION,
        "travel_mode": "TWO_WHEELER",
    }
    response = client.post("/api/v1/routes/compare", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "routes" in data
    assert len(data["routes"]) > 0
    assert "recommended_route_id" in data
    assert "disclaimer" in data
    assert "SafeRoute AI không cam kết tuyến đường an toàn tuyệt đối" in data["disclaimer"]


def test_routing_compare_identical_coordinates_returns_400():
    """Điểm đi và điểm đến trùng nhau -> Bắt buộc 400 Bad Request."""
    payload = {
        "origin": FIXTURE_ORIGIN,
        "destination": FIXTURE_ORIGIN,
        "travel_mode": "TWO_WHEELER",
    }
    response = client.post("/api/v1/routes/compare", json=payload)
    assert response.status_code == 400
    assert "identical" in response.json()["detail"].lower() or "trùng" in response.json()["detail"].lower()


def test_routing_compare_invalid_coordinates_range_returns_422():
    """Tọa độ ngoài phạm vi hợp lệ [-90, 90] -> Pydantic validation trả về 422."""
    payload = {
        "origin": {"latitude": 999.0, "longitude": 106.6980},
        "destination": FIXTURE_DESTINATION,
        "travel_mode": "TWO_WHEELER",
    }
    response = client.post("/api/v1/routes/compare", json=payload)
    assert response.status_code == 422
