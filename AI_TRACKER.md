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
| TV3-01 | TV3 | FastAPI chạy được, `/health`, config không lộ secret | backend | TODO | — | Python/runtime |
| TV3-02 | TV3 | PostGIS schema + migrations + auth/RBAC + report/vote/review endpoints | backend | TODO | — | DB/contract |
| TV3-03 | TV3 | Xuất OpenAPI thật; API errors/permissions tests | backend | TODO | — | TV2/TV4/TV5 duyệt contract |
| TV4-01 | TV4 | Chốt weights v1.1, class map, SHA/version, inference smoke test | backend | TODO | — | Model artifact/runtime |
| TV4-02 | TV4 | AI API: detections[]/bbox; timeout/empty/error; evidence evaluation | backend | TODO | — | API owner TV3; Android TV2 |
| TV5-01 | TV5 | Google Routes thật, decode polyline, risk scoring với PostGIS | backend | TODO | — | Routes key/billing, DB TV3 |
| TV5-02 | TV5 | Docker/Cloud Run HTTPS health, secret management | backend | TODO | — | Cloud credentials, runnable backend |
| TV5-03 | TV5 | CI/CD chạy build/lint/test; E2E + latency/cost evidence | backend | TODO | — | All modules |

## 4. API-LOCK / quyết định xuyên nhóm
| Contract | Owners xác nhận | Trạng thái | Link/chứng cứ | Ghi chú |
|---|---|---|---|---|
| Auth/report/vote/review: endpoints, RBAC, status enum | TV2 + TV3 + TV1 | UNCONFIRMED | — | `PENDING_VERIFICATION`, `VERIFIED`, `REJECTED` |
| AI: multipart, 5 classes, bbox coordinate system, errors | TV2 + TV3 + TV4 | UNCONFIRMED | — | No detection → manual fallback |
| Routing: origin/destination, polyline, routes[], recommended_id, hazards | TV1 + TV3 + TV5 | UNCONFIRMED | — | Google candidates, no invented alternatives |
| DB: geography/geometry, lat-lon, review audit, indexes | TV3 + TV5 | UNCONFIRMED | — | PostGIS migration là source of truth |
| Android: package, MainActivity, nav, Gradle, shared DTO | TV1 + TV2 | UNCONFIRMED | — | Không duplicate app/client |

## 5. Trục kiểm thử & minh chứng 100 điểm
| Tiêu chí BTC | Điểm | Bằng chứng mục tiêu | Tình trạng |
|---|---:|---|---|
| Sáng tạo & đổi mới | 10 | So sánh giải pháp, lợi thế cụ thể, không gán novelty tùy tiện | TODO |
| Tính thực tiễn | 10 | Persona, tình huống giao thông, thử nghiệm trên máy/địa bàn cụ thể | TODO |
| Dữ liệu & mô hình | 20 | Provenance/license, mAP/precision/recall thực, edge tests, bias/safety | TODO |
| Kiến trúc & hoàn thiện | 20 | APK build, API/DB tests, UI UX, CI/CD, bảo mật, end-to-end | TODO |
| Đóng góp cộng đồng | 10 | User journey, accessibility, reviewer workflow, kế hoạch nhân rộng | TODO |
| AI tiết kiệm chi phí | 10 | Latency + memory + cost theo runtime cloud đo được | TODO |
| Cloud AWS/Google | 10 | HTTPS deployment, health URL thực, logs và config an toàn | TODO |
| Teamwork, demo & phản biện | 10 | Phân công, PR, script demo, Q&A, bằng chứng phối hợp | TODO |

## 6. Nhật ký AI / Handoff (cập nhật liên tục)
| Thời điểm (Asia/Ho_Chi_Minh) | TV / Agent | Branch | Đã làm / tệp thay đổi | Test (PASS/FAIL/NOT_RUN) | PR/Commit | Blocker & việc tiếp theo |
|---|---|---|---|---|---|---|
| 2026-11-07 | Team setup | main | Tạo bộ khung `AGENTS.md`, `AI_TRACKER.md` | NOT_RUN (docs only) | See git history | TV1–TV5 bắt đầu từ đúng branch |

## 7. Nguyên tắc demo
- **P0-A:** Ảnh/GPS → YOLO gợi ý hoặc [] → user sửa → submit PENDING → reviewer quyết định → map refresh.
- **P0-B:** Điểm A/B → Google Routes trả tuyến → PostGIS risk scorer → hiển thị giải thích theo nguy cơ ghi nhận.
- Chỉ tick DONE nếu thực sự demo/test được. Mock data phải có nhãn; không giới thiệu demo mock như backend thật.
- Mọi thông tin còn chưa biết phải được ghi rõ `UNKNOWN` hoặc `UNVERIFIED`, không điền ước lượng như số đo thật.


## Full-release integration — 2026-11-07 (supersedes scaffold status above)

| Task | Owners | Status | Evidence / next gate |
|---|---|---|---|
| Release reconciliation | TV1–TV5 | DONE_VERIFIED | 3b70333; backend source tree restored; frontend preserved; docs/release/MERGE_PROVENANCE.md |
| Android HTTP integration | TV1/TV2 | DONE_VERIFIED | Shared client; 63 JVM tests/build/lint; 8 device regressions; HTTP report/review E2E PASS; provider gates separate |
| Real backend re-verification | TV3–TV5 | DONE_VERIFIED | 179 tests + 12 subtests PASS; migration/PostGIS; Docker restart; AI CPU; contract; docs/release evidence |
| Android report/review E2E | TV1–TV5 | DONE_VERIFIED | Actual Android/API/PostGIS/AI, vote/review/audit/map data and restart persistence PASS; docs/release/ANDROID_BACKEND_E2E.md |
| Google provider gates | TV1/TV5 | BLOCKED | Maps Authorization failure; supplied Routes key rejected with API_KEY_ANDROID_APP_BLOCKED (403); backend maps error to 502 |
| Integration CI | TV1–TV5 | DONE_VERIFIED | 373ceb8: Android 42371680002 and backend 42371680053 PASS |
| Main release | Team | BLOCKED | Maps/Routes live and PR review incomplete; main unchanged |

Original dirty TV4 worktree and historical tracker entries are preserved. New
integration evidence is recorded in docs/release, distinct from old owner runs.
