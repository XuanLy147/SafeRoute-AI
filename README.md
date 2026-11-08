# SafeRoute AI — COME FOR FOOD

Ứng dụng Android Java 17/XML kết nối FastAPI, PostgreSQL/PostGIS, YOLOv12s v1.1
và Google Routes. Báo cáo cộng đồng được người dùng xác nhận trước khi gửi ở
trạng thái PENDING_VERIFICATION; chỉ REVIEWER/ADMIN xác minh hoặc từ chối.
So sánh tuyến dựa trên nguy cơ đã ghi nhận, không cam kết tuyến tuyệt đối an toàn.

## Trạng thái tích hợp

Nhánh `integration/saferoute-full-release` khôi phục backend sau revert frontend
và nối Android với HTTP API thật. Đây không còn là scaffold. Chỉ merge main khi
các mandatory gates đạt; không suy diễn production-ready từ build thành công.
Xem [Release readiness](docs/release/RELEASE_READINESS.md),
[audit](docs/release/FULL_PROJECT_AUDIT.md) và
[provenance](docs/release/MERGE_PROVENANCE.md).

## Khởi chạy

```powershell
git clone https://github.com/XuanLy147/SafeRoute-AI.git
cd SafeRoute-AI
git checkout integration/saferoute-full-release
```

- Android: mở `frontend/` bằng Android Studio, SDK 35; Java source 17.
  Cấu hình Maps key và backend origin theo [Android runbook](docs/release/ANDROID_CONFIGURATION.md).
- Backend: Python 3.11, cài `backend/requirements.txt` và runtime AI đã pin trong
  `backend/requirements-ai.txt`; xem [local development](docs/handoff/LOCAL_DEVELOPMENT.md).
- PostGIS/Docker: đặt POSTGRES_PASSWORD và SECRET_KEY ngẫu nhiên trong môi trường,
  chạy `docker compose -f backend/docker-compose.yml up --build`. Compose chạy
  migrations, không dùng create_all để thay Alembic. `/health` là liveness,
  `/ready` kiểm tra DB migration và báo trạng thái AI/risk.
- Tạo USER bằng đăng ký thật trong app. Reviewer phải được owner cấp quyền trong DB.
  Chỉ harness E2E mới tự cấp quyền trong database test dùng một lần.
- Google Routes dùng server key riêng; Maps dùng Android-restricted key. Không
  đưa keys vào Git. Risk policy mặc định chưa khả dụng; không tự bật DEMO policy.

## Kiểm thử và bàn giao

Android: `frontend/gradlew.bat -p frontend :app:testDebugUnitTest :app:assembleDebug
:app:assembleDebugAndroidTest :app:lintDebug --console=plain` (một dòng).
Backend: `python scripts/qa/verify_postgis.py --backend backend --runxfail`,
`python scripts/qa/verify_contract.py`, `python scripts/qa/verify_ai.py`,
`ruff check backend`.

Docker: `docker build -f backend/Dockerfile -t saferoute-tv345:integration .`,
sau đó `python scripts/qa/verify_container.py`.
Android/backend thật: `python scripts/qa/verify_android_e2e.py` với emulator đang
boot, ANDROID_HOME/JAVA_HOME đã cấu hình. Xem runbook để phân biệt regression
fixtures và Google live tests. GitHub Actions chạy Android và backend trên PR/main/release.

[OpenAPI](docs/api/openapi.json) · [API lock](docs/handoff/API_CONTRACT_LOCK.md) ·
[Demo](docs/release/DEMO_SCRIPT.md) · [Giới hạn](docs/release/KNOWN_LIMITATIONS.md) ·
[Final handoff](docs/release/FINAL_HANDOFF.md) · [Tracker](AI_TRACKER.md).

Một Android project, một shared HTTP client, một schema/migration source.
Không commit `.env`, `local.properties`, tokens, ảnh cá nhân hay build outputs.
Cloud Run production chưa được triển khai/xác minh trong task này.
