# Final cross-branch integration gate — 2026-11-07

**Readiness: BLOCKED.** Core DB/spatial integration works, but complete HTTP
integration, source regressions and security/runtime gates are not satisfied.
No commit, push, merge, source-branch switch or deployment performed.

## Sources and preservation

Fetched all remotes; heads match requested commits:

- TV3: `c81aaeb5c358f27676ef588e5c347329d555e267`.
- TV4: `38df244da6a3b0cf8ce2e627b13d538bfde6e83f` plus explicitly retained,
  uncommitted TV4 keyed facade and contract/live tests from the prior task.
- TV5: `d5596b27b9486ab4099877ddbd67c9b6b903e72f`.

Main working branch remains feature/tv4-risk-core at 38df244. New isolated
snapshot: `C:\dev\SafeRoute-AI-final-c81aaeb-d5596b2`.
Exported TV3 backend/OpenAPI, then only TV5 routing source/tests, then TV4 risk
source/tests; overlaid TV4 local keyed facade/tests and the new final-gate tests.
No old import patch applied. No TV3/TV5 source edited.

Conflicts resolved by ownership: preserve TV5 populated routing/schemas.py over
TV3 empty scaffold; preserve TV4 test_risk_scoring.py over TV3 empty file; retain
TV3 models/session/migration/API router. Empty TV3 routing/router.py remains
empty, not falsely treated as a wired endpoint.

Imports: TV5 source/tests now use a canonical app.* family; TV3 removed eager
init_db import from db/__init__.py. Standalone TV4 adapter import PASS. No previous
mixed-namespace workaround/patch was needed for the new sources.

Dependency: email-validator was newly required by TV3 and missing from the
isolated environment. Installed email-validator 2.3.0 + dnspython 2.9.2 within
TV3's declared range. No app source or existing dependency versions upgraded.
Ultralytics is absent; no inference/model deserialization was performed.

## Execution and regression results

Used a fresh disposable PostgreSQL 16.4/PostGIS 3.4.3 container per run, bound
only to loopback with random credentials. All containers were stopped/auto-removed
in finally, including failing-suite runs. No production DB or Compose seed used.

The verification runner now supports --runxfail. This executes historical strict
XFAIL assertions normally without deleting decorators or changing assertions.
It also redacts JWTs in captured test-failure logs (synthetic test credentials).

1. Source-default run: **126 PASS / 13 FAIL**, zero XFAIL/skips.
   [Evidence](../db/evidence/tv4-final-gate.txt).
2. Explicit checkpoint path: **127 PASS / 12 FAIL**, zero XFAIL/skips;
   12 subtests PASS, one dependency deprecation warning.
   [Evidence](../db/evidence/tv4-final-gate-configured.txt).

Configured run only set YOLO_WEIGHTS_PATH to the existing repository checkpoint.
SHA256 was verified as
`ab42e303396857560995dd0fd4d54262015ef77851709220c2f20ca5fc6a4f4c`.
The checksum test then passed; this is NOT proof of live YOLO inference or
checkpoint availability inside a backend image.

| Suite / gate | Configured result |
|---|---|
| TV3 original health/models/Auth/Reports/Images/Votes/Review/AI tests | 32 PASS / 3 FAIL |
| TV4 unchanged risk core | 16 PASS |
| TV4 existing adapter unit tests | 20 PASS |
| TV4 original cross-branch probes including old XFAIL assertions | 14 PASS / 3 FAIL |
| TV5 full current routing suite | 33 PASS |
| TV4 real spatial tests | 5 PASS |
| New final HTTP/routing/security probes | 7 PASS / 6 FAIL |
| Total | 127 PASS / 12 FAIL; no hidden XFAIL |

TV3's three source-test failures are vote/duplicate prevention, TV2 vote_type and
reviewer verification: expected 201/200 but got 404. These tests use hardcoded JWT
subjects without provisioning corresponding DB users. Report creation can return
201 from its memory fallback after a DB FK failure; later handlers query the real
DB and fail to find that report. Owners must fix fixtures AND avoid silent DB-error
fallback in production, not treat these tests as a live workflow PASS.

## Real PostGIS and migrations

Both runs: upgrade head -> inspect schema -> downgrade base -> inspect removal ->
upgrade head -> inspect schema **PASS**. Verified 8 model tables, 8 FKs, 3 enum
types/labels, geography(Point,4326), idx_reports_location GiST. Alembic metadata
diff=[] for public application schema; extension-owned tables excluded.

All five real spatial tests PASS: near/far/parallel routes, persisted report
statuses, repeated segments/shared hazard dedup, lon/lat and SRID, meter offsets,
and DRAFT 25m radius inclusion at 24m/exclusion at 26m. No SQLite/mock query fallback.
Migration source was unchanged; no competing schema created.

## Real HTTP integration

**Flow A: partial success, overall FAIL (image contract).** FastAPI TestClient
used real authentication and the real PostGIS session (dependency injection only
selects the disposable connection). Verified each DB row rather than trusting 2xx:

- Register/login -> actual persisted User.
- Upload real PNG bytes -> image_id/url.
- Create Report -> actual Report and ReportImage, PENDING_VERIFICATION.
- Vote -> actual CommunityVote; USER review attempt denied 403.
- Reviewer account provisioned as a test fixture -> login -> verify -> actual
  VERIFIED state and ReviewLog; GET detail returns VERIFIED.
- Final image linkage assertion FAIL: upload URL ends in .png, report URL
  invents .jpg. Stored report-image MIME is also hardcoded image/jpeg in source.

No Auth dependency was mocked; test fixture emails/passwords are synthetic.

**Flow B:** fixture Google candidates -> real PostGIS -> TV4 keyed scorer ->
real TV5 RoutingService -> RouteCompareResponse **PASS**. Scores near/far = 6/0
under explicit DRAFT weights; order/IDs preserved, assessment AVAILABLE.
**Public HTTP step BLOCKED/FAIL:** `/api/v1/routes/compare` is absent from runtime
FastAPI OpenAPI and the exported TV3 OpenAPI; routing/router.py is empty. Did not
invent or mount a substitute endpoint. TV3/TV5 must agree and wire the endpoint.
Google provider was MOCK_FOR_DEV; no live Google Routes call claimed.

## Routing contract and historical tests

Confirmed working: keyed route ID mapping, stable nonempty candidate count/order,
AVAILABLE, ordinary PARTIAL, DB-error UNAVAILABLE, extra-key INVALID when some
candidate scores are present, zero vs None representation, no sentinel 999.

Historical four XFAIL assertions, run with --runxfail:

- Single unavailable explanation: now PASS; TV5 fixed the misleading text.
- Known score >999 vs unavailable: now PASS; sentinel removed.
- Partial risk must have no recommendation: still FAIL, because new contract
  deliberately returns an ETA-only recommendation with an explicit partial-risk
  caveat. This is a contract difference, not proof of the former safety claim bug.
- Extra keys must raise ValueError: still FAIL, because new contract deliberately
  returns INVALID + ETA-only instead. Requires owner agreement on error surface.
- Additional old cardinality test for zero candidates fails: new service raises
  NoRouteFoundError rather than returning routes=[]. No fake route is generated;
  owners must settle HTTP/status semantics.

New reproducible TV5 defects (not patched):

1. Dict with only extra keys sets INVALID then consolidation overwrites it with
   UNAVAILABLE when scored_count=0. Preserve INVALID independently of coverage.
2. List with 3 scores for 2 candidates is truncated; status becomes AVAILABLE,
   permitting risk comparison. Treat extra list items as explicit invalid output.
3. Score=0 with hazards_count=2 says no hazards recorded. Zero weighted sum is not
   zero reports. Use hazard counts/policy semantics in explanations.

Full/current TV5 source tests pass but do not cover all these combinations.
Additional source concern: scorer exceptions are logged verbatim; DB exceptions
can include SQL/coordinate parameters. Sanitization is still required.

## Security / infrastructure gate

**Improved and verified:** production default JWT secret rejected on fresh import;
random explicit secret accepted. Standalone model/adapter circular import fixed.
Production login's demo-user fallback and init_db demo-account seeding are guarded
in source. These improvements do not make all fallback paths safe.

**Blocking TV3 findings:**

- Inference fault injection with a ready/checksum-valid model raises internally,
  but predict catches it and returns detections=[] under the real model version.
  New assertion expecting explicit HTTPException FAIL. This is not a valid
  no-detection result and must not be represented as successful inference.
- Registration catches DB errors and returns an issued token/user_id=999;
  reports catch DB failures and return in-memory success without production guard.
  get_current_user reconstructs users/roles from a signed token when DB lookup
  fails or user is missing. Require explicit failure instead of phantom persistence.
- /uploads remains a public StaticFiles mount; public image/location access policy
  not resolved. CORS still wildcard with credentials.
- init_db still calls create_all; Compose invokes init_db rather than Alembic.
  Fixed development credentials/source mount/reload remain in Compose.
- AI default weights path resolves under backend/ai/weights, while supplied
  checkpoint is repository-root ai/weights. Backend Docker build context lacks
  checkpoint; requirements lack Ultralytics/PyTorch. Explicit host path only fixed
  checksum testing. Do not infer runtime container has a ready model.
- Dockerfile build/runtime NOT_RUN in this task; static context/runtime blockers
  above remain. No backend image smoke PASS claimed. PostGIS image did run.
- Cloud Run persistent private photo storage, resource limits, model packaging,
  migration startup and external health remain NOT_RUN/BLOCKED; local uploads
  filesystem is not evidence of durable cloud storage.

## Risk policy gate

All remain **DRAFT** pending TV3/TV4/TV5 approval: radius=25m; pending multiplier
0.5; LOW<2, MEDIUM [2,6), HIGH>=6; verified+pending count semantics; explicit
severity_weights and policy revision. HIGH=4 in tests is only a fixture.

No approved category-to-severity mapping: category_user and severity are separate
fields; TV3 category-based RiskWeight seeds must not silently overwrite user's
severity or TV4 severity policy. Rejected exclusion is confirmed. Once-per-ID-per-
route mechanics are tested; different IDs at one location are not auto-merged.
Policy revision/radius/weights/thresholds are constructor-injected, no production
configuration source or approval record exists. Tests cover invalid/nonfinite/
missing weights and unknown severity errors; neither unknown nor unavailable is
converted to score zero.

## Owner handoff and safe PR order

TV3: fix report image metadata/URL linkage; fail closed on DB persistence/auth
errors; provision DB fixtures in source tests; separate AI failures from valid
empty detections; package approved runtime/checkpoint; private uploads and CORS;
Alembic-owned startup; wire agreed routing API and regenerate OpenAPI.

TV5: retain INVALID for extra-only maps; reject overlong lists; correct zero-score
nonzero-count text; sanitize exception logs; agree empty-candidate and malformed-
score API behavior with TV3. No owner source was edited in this task.

Proposed PR sequence: (1) TV3 foundation/API/security fixes with real DB tests;
(2) TV5 contract edge fixes; (3) TV4 core/keyed adapter/tests plus jointly approved
versioned risk policy; (4) TV3/TV5 routing HTTP wiring and combined E2E PR;
(5) cloud readiness only after runtime/storage/security gates pass.

Next TV4 P0: retain these failing probes, verify owner fixes in a new isolated
snapshot, and rerun real DB + HTTP gates. Do not weaken assertions to green the
suite. Legacy contract tests may be superseded only after an explicit owner
decision, while retaining evidence of changed behavior.

Changes this turn: TV4 final-gate test file, verification runner --runxfail/JWT
redaction, sanitized evidence, this report and TV4 tracker/handoff entries.
Risk Scorer, models, migration and all TV3/TV5 modules unchanged. Test failures
are intentional evidence of an unmet gate; readiness is BLOCKED, not DONE.
