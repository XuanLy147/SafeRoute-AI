"""Real trusted-checkpoint CPU smoke, not an accuracy/evaluation benchmark."""
import hashlib
import io
import json
import sys
from pathlib import Path
root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / "backend"))
from PIL import Image
import torch
import ultralytics
from app.modules.ai.service import ai_service, EXPECTED_SHA256
assert hashlib.sha256((root / "ai/weights/best.pt").read_bytes()).hexdigest() == EXPECTED_SHA256
assert ai_service.is_model_ready(), "Real checkpoint/runtime unavailable"
assert ai_service.model.names == {0:"D00",1:"D10",2:"D20",3:"D40",4:"Repair"}
image = io.BytesIO()
Image.new("RGB", (640, 640), (128,128,128)).save(image, format="PNG")
result = ai_service.predict(image.getvalue())
assert result.model_version == "SafeRoute-YOLOv12s-v1.1"
assert all(len(d.bbox)==4 and all(0<=v<=1 for v in d.bbox) for d in result.detections)
print(json.dumps({"status":"PASS", "input":"synthetic gray image; NOT accuracy evaluation",
                  "torch":torch.__version__,"ultralytics":ultralytics.__version__,
                  "cuda_available":torch.cuda.is_available(), "result":result.model_dump()},ensure_ascii=False))
