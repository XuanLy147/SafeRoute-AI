import io

from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app
from app.modules.ai.service import ai_service

client = TestClient(app)

def test_checkpoint_checksum_valid():
    """
    Kiểm tra tính toàn vẹn SHA256 của checkpoint best.pt do nhóm cung cấp.
    """
    assert ai_service.checksum_valid is True
    # Kiểm tra phương thức verify
    assert ai_service._verify_checksum() is True

def test_ai_detect_with_blank_image_returns_empty_detections():
    """
    Quy tắc an toàn AI: Khi ảnh không phát hiện nguy cơ, server trả về detections: []
    và HTTP 200 OK bình thường (không sập ứng dụng).
    """
    blank_png = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15c4"
    image_file = io.BytesIO(blank_png)

    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("normal_road.png", image_file, "image/png")}
    )
    assert response.status_code == 200
    data = response.json()
    assert "model_version" in data
    assert "inference_time_ms" in data
    assert isinstance(data["detections"], list)
    assert data["detections"] == []

def test_ai_detect_with_hazard_image_returns_detections():
    """
    Kiểm tra cấu trúc detection item trả về: class_code, class_name, confidence, bbox.
    Không giả mạo tên YOLO khi chạy chế độ mock fallback dev.
    """
    img_bytes = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15c4"
    image_file = io.BytesIO(img_bytes)

    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("pothole_d40.png", image_file, "image/png")}
    )
    assert response.status_code == 200
    data = response.json()
    # Nếu nạp model thật: SafeRoute-YOLOv12s-v1.1; nếu chưa có Ultralytics/PyTorch: MOCK_FOR_DEV-v1.0
    assert data["model_version"] in ("SafeRoute-YOLOv12s-v1.1", "MOCK_FOR_DEV-v1.0")
    assert len(data["detections"]) > 0

    first_det = data["detections"][0]
    assert first_det["class_code"] == "D40"
    assert first_det["class_name"] == "Pothole"
    assert 0.0 <= first_det["confidence"] <= 1.0
    assert len(first_det["bbox"]) == 4

def test_ai_detect_invalid_mime_type_returns_400():
    txt_file = io.BytesIO(b"not an image")
    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("malicious.sh", txt_file, "text/x-sh")}
    )
    assert response.status_code == 400
    assert "Định dạng file không hợp lệ" in response.json()["detail"]

def test_ai_detect_magic_bytes_mismatch_returns_400():
    """
    Kiểm tra bảo mật: Khai báo image/png nhưng gửi text payload -> Magic bytes mismatch -> 400.
    """
    fake_png = io.BytesIO(b"This is plain text pretending to be PNG")
    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("fake.png", fake_png, "image/png")}
    )
    assert response.status_code == 400
    assert "Magic bytes mismatch" in response.json()["detail"]

def test_ai_production_blocks_silent_heuristic_fallback(monkeypatch):
    """
    Nhiệm vụ 4: Kiểm tra xác minh production không âm thầm dùng heuristic mock khi model chưa sẵn sàng.
    """
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")
    monkeypatch.setattr(ai_service, "model", None)

    valid_png = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x06\x00\x00\x00\x1f\x15c4"
    image_file = io.BytesIO(valid_png)

    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("pothole_d40.png", image_file, "image/png")}
    )
    assert response.status_code == 503
    assert "AI Inference Engine chưa sẵn sàng" in response.json()["detail"]
