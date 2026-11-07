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
| Google provider gates | TV1/TV5 | DONE_VERIFIED | New restricted keys; Maps rendered on API 35; real Android/Docker/Google Routes PASS; evidence/android-live-providers.txt |
| Integration CI | TV1–TV5 | DONE_VERIFIED | eacb0b9: Android 42373508154 and backend 42373508117 PASS |
| Main release | Team | BLOCKED | Provider gates passed; final PR review/merge requirements pending; main unchanged |

Original dirty TV4 worktree and historical tracker entries are preserved. New
integration evidence is recorded in docs/release, distinct from old owner runs.


## Auth/RBAC local follow-up - 2026-11-08

Android auth/session and ADMIN role management implemented on local/main-latest-20261107.
72 JVM tests/build/lint PASS; 196 backend tests + 12 subtests, Ruff, migration round trip and OpenAPI contract PASS.
See docs/integration/auth-rbac-flow.md for scope, evidence and remaining limitations. No push.
Device instrumentation: NOT_VERIFIED (label assertion fixed; retry hung and was stopped). APK install PASS; initial ADMIN bootstrap authorized and audited.


## Physical-device role audit - 2026-11-08

Status: IN_PROGRESS / release acceptance FAIL. Actual Xiaomi UI + local backend audit found 10 issues, including idle GET EOF failures and private-image 403 redirecting USER away from public report detail. Core QA create/vote/verify/reject and ADMIN grant/revoke/history exercised; 27/27 supplemental HTTP permission/state checks PASS. Camera/picker touch remains blocked by INJECT_EVENTS. No production fixes or push in this audit. See docs/qa/DEVICE_ROLE_AUDIT_2026-11-08.md for reproducible findings, limits and cleanup. Test APK build PASS; bridge runner OK (1 harness test), not a blanket business PASS.

## Device audit fixes — 2026-11-08 (supersedes open findings above)

Implemented F01–F10 fixes on local/main-latest-20261107, preserving existing dirty work. Local verification complete; no commit/push and no cloud deployment. See docs/qa/DEVICE_ROLE_FIXES_2026-11-08.md.

- Android: 77 JVM tests PASS; app/test APK build and lint PASS (0 errors). Final APK installed on Xiaomi.
- Backend: 206 tests + 12 subtests, Ruff, migration round trip and OpenAPI contract PASS.
- Device: idle GET recovery, private-image 403, own-vote persistence, reviewer history/duplicates, ADMIN all-status/filter/metrics/config/history, stale-role refresh and live Google routing reason/ETA verified.
- QA reports removed with ownership/prefix guards; original data and roles unchanged. QA session and bridge cleared.
- Limits: configuration is READ-ONLY; duplicates are suggestions, not automatic merges; system audit covers review decisions and role changes. Camera/picker system-touch E2E remains unverified. Not blanket production acceptance.

## Comprehensive regression audit - 2026-11-08

IN_PROGRESS: reproduce lifecycle/concurrency faults before fixing; preserve existing changes, database and roles. No dependency upgrades, schema reset, automatic write retries or push. Require regression tests and a fresh full-suite/device pass; report unverified cases explicitly.

## Comprehensive audit result - 2026-11-08

Local verification complete, not production certification; no commit/push. Fixed R01 ADMIN loading stuck after view recreation, R02 lost history mode, R03 ApiCalls starting writes for destroyed lifecycle owners, R04 demoted reviewer committing after waiting for a report lock. Added 3 JVM regressions and 2 real PostGIS concurrency tests; before-fix failures recorded.

80 JVM tests, 208 backend tests + 12 subtests, build/lint/Ruff/migration/OpenAPI PASS. Isolated real-phone E2E PASS: USER registration, synthetic image -> real AI/upload/report/vote, REVIEWER password login/review/map, live Google Maps/Routes, ADMIN password login/read screens/activity recreation/logout, backend restart persistence. First ActivityScenario attempt stalled and was stopped; test-only physicalDevice lifecycle harness passed the retry. Camera/picker system-touch E2E remains NOT_VERIFIED. Test resources removed, local USB mapping restored; live data and roles not used for mutations. See docs/qa/COMPREHENSIVE_REGRESSION_2026-11-08.md for guardrails, scope and evidence.

## Report draft follow-up - 2026-11-08

IN_PROGRESS: reproduce stale form/photo/AI state after discard or failed reanalysis. Preserve uncertain-write guard, no live data mutations or push.

## Report draft follow-up result - 2026-11-08

Local verification complete; no commit/push. Fixed R05 stale description/category/severity after discard, R06 same-URI photo not reloaded after discard, R07 stale AI editors/model/consent after failed reanalysis, R08 previous image remaining after unreadable replacement. Production scope: ReportWizardFragment only. Four failing reproductions now pass; 84 JVM tests, APK builds and lint PASS. Isolated Xiaomi E2E with USER/REVIEWER/ADMIN, live Maps/Routes and restart persistence PASS. Temporary services removed; real database/accounts not used for mutations. Backend unit suite unchanged and not rerun this round. See docs/qa/REPORT_DRAFT_REGRESSION_2026-11-08.md; camera/picker system gestures and EXIF coverage remain unverified.


## Frontend UI merge - 2026-11-08

Merged origin/frontend 9f49601 locally on integration/frontend-ui-preserve-fixes-20261108 after recovery checkpoint 5830b26. Preserved incoming UI and verified auth/RBAC/report fixes; resolved three conflicts, migrated saved category indices and cancelled stale address lookups on draft discard. 88 Android JVM tests, APK builds/lint, 208 backend tests + 12 subtests, Ruff/migrations/OpenAPI and isolated Xiaomi USER/REVIEWER/ADMIN E2E with live Maps/Routes and restart persistence PASS. New map screenshot inspected. No push; no live database/role mutations or backend service redeployment. Camera/picker gestures and EXIF remain unverified. See docs/integration/FRONTEND_UI_MERGE_2026-11-08.md.
