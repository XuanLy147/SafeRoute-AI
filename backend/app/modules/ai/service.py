import hashlib
import os
import time
from typing import Optional

from fastapi import HTTPException, status

from app.core.config import settings
from app.modules.ai.schemas import AiDetectResponse, DetectionItem

CLASS_MAP = {
    0: ("D00", "Longitudinal Crack"),
    1: ("D10", "Transverse Crack"),
    2: ("D20", "Alligator Crack"),
    3: ("D40", "Pothole"),
    4: ("Repair", "Road Repair Patch"),
}

EXPECTED_SHA256 = "ab42e303396857560995dd0fd4d54262015ef77851709220c2f20ca5fc6a4f4c"

class YoloInferenceService:
    """
    Singleton Service quản lý nạp và suy luận YOLOv12s SafeRoute v1.1.
    Chỉ nạp trọng số 1 lần duy nhất trong suốt vòng đời của ứng dụng.
    """
    _instance: Optional["YoloInferenceService"] = None

    def __init__(self, weights_path: str | None = None):
        self.weights_path = weights_path or os.getenv(
            "YOLO_WEIGHTS_PATH",
            os.path.join(os.path.dirname(__file__), "..", "..", "..", "..", "ai", "weights", "best.pt")
        )
        self.model_version = "SafeRoute-YOLOv12s-v1.1"
        self.device = "cpu"
        self.model = None
        self.checksum_valid = False
        self._load_model()

    @classmethod
    def get_instance(cls) -> "YoloInferenceService":
        if cls._instance is None:
            cls._instance = YoloInferenceService()
        return cls._instance

    def is_model_ready(self) -> bool:
        """Kiểm tra mô hình đã được nạp thành công và checksum hợp lệ hay chưa."""
        return (self.model is not None) and self.checksum_valid

    def _verify_checksum(self) -> bool:
        if not os.path.exists(self.weights_path):
            return False
        try:
            hasher = hashlib.sha256()
            with open(self.weights_path, "rb") as f:
                for chunk in iter(lambda: f.read(65536), b""):
                    hasher.update(chunk)
            digest = hasher.hexdigest().lower()
            return digest == EXPECTED_SHA256.lower()
        except Exception:
            return False

    def _load_model(self):
        if not os.path.exists(self.weights_path):
            print(f"[WARN] Checkpoint khong ton tai tai: {self.weights_path}. San sang fallback dev.")
            return

        self.checksum_valid = self._verify_checksum()
        if not self.checksum_valid:
            print(f"[WARN] Canh bao: Checksum cua {self.weights_path} khong khop voi manifest an toan.")
            return

        # Thu nap qua Ultralytics neu co cai dat
        try:
            from ultralytics import YOLO
            self.model = YOLO(self.weights_path)
            print(f"[OK] Da nap thanh cong YOLOv12s checkpoint ({self.model_version}) len {self.device}.")
        except ImportError:
            print("[INFO] Thu vien 'ultralytics' chua cai dat trong runtime nay. Model YOLO khong kha dung.")
            self.model = None
        except Exception as e:
            print(f"[WARN] Loi khi nap YOLO model: {e}")
            self.model = None

    def predict(self, image_bytes: bytes, filename: str = "image.jpg") -> AiDetectResponse:
        start_time = time.time()
        detections: list[DetectionItem] = []

        # 1. Chạy suy luận thật nếu model đã nạp
        if self.model is not None and self.checksum_valid:
            try:
                import io

                from PIL import Image
                img = Image.open(io.BytesIO(image_bytes)).convert("RGB")
                results = self.model.predict(img, conf=0.25, verbose=False)

                for r in results:
                    boxes = r.boxes
                    for box in boxes:
                        cls_id = int(box.cls[0].item())
                        conf = float(box.conf[0].item())
                        xyxy = [round(float(coord), 2) for coord in box.xyxy[0].tolist()]

                        class_info = CLASS_MAP.get(cls_id, ("OTHER", "Road Hazard"))
                        detections.append(
                            DetectionItem(
                                class_code=class_info[0],
                                class_name=class_info[1],
                                confidence=round(conf, 4),
                                bbox=xyxy,
                            )
                        )
                current_version = self.model_version
            except Exception as e:
                print(f"[ERROR] Loi khi thuc hien inference YOLO: {e}")
                detections = []
                current_version = self.model_version
        else:
            # Rào chắn Production: Nghiêm cấm giả mạo kết quả hoặc chạy heuristic ngầm trong production
            if settings.ENVIRONMENT.lower() == "production":
                raise HTTPException(
                    status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                    detail="AI Inference Engine tạm thời không khả dụng trong môi trường production (Model chưa sẵn sàng).",
                )

            # 2. MOCK_FOR_DEV: Chỉ cho phép trong môi trường test/dev, minh bạch tên version
            current_version = "MOCK_FOR_DEV-v1.0"
            lower_name = filename.lower()
            if "pothole" in lower_name or "o_ga" in lower_name or "d40" in lower_name:
                detections.append(
                    DetectionItem(
                        class_code="D40",
                        class_name="Pothole",
                        confidence=0.8845,
                        bbox=[120.0, 80.0, 310.0, 290.0],
                    )
                )
            elif "crack" in lower_name or "nut" in lower_name or "d00" in lower_name:
                detections.append(
                    DetectionItem(
                        class_code="D00",
                        class_name="Longitudinal Crack",
                        confidence=0.7420,
                        bbox=[50.0, 150.0, 420.0, 210.0],
                    )
                )
            else:
                # Ảnh đường bình thường hoặc không phát hiện hư hỏng: detections=[] hợp lệ
                detections = []

        elapsed_ms = round((time.time() - start_time) * 1000, 2)
        return AiDetectResponse(
            model_version=current_version,
            inference_time_ms=elapsed_ms,
            device=self.device,
            detections=detections,
        )

# Khởi tạo singleton
ai_service = YoloInferenceService.get_instance()
