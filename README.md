# SafeRoute AI — COME FOR FOOD

Repository chính của nhóm **COME FOR FOOD** cho Hackathon SGU 2026–2027.

SafeRoute AI là ứng dụng Android giúp cộng đồng báo cáo hư hỏng mặt đường bằng ảnh và GPS, dùng YOLOv12s hỗ trợ nhận diện để người dùng xác nhận, hiển thị cảnh báo trên bản đồ và so sánh các tuyến theo **nguy cơ đã được ghi nhận** (không cam kết an toàn tuyệt đối).

## Phân công & công nghệ

| Vai trò | Phạm vi | Công nghệ |
|---|---|---|
| TV1 | Android bản đồ, GPS, marker, tuyến đường, navigation | Java + XML, AndroidX, Google Maps SDK |
| TV2 | Android báo cáo ảnh/AI, tài khoản, bình chọn, kiểm duyệt | Java + XML, Retrofit |
| TV3 | API, xác thực, database, reports/review | FastAPI, PostgreSQL/PostGIS |
| TV4 | AI inference, kiểm thử và model card | YOLOv12s v1.1, Python |
| TV5 | Google Routes, risk scoring, Cloud, CI/CD | Routes API, Docker, Cloud Run |

## Cấu trúc thư mục

- `android/` — **Một** Android Studio project dùng chung cho TV1/TV2 (Java + XML).
- `backend/` — **Một** FastAPI modular monolith; sở hữu API và DB migrations.
- `ai/` — Model metadata, đánh giá, tài liệu nguồn weights/dataset; runtime AI gắn vào backend.
- `infra/` — Hạ tầng Cloud Run, Docker/triển khai.
- `docs/` — Kiến trúc, hợp đồng API, bản mẫu UI, kiểm thử, demo.
- `scripts/` — Lệnh dùng chung và smoke test.
- `.github/workflows/` — CI/CD sau khi có mã chạy và credentials cấu hình an toàn.

> **Trạng thái:** Repo vừa được khởi tạo cấu trúc. File đánh dấu thư mục và tài liệu không chứng minh chức năng đã triển khai. Code chạy thật được phát triển và kiểm thử sau.

## Nguyên tắc phối hợp

1. Làm việc trên nhánh `feature/*`, mở Pull Request để tích hợp lên `main`.
2. TV1 sở hữu `MainActivity`, navigation và Map/Routing UI; TV2 sở hữu Report/Auth/Review và client/network dùng chung.
3. TV3 xuất `openapi.json` từ backend thực tế. Không tự tạo nhiều hợp đồng JSON khác nhau.
4. Giữ nguyên Java/XML. Không tự chuyển Kotlin, Compose, Flutter, React Native.
5. Không commit API keys, token, `.env`, `local.properties`, `.venv/`, build output, ảnh cá nhân, model weights nếu chưa có chính sách lưu trữ.
6. Báo cáo mới có trạng thái chưa xác minh; AI confidence không phải mức độ nguy hiểm; không có detection vẫn cho báo cáo thủ công.
7. Chỉ ghi DONE/PASS nếu đã build/test có bằng chứng.

Tham khảo code thử nghiệm tại repository riêng `SafeRoute-AI_Demo`; **không mặc định code demo đã có trong repo chính**.
