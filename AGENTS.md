# AGENTS.md — SafeRoute AI / COME FOR FOOD

> Hướng dẫn áp dụng cho mọi AI coding agent (Codex, Antigravity, v.v.) khi làm việc trong repository này. Đọc trước khi đọc/sửa code. Root-level scope: toàn bộ repository.

## 1. Dự án & mục tiêu
SafeRoute AI — Hackathon SGU 2026–2027, nhóm COME FOR FOOD (5 thành viên).
Ứng dụng Android dùng ảnh + GPS để cộng đồng ghi nhận hư hỏng mặt đường; YOLOv12s gợi ý nhãn, người dùng xác nhận/sửa, report chờ xác minh, reviewer/admin quyết định, bản đồ hiển thị các nguy cơ và hệ thống so sánh các tuyến dựa trên **nguy cơ đã ghi nhận**. Không cam kết tuyến tuyệt đối an toàn.

## 2. Bắt đầu phiên làm việc — BẮT BUỘC
1. Đọc `README.md`, `AGENTS.md`, `AI_TRACKER.md` và tài liệu liên quan trong `docs/`.
2. Kiểm tra `git status --short`, `git branch --show-current`, `git log -1 --oneline`; tuyệt đối không xóa thay đổi chưa commit.
3. Xác định chủ sở hữu module (TV1–TV5), phạm vi task, đầu vào/đầu ra và API contract hiện hành.
4. Kiểm tra files và test hiện có; không tự suy đoán file trống = code hoàn thành.
5. Chỉ sửa những file thuộc task. Với file dùng chung, nêu tác động và phối hợp owner.
6. Trước khi bàn giao, build/test thật nếu môi trường cho phép, ghi bằng chứng vào báo cáo. Cập nhật tracker nếu đã xác minh trạng thái.

## 3. Stack đã khóa
- Android: **Java 17 + Android XML Views**, một project trong `android/`. Không dùng Kotlin, Compose, Flutter, React Native, WebView thay UI native.
- Maps: Google Maps SDK for Android; routing backend dùng Google Routes API.
- Backend: Python FastAPI; database PostgreSQL + PostGIS.
- AI: YOLOv12s SafeRoute AI v1.1, các class `D00`, `D10`, `D20`, `D40`, `Repair`.
- Hạ tầng: Docker, Google Cloud Run, GitHub Actions (khi được triển khai, không coi file khung là CI/CD hoạt động).
- Không tự nâng AGP/Gradle/Python/PyTorch/Ultralytics hay thay mô hình mà chưa kiểm tra compatibility và trao đổi.

## 4. Module owners
| Owner | Trách nhiệm | Vùng code chính |
|---|---|---|
| TV1 | App shell, MainActivity/navigation, Map/GPS/markers, routing UI | `android/` → `feature/map/`, `feature/routing/` |
| TV2 | Auth, báo cáo ảnh/AI preview/manual correction, report detail, vote, review UI, shared Retrofit/DTO | `android/` → `feature/{auth,report,reports,review,profile}/`, `core/network/`, `core/model/` |
| TV3 | FastAPI, PostgreSQL/PostGIS, RBAC, reports, migrations, OpenAPI | `backend/` (API + DB) |
| TV4 | Inference YOLO, class mapping, model manifest, evaluation, AI service | `ai/`, `backend/app/modules/ai/` |
| TV5 | Google Routes adapter, risk scoring, Cloud Run, Docker/CI/CD, integration | `backend/app/modules/routing/`, `infra/`, `.github/workflows/` |

**Shared contract:** TV3 giữ chuẩn OpenAPI và DB migrations; TV1 giữ Android app shell/navigation; TV2 giữ Android network/DTO; TV4/TV5 thống nhất AI/route schemas với TV3 và Android. Không tạo bản trùng của MainActivity, Retrofit client, API DTO hay database schema.

## 5. Git workflow
- `main`: tích hợp sau review.
- `frontend`: TV1 + TV2 (Android).
- `backend`: TV3 + TV4 + TV5 (API, AI, routing, cloud).
- Ưu tiên tạo `feature/tv1-...` / `feature/tv2-...` từ `frontend`; `feature/tv3-...` / `feature/tv4-...` / `feature/tv5-...` từ `backend`. Merge qua PR.
- Không tự `git reset --hard`, `git clean -fd`, force-push, tự push/merge, xóa file người khác hoặc đổi branch khi có thay đổi chưa lưu.
- Repository `SafeRoute-AI_Demo` là nguồn tham khảo code thử nghiệm riêng; không tuyên bố repo chính đã tích hợp nếu chưa có commit.

## 6. Business rules không được phá
- Report mới: `PENDING_VERIFICATION`; quyết định chính thức chỉ bởi REVIEWER/ADMIN: `VERIFIED` hoặc `REJECTED`. Vote cộng đồng không thay thế xác minh.
- No-detection: vẫn cho nhập nhãn thủ công. User có thể sửa đề xuất AI.
- `Repair` là class của AI, **không** phải trạng thái `RESOLVED`.
- `confidence` AI ≠ mức độ nguy hiểm (`severity`).
- Pending có thể hiển thị nếu **gắn nhãn chưa xác minh**; rejected không dùng để cảnh báo như report hợp lệ.
- Routing chỉ dùng tuyến Google Routes thực sự trả về; 1 tuyến thì hiển thị đúng 1; không dựng tuyến giả. Đề xuất là **ít nguy cơ đã ghi nhận hơn**, không chắc chắn an toàn.
- GPS có thể bị từ chối; ảnh thiếu metadata, mất mạng/AI lỗi, timeout, no-hazard, empty list đều cần fallback.
- Tọa độ PostGIS/GeoJSON: chú ý (longitude, latitude) khi lưu; Android LatLng thường (latitude, longitude).

## 7. API & bảo mật
- Đừng tự bịa field JSON/multipart/bbox/status. Xem `docs/api/` và backend OpenAPI **đã sinh từ implementation thật** trước khi viết client.
- API quan trọng dự kiến: `POST /api/v1/ai/detect`, `POST /api/v1/routes/compare`, các endpoints auth/report/review — chi tiết phải được chốt bởi owners.
- Không đưa API keys/tokens, service-account JSON, private photos, model weights chưa phê duyệt, `.env`, `local.properties` lên Git.
- Backend thực thi RBAC và audit review; Android ẩn UI không thay kiểm tra quyền ở server.
- Che thông tin nhạy cảm trong log, giữ việc truy cập ảnh và vị trí ở mức cần thiết.

## 8. Quy chuẩn chất lượng/hackathon 100 điểm
BTC chấm: sáng tạo 10, thực tiễn 10, dữ liệu & mô hình 20, chất lượng triển khai 20, cộng đồng 10, chi phí AI 10, cloud 10, teamwork/demo/phản biện 10.
Mỗi task nên nêu tiêu chí liên quan, cách kiểm thử, bằng chứng, owner. Không tự chế accuracy/mAP, latency, chi phí, cloud URL, CI xanh hoặc kết quả user test.
Trạng thái task trong `AI_TRACKER.md`: `TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE_VERIFIED`. Chỉ `DONE_VERIFIED` khi có bằng chứng test/build.

## 9. Quy tắc trả lời/hoàn tất
- Trình bày ngắn: mục tiêu, thay đổi file, kết quả test (PASS/FAIL/NOT_RUN), blockers, bước tiếp theo, impact lên team.
- Chủ động thực thi task được giao trong phạm vi được phép, không chỉ viết pseudocode.
- Nếu thiếu SDK, secrets, backend hoặc quyền truy cập: báo giới hạn rõ ràng, không tuyên bố đã chạy.
- Ưu tiên luồng P0 chạy thật: (A) ảnh→AI→user sửa→report PENDING→review→map; (B) A/B→Google Routes→risk scoring→UI, trước tính năng trình diễn phụ.
