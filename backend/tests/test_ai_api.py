import io

from fastapi.testclient import TestClient

from app.core.config import settings
from app.main import app
from app.modules.ai.service import ai_service

client = TestClient(app)

# 10x10 valid complete PNG binary with IHDR, IDAT, and IEND chunks
VALID_PNG_BYTES = (
    b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\n\x00\x00\x00\n\x08\x02\x00\x00\x00"
    b"\x02PX\xea\x00\x00\x00\x16IDATx\x9cc\xfc\xff\xff?\x03n\xc0\x84G\x8ea\xe4J\x03\x00"
    b"\xa5\xe3\x03\x11\xc7z\x1cU\x00\x00\x00\x00IEND\xaeB`\x82"
)


def test_checkpoint_checksum_valid():
    """Kiểm tra tính toàn vẹn SHA256 của checkpoint best.pt do nhóm cung cấp."""
    assert ai_service.checksum_valid is True
    assert ai_service._verify_checksum() is True


def test_ai_detect_with_blank_image_returns_empty_detections():
    """Quy tắc an toàn AI: Khi ảnh không phát hiện nguy cơ, server trả về detections: [] và HTTP 200 OK."""
    image_file = io.BytesIO(VALID_PNG_BYTES)

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
    """Kiểm tra cấu trúc detection item trả về: class_code, class_name, confidence, bbox."""
    image_file = io.BytesIO(VALID_PNG_BYTES)

    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("pothole_d40.png", image_file, "image/png")}
    )
    assert response.status_code == 200
    data = response.json()
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
    """Kiểm tra bảo mật: Khai báo image/png nhưng gửi text payload -> Magic bytes mismatch -> 400."""
    fake_png = io.BytesIO(b"This is plain text pretending to be PNG")
    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("fake.png", fake_png, "image/png")}
    )
    assert response.status_code == 400
    assert "Magic bytes mismatch" in response.json()["detail"]


def test_ai_detect_corrupted_image_stream_returns_400():
    """AI Exception Handling: File có magic bytes hợp lệ nhưng dữ liệu bị cắt ngắn/hỏng -> PIL verify bắt lỗi -> 400."""
    corrupted_png = b"\x89PNG\r\n\x1a\n" + b"Corrupted broken trailing data without IHDR or IEND"
    image_file = io.BytesIO(corrupted_png)

    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("corrupt.png", image_file, "image/png")}
    )
    assert response.status_code == 400
    detail = response.json()["detail"]
    assert "Dữ liệu ảnh không hợp lệ" in detail or "hỏng" in detail or "giải mã" in detail


def test_ai_detect_oversized_image_returns_413():
    """AI Exception Handling: Ảnh vượt quá giới hạn MAX_IMAGE_SIZE_MB (5MB) -> 413 Payload Too Large."""
    large_payload = b"\x89PNG\r\n\x1a\n" + b"0" * (6 * 1024 * 1024)
    image_file = io.BytesIO(large_payload)

    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("large.png", image_file, "image/png")}
    )
    assert response.status_code == 413
    assert "vượt quá giới hạn cho phép" in response.json()["detail"]


def test_ai_production_blocks_silent_heuristic_fallback(monkeypatch):
    """Nhiệm vụ 4: Kiểm tra xác minh production không âm thầm dùng heuristic mock khi model chưa sẵn sàng."""
    monkeypatch.setattr(settings, "ENVIRONMENT", "production")
    monkeypatch.setattr(ai_service, "model", None)

    image_file = io.BytesIO(VALID_PNG_BYTES)

    response = client.post(
        "/api/v1/ai/detect",
        files={"file": ("pothole_d40.png", image_file, "image/png")}
    )
    assert response.status_code == 503
    assert "AI Inference Engine chưa sẵn sàng" in response.json()["detail"]
