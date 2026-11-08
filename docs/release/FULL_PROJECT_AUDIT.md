# Full project audit — 2026-11-07

## Provenance and scope

See MERGE_PROVENANCE.md. Worktree isolates all changes from dirty TV4 source.
TV1 owns map/routing/shell; TV2 owns shared Android network/auth/report/review;
TV3 owns API/migrations; TV4 owns approved YOLO runtime; TV5 owns routes/cloud/CI
under the root AGENTS mapping. This release task authorizes cross-module fixes.
Contract revision tv345-contract-2026-11-07 remains generated from FastAPI.

## Baseline findings

- Frontend e096f40 contains native Java/XML views, lifecycle state and synthetic
  test fixtures, but default TV1DataSource returns NOT_CONNECTED. Auth submits,
  report submit, my reports, community and reviewer actions lack HTTP integration.
- DTO fallback ID/date/address/model metadata invent facts. Private image URLs
  require Bearer. Existing polyline decoder can be reused.
- Backend history was merged then reverted in frontend. Git ancestry alone is
  insufficient; selective reconciliation restores implementations and evidence.
- Backend has actual migrations 0001/0002, RBAC, durable images, normalized AI,
  Google adapter, policy-aware PostGIS scorer, tests and Docker runtime.
- No production numeric risk policy approval. No cloud deployment is authorized.

## Changes and compatibility

Single Retrofit/OkHttp/Gson client now drives existing TV1 boundary and TV2 views.
Request origin excludes API suffix; tokens cannot follow redirects/cross origins.
No HTTP in release manifest. Keystore encrypted token, memory-only fallback when
Keystore unavailable, /auth/me on restore, no refresh/password persistence.
Model metadata no longer fabricates values. Report ID remains String in Android
and Gson reads the numeric API ID. UI labels missing metadata explicitly.
Routes preserve IDs/order/count and nullable risk; INVALID clears scores and
PARTIAL never receives a lowest-risk claim. Unknown counts do not become zero.
Photo bytes stream on worker threads with a 5 MiB cap. Manual AI fallback and
GPS coordinates remain usable; boxes validate normalized bounds and aspect fit.
Creation clears draft only after a server response with a real ID; uncertain
writes are not retried blindly. Review/votes reload authoritative server state.

## Security review

- No role grant from registration input; role UI is presentation and DB RBAC stays authoritative.
- Persisted role is not trusted on process restore; /auth/me resolves current role.
- No token logging, URL token, public image cache or cross-origin image requests.
- Writes have retryOnConnectionFailure(false), redirects disabled, bounded timeout.
- Private image decoding uses a bounded buffer off UI thread; 403 retains placeholder.
- New files contain only explicit disposable test account inputs, never production credentials.
- Cloud and risk approval are separate from integrated demo readiness.

## Backlog / release gates

P0: correct Maps provider authorization and supply a separate Routes server key.
Android/backend UI flow and regression CI passed at 7c167eb. Main merge blocked until
all mandatory gates and GitHub review requirements pass.
P1: exhaustive offline/rotation/permission/accessibility matrix and real photo
camera/picker testing; image and draft recovery across all OEM process behavior.
P2: unsupported admin users/metrics/password reset/social login stay explicitly unavailable.
No source test assertion has been removed. Disconnected TV1 regression tests now
inject the disconnected source explicitly because production uses HTTP.

## Inventory

Java source files: 47; JVM test files: 6; device test files: 5; backend test files: 15.

- `frontend/app/src/test/java/com/comeforfood/saferoute/ApiContractTest.java`
- `frontend/app/src/test/java/com/comeforfood/saferoute/AuthDtoTest.java`
- `frontend/app/src/test/java/com/comeforfood/saferoute/HtmlFlowTest.java`
- `frontend/app/src/test/java/com/comeforfood/saferoute/RouteAndHazardTest.java`
- `frontend/app/src/test/java/com/comeforfood/saferoute/MapRoutingUiTest.java`
- `frontend/app/src/test/java/com/comeforfood/saferoute/AccountReportIntegrationTest.java`
- `frontend/app/src/androidTest/java/com/comeforfood/saferoute/AddressDeviceTest.java`
- `frontend/app/src/androidTest/java/com/comeforfood/saferoute/HtmlFlowDeviceTest.java`
- `frontend/app/src/androidTest/java/com/comeforfood/saferoute/LiveBackendDeviceTest.java`
- `frontend/app/src/androidTest/java/com/comeforfood/saferoute/MapRoutingDeviceTest.java`
- `frontend/app/src/androidTest/java/com/comeforfood/saferoute/AccountReportDeviceTest.java`
- `backend/tests/test_ai_api.py`
- `backend/tests/test_auth_rbac.py`
- `backend/tests/test_health.py`
- `backend/tests/test_models.py`
- `backend/tests/test_reports.py`
- `backend/tests/test_risk_postgis_adapter.py`
- `backend/tests/test_risk_postgis_live.py`
- `backend/tests/test_risk_scoring.py`
- `backend/tests/test_routing.py`
- `backend/tests/test_routing_api.py`
- `backend/tests/test_runtime_config.py`
- `backend/tests/test_tv345_integration.py`
- `backend/tests/test_tv4_crossbranch_contract.py`
- `backend/tests/test_tv4_final_integration_gate.py`
- `backend/tests/test_votes_review.py`
