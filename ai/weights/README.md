# SafeRoute AI — Model checkpoint for backend (TV3 / TV4)

**Checkpoint expected path:** `ai/weights/best.pt`  
**File uploaded by project member in conversation:** `best.pt`  
**Measured size:** 18,930,714 bytes (~18.06 MiB)  
**SHA-256:** `ab42e303396857560995dd0fd4d54262015ef77851709220c2f20ca5fc6a4f4c`

> **STATUS: AWAITING BINARY UPLOAD.** This README and checksum **do not contain the model weights**. Do not claim the model has been uploaded until `best.pt` is present and its SHA-256 matches. The checkpoint was not unpickled or run while preparing this metadata.

## Upload to the `backend` branch

1. Open https://github.com/XuanLy147/SafeRoute-AI/tree/backend/ai/weights
2. Choose **Add file → Upload files**, select the original `best.pt` from your computer.
3. Commit with the message: `chore(ai): add verified SafeRoute AI checkpoint` to branch `backend`.
4. Confirm that `ai/weights/best.pt` appears on GitHub, and verify SHA-256.

This file is ~18.06 MiB, under the GitHub browser upload limit of 25 MiB. Large or frequently updated model binaries should use **Git LFS / GitHub Releases / Cloud Storage** instead of repeated normal Git commits.

The root `.gitignore` ignores all `*.pt` except this exact approved path.

## Verify integrity

From repository root (Linux/macOS/Git Bash):

```bash
cd ai/weights
sha256sum -c best.pt.sha256
```

From PowerShell on Windows:

```powershell
(Get-FileHash .\ai\weights\best.pt -Algorithm SHA256).Hash.ToLower()
```

The value **must match** `ab42e303396857560995dd0fd4d54262015ef77851709220c2f20ca5fc6a4f4c`.

## Backend usage

Set environment variable for the process that starts the backend:

```bash
# From the repository root:
export YOLO_WEIGHTS_PATH="$(pwd)/ai/weights/best.pt"

# If running backend/ as the working directory:
export YOLO_WEIGHTS_PATH="../ai/weights/best.pt"
```

PowerShell (from the repo root):

```powershell
$env:YOLO_WEIGHTS_PATH = (Resolve-Path .\ai\weights\best.pt).Path
```

The backend AI adapter should read `YOLO_WEIGHTS_PATH` and load the model **once during application startup**, not once per HTTP request. A Python example for a trusted checkpoint, after installing a version of Ultralytics/PyTorch confirmed compatible with YOLOv12s v1.1:

```python
import os
from ultralytics import YOLO

model = YOLO(os.environ["YOLO_WEIGHTS_PATH"])
# Model inference and class mapping must be validated with real smoke tests.
```

The `.pt` format may contain serialized Python objects. **Only load trusted checkpoints** and pin the runtime dependencies; do not treat a hash match as a malware scan.

## Team validation (TV4 / TV3)

- Confirm whether this binary is actually the intended YOLOv12s **v1.1** checkpoint; this version is project context, not independently recovered from weights.
- Check actual model class order against planned `D00`, `D10`, `D20`, `D40`, `Repair`.
- Confirm runtime versions, model license and dataset provenance; record smoke test results and any known failure cases.
- Agree on the inference response (detections[], confidence, bounding-box coordinates, model_version) before connecting TV2.
- For Cloud Run, consider storing weights in Cloud Storage / build artifact or downloading them at deployment; document provenance and startup latency. **Do not hardcode secrets.**
