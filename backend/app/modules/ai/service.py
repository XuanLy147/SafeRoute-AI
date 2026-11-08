import hashlib
import os
import time
from pathlib import Path
from threading import Lock
from typing import Optional

from fastapi import HTTPException, status

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

    _instance_lock = Lock()
    _predict_lock = Lock()
    _instance: Optional["YoloInferenceService"] = None

    def __init__(self, weights_path: str | None = None):
        self.weights_path = weights_path or os.getenv(
            "YOLO_WEIGHTS_PATH", str(Path(__file__).resolve().parents[4] / "model ai" / "weights" / "best.pt")
        )
        self.model_version = "SafeRoute-YOLOv12s-v1.1"
        self.device = "cpu"
        self.model = None
        self.checksum_valid = False
        self._load_model()

    @classmethod
    def get_instance(cls) -> "YoloInferenceService":
        with cls._instance_lock:
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
            print(f"[WARN] Checkpoint khong ton tai tai: {self.weights_path}. AI unavailable; manual reporting supported.")
            return

        self.checksum_valid = self._verify_checksum()
        if not self.checksum_valid:
            print(f"[WARN] Canh bao: Checksum cua {self.weights_path} khong khop voi manifest an toan.")
            return

        # Thu nap qua Ultralytics neu co cai dat
        try:
            from ultralytics import YOLO

            model = YOLO(self.weights_path)
            if dict(model.names) != {i: v[0] for i, v in CLASS_MAP.items()}:
                raise ValueError("Checkpoint class mapping mismatch")
            self.model = model
            print(f"[OK] Da nap thanh cong YOLOv12s checkpoint ({self.model_version}) len {self.device}.")
        except ImportError:
            print("[INFO] Thu vien 'ultralytics' chua cai dat trong runtime nay. Model YOLO khong kha dung.")
            self.model = None
        except Exception:
            print("[WARN] AI model load failed")
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
                with self._predict_lock:
                    results = self.model.predict(img, conf=0.25, verbose=False, device=self.device)

                for r in results:
                    boxes = r.boxes
                    for box in boxes:
                        cls_id = int(box.cls[0].item())
                        conf = float(box.conf[0].item())
                        xyxy = [float(coord) for coord in box.xyxyn[0].tolist()]

                        class_info = CLASS_MAP[cls_id]
                        detections.append(
                            DetectionItem(
                                class_code=class_info[0],
                                class_name=class_info[1],
                                confidence=round(conf, 4),
                                bbox=xyxy,
                            )
                        )
                current_version = self.model_version
            except Exception:
                print("[ERROR] AI inference failed")
                raise HTTPException(
                    status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
                    detail="Đã xảy ra lỗi trong quá trình thực thi suy luận mô hình AI.",
                )
        else:
            raise HTTPException(status_code=503, detail="AI model unavailable; manual reporting remains available")

        elapsed_ms = round((time.time() - start_time) * 1000, 2)
        return AiDetectResponse(
            model_version=current_version,
            inference_time_ms=elapsed_ms,
            device=self.device,
            detections=detections,
        )


# Khởi tạo singleton
ai_service = YoloInferenceService.get_instance()
