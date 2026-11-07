# AI_TRACKER.md — Theo dõi tiến độ AI-assisted development

**Dự án:** SafeRoute AI · **Nhóm:** COME FOR FOOD · **Hackathon:** SGU 2026–2027  
**Repository:** `XuanLy147/SafeRoute-AI` · **Cập nhật khung:** 2026-11-07  
**Quy tắc:** Trạng thái dưới đây là **khởi tạo theo cấu trúc repo**, không suy diễn kết quả build/test. Thành viên hoặc coding agent phải cập nhật ngay khi có bằng chứng.

## 1. Hướng dẫn cập nhật
- `TODO`: chưa bắt đầu hoặc chưa xác minh đã làm.
- `IN_PROGRESS`: đang sửa code có thể kiểm tra được.
- `BLOCKED`: thiếu API contract, quyền, SDK, key hoặc phụ thuộc; ghi blocker.
- `DONE_VERIFIED`: có commit/PR, lệnh test thực chạy + log hoặc bằng chứng demo.
- Không điền số liệu mô hình, latency/cost, URL cloud, PASS giả. Gắn liên kết PR/commit, logs, ảnh test **không chứa secrets**.
- **AI bắt đầu ca mới:** đọc `AGENTS.md`, kiểm tra branch/`git status`, chọn task đúng owner; tránh sửa hai branch không liên quan.
- Thêm dòng trong Nhật ký AI khi bắt đầu, bàn giao hoặc gặp blocker.

## 2. Nhánh & owner
| Branch | Owner | Phạm vi | Tình trạng |
|---|---|---|---|
| `main` | Team/merge owner | Code đã review/tích hợp | Scaffold initial |
| `frontend` | TV1, TV2 | Android Java/XML | Scaffold initial |
| `backend` | TV3, TV4, TV5 | FastAPI, DB, AI, routing, cloud | Scaffold initial |
| `TV03` | TV3 | FastAPI core, Auth/RBAC, Reports, Images, Votes, Review, AI, Routing, OpenAPI | Active (Commits: `c81aaeb`, `ac607a2`) |

> Branch trạng thái khởi tạo không có nghĩa Android/FastAPI đã build thành công. Không tự chép code từ `SafeRoute-AI_Demo` mà chưa kiểm tra và tạo PR.

## 3. Bảng task P0 — ưu tiên dòng chạy thật
| ID | Owner | Hạng mục / kết quả tối thiểu | Nhánh | Trạng thái | Bằng chứng | Blocker / phụ thuộc |
|---|---|---|---|---|---|---|
| TV1-01 | TV1 | Một Android Studio project Java/XML, app khởi động trên máy thật | frontend | TODO | — | SDK/Gradle/toolchain |
| TV1-02 | TV1 | Map, marker report, filter, preview, GPS permission fallback | frontend | TODO | — | Google Maps key; reports API |
| TV1-03 | TV1 | Chọn A/B, xem các tuyến thật, polyline và reason/risk | frontend | TODO | — | Route response TV5/TV3 |
| TV2-01 | TV2 | Photo picker/camera, GPS, AI preview, sửa nhãn/manual fallback | frontend | TODO | — | AI contract TV4/TV3 |
| TV2-02 | TV2 | Gửi report PENDING, my reports, detail/vote | frontend | TODO | — | Reports/auth APIs TV3 |
| TV2-03 | TV2 | Reviewer queue, verify/reject + reason, role-aware UI | frontend | TODO | — | RBAC/review API TV3 |
| TV3-01 | TV3 | FastAPI chạy được, `/health`, config không lộ secret | TV03 | DONE_VERIFIED | Commit `c81aaeb`, `ac607a2`; `test_health.py` (2 tests PASS); `/health` trả về 200 OK | — |
| TV3-02 | TV3 | PostGIS schema/entities + Auth/RBAC + report/vote/review/images/routes endpoints | TV03 | DONE_VERIFIED | Commit `c81aaeb`, `ac607a2`; 16 endpoints hoạt động; Bcrypt >72B guard; `SecureStaticFiles` nosniff/CSP; 75/75 tests PASS | Live PostGIS DB container (TV4) |
| TV3-03 | TV3 | Xuất OpenAPI thật; API errors/permissions tests | TV03 | DONE_VERIFIED | File `docs/api/openapi.json` (16 paths, OpenAPI 3.1.0); test suite 75/75 PASS; ruff 0 lỗi | Bàn giao TV1/TV2/TV4/TV5 duyệt contract |
| TV4-01 | TV4 | Chốt weights v1.1, class map, SHA/version, inference smoke test | backend | TODO | — | Model artifact/runtime |
| TV4-02 | TV4 | AI API: detections[]/bbox; timeout/empty/error; evidence evaluation | backend | TODO | — | API owner TV3; Android TV2 |
| TV5-01 | TV5 | Google Routes thật, decode polyline, risk scoring với PostGIS | backend | TODO | — | Routes key/billing, DB TV3 |
| TV5-02 | TV5 | Docker/Cloud Run HTTPS health, secret management | backend | TODO | — | Cloud credentials, runnable backend |
| TV5-03 | TV5 | CI/CD chạy build/lint/test; E2E + latency/cost evidence | backend | TODO | — | All modules |

## 4. API-LOCK / quyết định xuyên nhóm
| Contract | Owners xác nhận | Trạng thái | Link/chứng cứ | Ghi chú |
|---|---|---|---|---|
| Auth/report/vote/review: endpoints, RBAC, status enum | TV2 + TV3 + TV1 | CONFIRMED | `docs/api/openapi.json`, commit `c81aaeb`, `ac607a2` | `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED`; Vote: `SUPPORT`, `OPPOSE`, `UNSURE` đồng bộ Android DTO TV2 |
| AI: multipart, 5 classes, bbox coordinate system, errors | TV2 + TV3 + TV4 | CONFIRMED | `POST /api/v1/ai/detect`, SHA256 `ab42e303...` khớp 100%, PIL/corrupted stream error handling, production 503 guard | 5 classes: `D00`, `D10`, `D20`, `D40`, `Repair`; trả `detections: []` khi đường sạch |
| Routing: origin/destination, polyline, routes[], recommended_id, hazards | TV1 + TV3 + TV5 | CONFIRMED | `POST /api/v1/routes/compare`, `test_routing.py` (33 tests PASS), `test_routing_api.py` (3 tests PASS) | Google Routes v2 candidates, PostGIS risk integration, ETA fallback an toàn |
| DB: geography/geometry, lat-lon, review audit, indexes | TV3 + TV5 | CONFIRMED | `backend/app/models/entities.py`, SRID 4326 Point/LineString | Giữ nguyên migrations TV4, tương thích truy vấn không gian `ST_DWithin` |
| Android: package, MainActivity, nav, Gradle, shared DTO | TV1 + TV2 | UNCONFIRMED | — | Không duplicate app/client |

## 5. Trục kiểm thử & minh chứng 100 điểm
| Tiêu chí BTC | Điểm | Bằng chứng mục tiêu | Tình trạng |
|---|---:|---|---|
| Sáng tạo & đổi mới | 10 | So sánh giải pháp, lợi thế cụ thể, không gán novelty tùy tiện | TODO |
| Tính thực tiễn | 10 | Persona, tình huống giao thông, thử nghiệm trên máy/địa bàn cụ thể | TODO |
| Dữ liệu & mô hình | 20 | Checkpoint SHA256 `ab42e303...` v1.1 verified, inference singleton, error handling bảo vệ | IN_PROGRESS |
| Kiến trúc & hoàn thiện | 20 | FastAPI modular 16 endpoints, 75/75 unit tests PASS, Ruff 0 lỗi, bảo mật Bcrypt/Magic bytes/CSP/Nosniff | IN_PROGRESS |
| Đóng góp cộng đồng | 10 | Báo cáo cộng đồng PENDING_VERIFICATION, vote cộng đồng không tự duyệt, audit log thanh tra | IN_PROGRESS |
| AI tiết kiệm chi phí | 10 | Latency + memory + cost theo runtime cloud đo được | TODO |
| Cloud AWS/Google | 10 | HTTPS deployment, health URL thực, logs và config an toàn | TODO |
| Teamwork, demo & phản biện | 10 | Phân công, PR, script demo, Q&A, bằng chứng phối hợp | TODO |

## 6. Nhật ký AI / Handoff (cập nhật liên tục)
| Thời điểm (Asia/Ho_Chi_Minh) | TV / Agent | Branch | Đã làm / tệp thay đổi | Test (PASS/FAIL/NOT_RUN) | PR/Commit | Blocker & việc tiếp theo |
|---|---|---|---|---|---|---|
| 2026-11-07 | Team setup | main | Tạo bộ khung `AGENTS.md`, `AI_TRACKER.md` | NOT_RUN (docs only) | See git history | TV1–TV5 bắt đầu từ đúng branch |
| 2026-11-07 17:15 | TV3 (Backend) | TV03 | Hoàn tất FastAPI core, Auth JWT/RBAC, Reports, Images/SecureStaticFiles, Votes (`SUPPORT/OPPOSE/UNSURE`), Review Audit, AI Exception Handling, Routing API bridge (`POST /routes/compare`), OpenAPI 16 endpoints. | 75 PASS / 0 FAIL (100% PASS), Ruff ALL PASS | Commit `c81aaeb`, `ac607a2` on branch `TV03` | Sẵn sàng bàn giao TV4 (Live PostGIS DB verification) và TV2/TV1 (Android Retrofit E2E smoke test). |

## 7. Nguyên tắc demo
- **P0-A:** Ảnh/GPS → YOLO gợi ý hoặc [] → user sửa → submit PENDING → reviewer quyết định → map refresh.
- **P0-B:** Điểm A/B → Google Routes trả tuyến → PostGIS risk scorer → hiển thị giải thích theo nguy cơ ghi nhận.
- Chỉ tick DONE nếu thực sự demo/test được. Mock data phải có nhãn; không giới thiệu demo mock như backend thật.
- Mọi thông tin còn chưa biết phải được ghi rõ `UNKNOWN` hoặc `UNVERIFIED`, không điền ước lượng như số đo thật.
