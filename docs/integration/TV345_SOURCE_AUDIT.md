# TV345 source audit — 2026-11-07

Integration branch: `integration/tv345-backend-handoff`, separate worktree
`C:/dev/SafeRoute-AI-tv345-handoff`. Original dirty worktree untouched; every changed
and untracked TV4 file copied to `C:/dev/SafeRoute-AI-tv345-local-backup` and included
in integration commit 702af0e. No reset, clean, source push or Android edit.

## Fetched heads

| Source | SHA |
|---|---|
| TV03 | ac607a24c35b6b8739fdb1cc97186003b6aeef04 |
| feature/tv4-risk-core | 38df244da6a3b0cf8ce2e627b13d538bfde6e83f |
| feature/tv5-routes-adapter | b5c81b4ecf5298ec6dcd8923d4abb848260092ac |
| frontend (read only) | d76153b |
| feature/tv2-report-community (read only) | 384e1bf |

All requested source heads matched after fetch. Relative to prior local remote refs,
TV3 advanced c81aaeb→ac607a2 (HTTP routing, inference error propagation, static guard),
TV5 d5596b2→b5c81b4 (INVALID, cardinality, zero-score semantics).
Merge order main→TV03→TV4→TV5. Actual merge conflicts were routing/service.py and
routing tests; owner TV5 versions selected. TV3 HTTP router retained; TV4 risk core
and keyed facade retained. No obsolete import patch applied.

## Ownership and duplicate modules

| Owner | Authoritative scope | Shared impact |
|---|---|---|
| TV3 | Auth/reports/images/votes/review/AI HTTP/OpenAPI | TV2 DTO and image authorization |
| TV4 | DB/models/migrations/PostGIS/risk/Docker/cloud/CI | TV3 models and TV5 scorer interface |
| TV5 | Google adapter/routing/geometry/recommendation | TV1 routing models |

TV3 included copies of risk and routing. Risk core matched TV4; TV5 service was
newer. Empty geometry/google_routes/risk_scoring scaffold files are not completed
features; actual implementations are adapter.py/polyline.py/service.py. One
SQLAlchemy Base/session, one initial migration, one public API router retained.

## Findings at source baseline

- P0: JWT lookup failure/missing user manufactured authenticated users from claims.
- P0: auth/report/vote/review DB exceptions returned fake in-memory success.
- P0: upload files were public despite UUID validation; no persisted upload owner.
- P0: report image URL and MIME guessed JPEG; attachment committed separately.
- P0: risk router imported nonexistent defaults and wrong RiskPolicy parameters,
  swallowed configuration errors, then silently used ETA or Google fixture.
- P0: create_all and implicit development seed bypassed Alembic.
- P1: AI bbox pixels versus Android normalized xyxy; full_name and confirmed
  detections ignored; audit and timestamp aliases absent.
- P1: Docker context omitted approved checkpoint; AI dependencies absent.
- P1: test JWT subjects were not persisted users; prior static-public and implicit
  mock expectations contradicted requested security contract.
- P1: production policy not approved; category risk_weights cannot be silently
  interpreted as severity weights.
- P2: cloud credentials, Google live call, evaluation dataset/provenance absent.

## Test inventory and interpretation

TV3 HTTP tests, TV4 scorer/adapter/live spatial tests, TV5 routing/geometry/provider
regressions and preserved TV4 cross-branch probes are combined, not added from
historical counts. Historical 127/12 is not current evidence. Run all tests against
one disposable PostGIS DB, with --runxfail, and retain sanitized evidence.

Contract changes in historical probes are explicit: no routes raises 404;
PARTIAL recommends ETA with caveat (not lowest risk); INVALID clears every score
rather than raising a service ValueError. Public static-image assertions become
401/403 authenticated-image assertions plus static 404. AI HTTP unit tests inject
model outputs explicitly instead of expecting filename-based heuristic detection.
No regression is hidden by xfail or removed to obtain green results.

Android DTOs inspected read-only: AuthDto, DetectionDto, ReportDto, Route,
RouteComparison and Tv1DataSource. TV1 boundary is a UI interface, not an existing
HTTP contract. No shared Retrofit implementation was found in inspected branch
paths; integration mapping remains TV1/TV2 work, documented in handoff.

## Late source HEAD reconciliation

Final fetch found TV03 1349d2a4713e3dab2f979011d1b3309ea11667d8, a single
tracker-only commit after ac607a2. Diff: AI_TRACKER.md only, 12 insertions and
10 deletions; no runtime/source/test changes. Merged with a real merge commit;
retained both TV3 and TV4 journal entries in the sole conflict. Source freeze
for final delivery uses 1349d2a / 38df244 / b5c81b4. Historical TV3 75-test claims
are not added to current integration counts or treated as production approval.
