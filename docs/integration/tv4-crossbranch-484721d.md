# TV4 cross-branch verification — 2026-11-07

Result: **83 PASS (78 unit + 5 real PostGIS), 4 strict XFAIL**, no skips.
The four XFAIL cases are reproduced TV5 defects/contract gaps, NOT passes or
an assertion that the entire routing integration is ready.

## Source control and snapshot

Fetched all remotes. Actual heads equal the handoff commits (no diff): TV3
9197a41, TV4 38df244, TV5 484721d. Main working branch remains
feature/tv4-risk-core at 38df244. No commits, pushes, merges or deployments.

New isolated source snapshot: `C:\dev\SafeRoute-AI-tv4-crossbranch-484721d`.
Exported TV3 backend first, then TV5 backend, then only TV4 risk module and its
three test files from 38df244. Overlay only this turn's TV4 adapter/test changes.
Used the existing isolated Python 3.11 environment without package upgrades.

Collisions resolved by ownership:

- TV3 empty routing/schemas.py -> actual TV5 484721d schemas retained.
- TV3 empty test_risk_scoring.py -> TV4 actual tests retained.
- TV3 empty routing placeholders remain inert; no replacement models/session or
  initial migration created.
- Did NOT apply tv5-canonical-imports.patch or copy old snapshot routing source.
- TV5 source uses relative imports, but its tests still use backend.app.*.
  PYTHONPATH points to the snapshot root to run those unchanged tests from backend.
  This enables tests; it does not unify module identities across namespaces.

## TV4 changes and protocol compatibility

Added opt-in `KeyedPostgisRiskAdapter`, a subclass whose evaluate_routes returns
the ID-keyed map and validates exact candidate ID coverage. Original list facade
and scoring.py remain unchanged. No production policy defaults introduced.
TV5's new protocol explicitly supports both list and dict, so the keyed subclass
fits that shape. Dict ordering is irrelevant to the join; output candidate order
and count are preserved. Tests cover 0/1/3 candidates, reversed score-map order,
missing/extra keys at the TV4 boundary, DB exceptions and zero vs unavailable.

RouteCandidate.to_geojson() and TV4 validated conversion produce the same
longitude/latitude LineString. TV4 retains validation of bounds/finite coordinates
and distinct vertices rather than relying on TV5's unvalidated conversion alone.

## Real DB verification

Executed existing verification runner against a fresh disposable container:
PostgreSQL 16.4, PostGIS 3.4.3; image digest
sha256:44126d872ac91993766c341e369c539e8196614321765d36a6f1bab0419a5fa5.

- Alembic upgrade -> rollback -> upgrade: PASS.
- Both schema checks: 8 model tables, 8 FKs, 3 enums, geography POINT/4326,
  idx_reports_location GiST; metadata diff=[] in public application schema.
- Original four real PostGIS tests: PASS.
- Added fifth DB test for keyed output and DRAFT 25m/0.5/2–6 configuration.
  Persisted reports projected 24m and 26m east of the route: 24m included, 26m
  excluded. Base verified=4, pending=2, extra verified at 24m=4 -> score 10;
  rejected/26m reports excluded. Far route remains score zero; IDs/order preserved.
- Tests use synthetic coordinates/policy on real DB, not mocked spatial queries.
- Container stopped and auto-removed; no production DB touched.

Command from main workspace (snapshot root in PYTHONPATH is needed for TV5 tests):

```powershell
$env:PYTHONPATH='C:\dev\SafeRoute-AI-tv4-crossbranch-484721d'
& 'C:\dev\SafeRoute-AI-tv4-integration-20261107\.venv\Scripts\python.exe' `
  scripts/qa/verify_postgis.py `
  --backend 'C:\dev\SafeRoute-AI-tv4-crossbranch-484721d\backend'
```

[Execution evidence](../db/evidence/tv4-crossbranch-484721d.txt):
`83 passed, 4 xfailed, 1 warning, 12 subtests passed in 1.97s`.
Unit breakdown: TV3 6; risk core 16; existing adapter 20; TV5 23;
new passing contract cases 13. Five DB tests are separate from those 78 units.

## Unresolved TV5 behavior — strict XFAIL evidence

Tests live in `backend/tests/test_tv4_crossbranch_contract.py`; xfail(strict=True)
ensures future fixes produce XPASS failures until the expectations are reviewed.
No TV5 module was edited.

| Case | Observed behavior | Required owner decision/fix |
|---|---|---|
| One route, scorer raises/returns unavailable | risk=None but recommendation says no recorded hazards | Distinguish unavailable from evaluated zero |
| Two routes, only first assessed | Chooses assessed route and claims lowest recorded risk across alternatives | Define honest partial-risk recommendation policy; avoid unsupported comparison |
| Known score=1000, another route unavailable | Unknown is ranked as sentinel 999 and recommended | Never rank unknown as a numeric risk; handle partial risk explicitly |
| Extra risk-map key | Unknown key silently ignored | Explicit contract validation/diagnostic; TV4 already rejects extra keys |

Missing map keys now preserve candidates with risk=None rather than truncating:
the old zip cardinality bug is fixed. List scores remain positional and cannot
detect equal-length reordering; prefer keyed facade. DB errors preserve route
geometry with risk=None; they do not create synthetic zero scores. However the
single-route explanation still contradicts that representation.

Source review also shows raw exception text is logged by TV5 when scoring fails.
SQLAlchemy errors can include SQL/parameters (route coordinates); sanitize those
messages before production. This is a potential logging exposure, not a claim
that private data appeared in this synthetic test run.

## Import boundaries and TV3 handoff

Separate probe combining `backend.app.modules.routing.service.RoutingService`
with TV4's app.* adapter produced Pydantic ValidationError on EvaluatedRoute.risk:
the RouteRiskScore instance belongs to a different module identity. Relative
imports keep TV5 internally consistent but do not remove dual namespaces.
All passing cross-contract tests use app.* on both sides. Owners should select
one import root (app.* matches TV3 startup) and align entry points/tests; do not
apply the obsolete old import patch to new TV5 code.

TV3 standalone entity/adapter circular import remains a previously reproduced
blocker; tests initialize app.db.session first like the real TV3 startup path.
No owner change was made. Existing create_all/Alembic, fixed development secret/
seed accounts, public uploads, CORS and config consistency issues remain open.

## Production policy gate

| Item | Status | What is verified / missing |
|---|---|---|
| Distance unit, SRID, lon/lat | CONFIRMED mechanics | Real geography ST_DWithin in meters; SRID 4326 |
| Radius 25m | DRAFT | Boundary fixture passes; no joint production approval |
| Pending multiplier 0.5 | DRAFT | Pending contributes half its configured weight; counts remain unverified |
| Thresholds medium=2, high=6 | DRAFT | Boundary scores 2/6 tested; no empirical/owner approval |
| severity_weights | DRAFT | Explicit nonnegative finite nonempty map required; unknown severity fails, not zero. HIGH=4 is a test value, not approved policy; LOW/MEDIUM and category-based DB seeds unresolved |
| Rejected | CONFIRMED business rule | Never contributes score/count |
| Count semantics | DRAFT | Distinct verified + pending nearby IDs; rejected excluded; pending count independent of its score multiplier |
| Duplicates | DRAFT policy / verified mechanics | Once per report ID per route; shared report counts on each route, conflicts fail; no dedup of different report IDs |
| Zero vs unavailable | CONFIRMED distinction | Zero is evaluated recorded-risk sum; None is unavailable. Recommendation implementation still has TV5 gaps |
| Keyed mapping | Compatible source interface | TV4 subclass returns dict supported by TV5; public response/error policy still needs approval |

No seed values or proposed constants were promoted to production defaults.
Zero weighted score need not mean zero hazard count (e.g. a zero severity weight
or excluded pending); consumer messaging must use counts and availability too.

## Safe PR integration order (proposal only)

1. TV3 foundation/schema PR, reviewing migration/session/import/security fixes and
   confirming ownership; no duplicate initial migration or blind Alembic stamp.
2. TV5 routing PR based on 484721d plus recommendation/validation fixes, with a
   single import namespace and the failing contract probes promoted to passing.
3. TV4 risk-core/adapter PR based on 38df244 plus this keyed facade and tests;
   jointly approve an explicit versioned production policy before wiring it.
4. TV3 API-wiring integration PR consuming agreed TV4/TV5 interfaces; rerun the
   full real-DB suite, error paths and HTTP/auth checks on the combined commit.
5. Cloud readiness work only after private storage, secrets, RBAC, runtime/resource
   configuration and external health checks are verified.

DB integration is verified for these pinned sources plus this turn's TV4 changes.
API integration is conditional, not complete: endpoint wiring and production
policy await owners; recommendation/import defects remain. Cloud and live Google
Routes/E2E were NOT_RUN. No PR was opened, committed, pushed or merged this turn.
