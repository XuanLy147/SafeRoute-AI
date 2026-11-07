# TV4 Database / PostGIS audit — 2026-11-07

Latest status: **real PostGIS verification PASS**; production contract approval
still pending. See [current gate and evidence](tv4-postgis-verification.md):
8 tables, 8 FKs, 3 enums, GiST, metadata diff=[], upgrade/rollback/re-upgrade
and all 4 real DB tests PASS. Pre-fetch matrix below is retained as historical
audit; current CONFIRMED/DRAFT/BLOCKED decisions are in the linked report.

Local branch: `feature/tv4-risk-core`; HEAD: `7a78359`.
Production API integration: **BLOCKED on contract/owner fixes**. No shared
production policy is approved by successful database tests.

## Evidence

Initial status contained only the three existing untracked TV4 artifacts: risk
module, risk tests and handoff. `git diff` was empty because these are untracked.
Their contents were read and preserved. Only one worktree is registered.
`git log --all -1 --oneline -- backend/app/models/entities.py` returned no history
in locally available refs. No remote fetch/inspection was performed; this does
not establish current GitHub state or contents of TV3's machine.

`Test-Path` returned False for all seven reported artifacts:

- `backend/app/main.py`
- `backend/app/core/config.py`
- `backend/app/db/session.py`
- `backend/app/db/geo.py`
- `backend/app/db/init_db.py`
- `backend/app/models/entities.py`
- `docs/api/openapi.json`

TV3's 6/6 pytest report is USER-REPORTED / NOT REPRODUCED HERE. No dependency
manifest, Alembic setup or executable routing adapter was found. No replacement
entities, schema, DB session or migration were created.

## Contract matrix

CONFIRMED means confirmed by user instructions, not checked against missing
models. DRAFT means TV4 behavior/proposal awaiting recipient review.

| Area | Classification | Evidence / alignment needed | Owner |
|---|---|---|---|
| Ownership | CONFIRMED | TV3 backend/API/AI; TV4 DB/PostGIS/Alembic/risk/cloud; TV5 Routes | User |
| Governance text | CONFLICT | README, AGENTS, DB notes, migration README and historical tracker rows retain former assignment; current user assignment takes precedence | TV3/integration lead |
| Report ID | BLOCKED | Scorer expects nonempty string; actual PK type and canonical conversion unknown; do not assume UUID/int/string | TV3 + TV4 |
| Status semantics | CONFIRMED | PENDING_VERIFICATION / VERIFIED / REJECTED; votes cannot verify | TV3 + TV4 |
| Status persistence | BLOCKED | SQL enum/string type, defaults, nullability and mapping unknown | TV3 + TV4 |
| Severity / weight | DRAFT | Scorer uses explicit nonnegative weights keyed by string severity. Test low/high are synthetic, not approved DB enums; no production defaults | TV4 + TV3/TV5 |
| Severity field | BLOCKED | Need actual type/bounds/null policy and user-confirmed source; confidence must not substitute | TV3 + TV4 |
| GPS axis order | CONFIRMED | PostGIS/GeoJSON longitude, latitude; Android latitude, longitude | TV3 + TV4 + TV5 |
| GPS fields | BLOCKED | Column names, bounds, nullability and source precedence unknown | TV3 + TV4 |
| Relationships / FKs | BLOCKED | Need actual metadata, ownership/review/image/vote keys, delete rules and uniqueness | TV3 + TV4 |
| Spatial representation | DRAFT | WGS84 EPSG:4326 and meter-based geography query proposed; storage type and index expression unverified | TV4 + TV3 |
| Route geometry | BLOCKED | Need TV5 format, encoding precision/decoder, axis order, candidate ID/order and empty/invalid geometry rules | TV5 + TV4 |
| Rejected policy | CONFIRMED | Never contributes as valid hazard; scorer excludes it | TV3 + TV4 |
| Pending policy | DRAFT | Explicit multiplier [0,1]; zero excludes; positive contribution preserves unverified status. Production multiplier not approved | TV4 + TV3/TV5 |
| Anti-duplicate | DRAFT | Same report ID once per route; conflicting snapshots fail; shared reports count per affected route. Distinct IDs at same location are not merged | TV4 + TV3/TV5 |
| Output / explanation | DRAFT | Sum of configured contributions, revision, report/status/severity/weight/multiplier and excluded counts. Public DTO, units and tie/recommendation rules unapproved | TV4 + TV3/TV5 |
| Session / transaction | BLOCKED | Reuse actual TV3 metadata/session; need sync/async mode, lifecycle and consistent snapshot semantics | TV3 + TV4 |
| Alembic vs init_db | BLOCKED | Inspect init_db before resolving competing create_all/migration paths | TV3 + TV4 |

No field-level incompatibility with TV3 is asserted without models. The observed
conflict is stale governance text, not a proven implementation conflict.

## Independent preparation: implementation sequence

1. Receive TV3 source at an identifiable revision, dependency/runtime versions,
   tests, generated OpenAPI and configuration example without secrets. Inspect
   metadata/session/geo/init_db together; resolve matrix before writing adapters.
2. Configure Alembic against existing metadata/configuration, with no second
   Base or DB session. Review extension ownership, enum lifecycle, FK/delete,
   check/unique constraints and downgrade order. Do not drop a shared PostGIS
   extension on rollback without ownership evidence.
3. Start a disposable PostgreSQL/PostGIS test instance on agreed compatible
   versions. Scope destructive rollback tests to that explicit test database.
4. Implement parameterized ST_DWithin with geography operands and radius in
   meters. Do not use a meter radius with angular-degree geometry. GiST index
   must match the queried column/expression, including casts. Exact SQL awaits
   actual model/table/column identifiers; no guessed schema is supplied here.
5. Preserve real candidate IDs/order and empty routes; deduplicate route/report
   join rows, map model values through the approved contract, then call the
   existing scorer. Agree snapshot consistency and exclusion-count semantics:
   filtering rejected/pending in SQL must not silently change reason counts.
6. Execute the real DB matrix below and record versions, commands, revision and
   sanitized results. Synthetic coordinates on real PostGIS are DB fixtures,
   not evidence of live Google Routes.

## Verification matrix

| Test | Required assertion | Result |
|---|---|---|
| Risk regression | Existing suite unchanged | PASS: Python 3.11.9, 16 tests, 0.001s |
| Upgrade / rollback | Empty disposable DB -> head -> base -> head; inspect schema/revision at each stage | NOT_RUN |
| Schema / constraints | Actual types, SRID, nullability, FKs, unique/check constraints; invalid inserts fail | NOT_RUN |
| Spatial index | Inspect GiST definition; EXPLAIN representative query; small-table sequential scan alone is not failure | NOT_RUN |
| Axis order | Asymmetric valid lon/lat fixture; ST_X/ST_Y and expected-location query | NOT_RUN |
| Meter distance | Points at known meter offsets via PostGIS geography; inside/outside with tolerance and boundary semantics | NOT_RUN |
| Rejected / pending | Persist statuses, query, verify approved pending policy and zero rejected contribution | NOT_RUN |
| Duplicate hazards | Repeated joins/segments count once per report/route; distinct IDs follow approved policy | NOT_RUN |
| No hazards | Candidate retained with zero contributions, no invented route | NOT_RUN |
| Parallel routes | Point near only A and point near both; independently assert membership/score | NOT_RUN |
| DB-to-scorer | Persist fixtures -> real ST_DWithin -> entity mapping -> scorer; assert score and every reason | NOT_RUN |
| Snapshot consistency | Concurrent status changes follow agreed transaction behavior; no silent conflict resolution | NOT_RUN |

Regression command from `backend/`:

```powershell
py -3.11 -m unittest discover -s tests -p test_risk_scoring.py -v
```

Risk tests use MOCK_FOR_DEV data; they are not DB tests. No SQLite or mock result
is counted as a PostGIS PASS. TV3 pytest, HTTP, Routes, CI, Cloud and E2E: NOT_RUN.

`docker info --format '{{.ServerVersion}}'` failed: Docker Desktop Linux engine
pipe unavailable. `psql` not found on PATH. No test DB endpoint supplied. These
checks do not establish whether a remote DB exists.

## Handoff / next P0

- TV3: provide seven reported files plus imports, dependency pins, tests,
  sanitized run instructions and revision; confirm model/API decisions, ID/status/
  severity mapping, session ownership and init_db-to-Alembic transition.
- TV5: provide candidate/geometry interface and labeled fixtures, encoding
  precision, IDs/order, radius decisions, empty/error behavior and expected risk
  output. Keep fixtures distinct from live Routes evidence.
- TV4: after receipt and contract alignment, implement migration and ST_DWithin
  adapter on actual models, then execute this matrix on real PostGIS.

Rubric: architecture/implementation (maximum 20 points), owner TV4; only isolated
risk regression is verified. No DB/cloud points or completion claimed.
