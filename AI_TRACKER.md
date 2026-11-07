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

### TV4 Database / PostGIS — audit 2026-11-07 theo phân công mới

Phân công hiện hành do user xác nhận: TV3 Backend/API/AI integration;
TV4 Database/PostGIS/Alembic/Risk/Cloud; TV5 Google Routes/Routing.
Các dòng task cũ phía trên giữ nguyên để truy vết, không còn là căn cứ phân công
TV4-AI hoặc TV5-Cloud. TV3/integration lead cần rebaseline toàn bộ bảng chung.

| ID | Owner | Hạng mục | Trạng thái | Bằng chứng / blocker |
|---|---|---|---|---|
| TV4-RISK-CORE | TV4 | Risk scorer độc lập và regression | IN_PROGRESS | 16/16 unittest PASS trên Python 3.11.9; MOCK_FOR_DEV; contract DRAFT; xem commit TV4 trong Git history |
| TV4-DB-INTEGRATION | TV4 | Models alignment, Alembic, PostGIS, ST_DWithin -> risk | BLOCKED | Pre-commit rerun: upgrade/rollback/re-upgrade, metadata diff=[], 61 unit + 4 PostGIS PASS. Còn chờ duyệt policy/import/protocol và owner fixes. Xem docs/db/tv4-postgis-verification.md |

Audit và handoff: [DB contract matrix](docs/db/tv4-postgis-contract-audit.md),
[TV4 risk handoff](docs/integration/tv4-risk-handoff.md).
TV3 6/6 đã tái chạy PASS trong snapshot tích hợp sau fetch; không chứng minh DB online.

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
| 2026-11-07 | TV4 / Codex | feature/tv4-risk-core | Audit local, cập nhật DB contract matrix/handoff/tracker; giữ nguyên scorer/tests | PASS 16/16 risk unittest; PostGIS/Alembic/DB integration NOT_RUN | HEAD 7a78359; thay đổi chưa commit | Thiếu source TV3, contract TV3/TV5 và PostGIS test environment; xem docs/db/tv4-postgis-contract-audit.md |

## 7. Nguyên tắc demo

Final integration gate 2026-11-07: TV3 c81aaeb / TV4 38df244 + local TV4 changes /
TV5 d5596b2: configured-checkpoint run 127 PASS / 12 FAIL, --runxfail giữ nguyên
assertions cũ. Migration cycle/metadata PASS, 5 PostGIS PASS; HTTP business flow
ghi DB được nhưng image URL sai; routes/compare chưa gắn. Overall BLOCKED.
Xem [final integration gate](docs/integration/tv4-final-integration-gate.md).
Không commit/push/merge/deploy; không sửa source TV3/TV5.

Cross-branch verification 2026-11-07: snapshot TV3 9197a41 / TV4 38df244 + local
keyed facade / TV5 484721d; 78 unit + 5 PostGIS PASS, 4 strict XFAIL thuộc TV5
(unavailable/partial recommendations, sentinel 999, extra keys). Migration cycle
PASS; metadata diff=[]. Policy 25m/0.5/2–6 vẫn DRAFT. Không commit/push/merge.
Chi tiết: [cross-branch report](docs/integration/tv4-crossbranch-484721d.md).

Pre-commit verification 2026-11-07: user cho phép commit/push riêng nhánh TV4.
Đã rerun 65 tests PASS và migration thật trong snapshot pinned TV3 9197a41 /
TV5 5d65a6c. Remote TV3 hiện bf29861 chưa được tích hợp/test ở đây.
Evidence: docs/db/evidence/tv4-precommit-verification.txt. Không commit snapshot,
không merge main, không thay tiến độ task của TV khác.

Latest verification 2026-11-07: đã khởi động Docker Desktop, dùng container test
riêng PostgreSQL 16.4/PostGIS 3.4.3, xác minh migration và 4 DB tests thật PASS
(65 tests tổng, không skip). Container đã dọn. Không sửa scorer/models/migration
hoặc TV5 service. Contract production còn DRAFT; phát hiện thêm circular import
TV3 khi import adapter trực tiếp; TV5 zip vẫn chưa sửa. Evidence và contract gate:
[real PostGIS verification](docs/db/tv4-postgis-verification.md).

Post-push follow-up 2026-11-07, TV4: đã bảo toàn local, fetch TV3/TV5, dựng
snapshot `C:\dev\SafeRoute-AI-tv4-integration-20261107`; thêm draft PostGIS
adapter và unit/live tests, chuẩn bị import patch cho TV5. 61 UNIT_PASS,
4 DB tests skipped; offline Alembic SQL sinh được nhưng live upgrade/rollback
NOT_RUN. Không commit/push/merge/deploy. Chi tiết và handoff:
[post-push report](docs/integration/tv4-post-push-report.md).
- **P0-A:** Ảnh/GPS → YOLO gợi ý hoặc [] → user sửa → submit PENDING → reviewer quyết định → map refresh.
- **P0-B:** Điểm A/B → Google Routes trả tuyến → PostGIS risk scorer → hiển thị giải thích theo nguy cơ ghi nhận.
- Chỉ tick DONE nếu thực sự demo/test được. Mock data phải có nhãn; không giới thiệu demo mock như backend thật.
- Mọi thông tin còn chưa biết phải được ghi rõ `UNKNOWN` hoặc `UNVERIFIED`, không điền ước lượng như số đo thật.


## TV345 master integration rebaseline — 2026-11-07

Current ownership supersedes historical assignments: TV3 API/Auth/Reports/Images/
Votes/Review/AI HTTP; TV4 DB/PostGIS/Alembic/Risk/Docker/Cloud/CI; TV5 Google Routes/
Routing/Geometry/Recommendation. Android remains TV1/TV2.

| Task | Owner | Status | Evidence |
|---|---|---|---|
| Durable API/private images/security/DTO | TV3 | DONE_VERIFIED | b7c0b87; 178 combined tests PASS; container HTTP/restart PASS |
| PostGIS/migrations/keyed risk | TV4 | DONE_VERIFIED | cycle/data preservation/diff=[]; 5 live spatial + HTTP risk PASS |
| Routing/Google adapter regression | TV5 | DONE_VERIFIED | b5c81b4 retained; unavailable/partial/invalid/zero tested |
| Production risk approval | TV4 + owners | BLOCKED | numeric approval absent; explicit demo profile |
| Docker/real AI CPU | TV3/TV4 | DONE_VERIFIED | build/start/restart/checksum/class mapping/inference PASS |
| Cloud/live Google | TV4/TV5 | BLOCKED | credentials/cost approval absent |
| GitHub CI | TV4 | IN_PROGRESS | workflow authored; remote evidence pending |
| Android device integration | TV1/TV2 | TODO | handoff ready; no Android modifications |

Evidence: docs/integration/evidence/; handoff: docs/handoff/RELEASE_READINESS.md.
Older failure counts/assignments above are historical. Original TV4 workspace is
unchanged; all work uses the separate integration worktree and actual merge history.
