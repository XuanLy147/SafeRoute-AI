from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

def test_health_check_returns_200():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert "status" in data
    assert "service" in data
    assert "version" in data
    assert "database" in data
    assert "timestamp" in data

def test_ping_v1():
    response = client.get("/api/v1/ping")
    assert response.status_code == 200
    assert response.json()["message"] == "SafeRoute AI API v1 ready"
