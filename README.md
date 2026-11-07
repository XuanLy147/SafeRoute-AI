# SafeRoute AI — COME FOR FOOD

Repository chính của nhóm **COME FOR FOOD** cho Hackathon SGU 2026–2027.

SafeRoute AI là ứng dụng Android giúp cộng đồng báo cáo hư hỏng mặt đường bằng ảnh và GPS, dùng YOLOv12s hỗ trợ nhận diện để người dùng xác nhận, hiển thị cảnh báo trên bản đồ và so sánh các tuyến theo **nguy cơ đã được ghi nhận** (không cam kết an toàn tuyệt đối).

## Phân công & công nghệ

| Vai trò | Phạm vi | Công nghệ |
|---|---|---|
| TV1 | Android bản đồ, GPS, marker, tuyến đường, navigation | Java + XML, AndroidX, Google Maps SDK |
| TV2 | Android báo cáo ảnh/AI, tài khoản, bình chọn, kiểm duyệt | Java + XML, Retrofit |
| TV3 | API, Auth/Reports/Images/Votes/Review, AI HTTP | FastAPI |
| TV4 | Database/PostGIS, Alembic, Risk, Docker, Cloud, CI/CD | PostgreSQL/PostGIS, Docker, Cloud Run |
| TV5 | Google Routes, Routing Service, Geometry, Recommendation | Google Routes API |

## Cấu trúc thư mục

- `android/` — **Một** Android Studio project dùng chung cho TV1/TV2 (Java + XML).
- `backend/` — **Một** FastAPI modular monolith; sở hữu API và DB migrations.
- `ai/` — Model metadata, đánh giá, tài liệu nguồn weights/dataset; runtime AI gắn vào backend.
- `infra/` — Hạ tầng Cloud Run, Docker/triển khai.
- `docs/` — Kiến trúc, hợp đồng API, bản mẫu UI, kiểm thử, demo.
- `scripts/` — Lệnh dùng chung và smoke test.
- `.github/workflows/` — CI/CD sau khi có mã chạy và credentials cấu hình an toàn.

> **Trạng thái frontend sau ghép TV345:** Android Java/XML đã có giao diện, Maps và tìm địa điểm chạy trên máy thật; 53 JVM tests PASS. Source backend, migrations, OpenAPI và handoff TV345 đã được ghép cùng repository. Android HTTP/image adapter chưa nối nên hai luồng P0 chưa đạt E2E trên điện thoại. Xem [bản ghép frontend–TV345](docs/integration/frontend-tv345-merge.md).

## Nguyên tắc phối hợp

1. Làm việc trên nhánh `feature/*`, mở Pull Request để tích hợp lên `main`.
2. TV1 sở hữu `MainActivity`, navigation và Map/Routing UI; TV2 sở hữu Report/Auth/Review và client/network dùng chung.
3. TV3 xuất `openapi.json` từ backend thực tế. Không tự tạo nhiều hợp đồng JSON khác nhau.
4. Giữ nguyên Java/XML. Không tự chuyển Kotlin, Compose, Flutter, React Native.
5. Không commit API keys, token, `.env`, `local.properties`, `.venv/`, build output, ảnh cá nhân, model weights nếu chưa có chính sách lưu trữ.
6. Báo cáo mới có trạng thái chưa xác minh; AI confidence không phải mức độ nguy hiểm; không có detection vẫn cho báo cáo thủ công.
7. Chỉ ghi DONE/PASS nếu đã build/test có bằng chứng.

Tham khảo code thử nghiệm tại repository riêng `SafeRoute-AI_Demo`; **không mặc định code demo đã có trong repo chính**.

## TV345 integration handoff — 2026-11-07

Nhánh `integration/tv345-backend-handoff` tích hợp source TV3/TV4/TV5, giữ Android
read-only. Phân công hiện hành: TV3 API/Auth/Reports/AI HTTP; TV4 DB/PostGIS/Alembic/
Risk/Docker/Cloud/CI; TV5 Google Routes/Routing/Geometry/Recommendation. Các mô tả
scaffold ở trên là lịch sử khởi tạo, không phải trạng thái kiểm chứng mới.

Xem [handoff](docs/handoff/RELEASE_READINESS.md), [local development](docs/handoff/LOCAL_DEVELOPMENT.md),
[source audit](docs/integration/TV345_SOURCE_AUDIT.md) và [risk decision](docs/integration/TV345_RISK_DECISION.md).
