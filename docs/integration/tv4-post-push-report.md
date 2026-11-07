# TV4 post-push integration P0 — 2026-11-07

Latest result: **real DB verification PASS**, production policy still **DRAFT/BLOCKED**.
See [PostGIS verification and contract gate](../db/tv4-postgis-verification.md):
upgrade -> rollback -> upgrade PASS, metadata diff=[], 61 unit + 4 live DB tests
PASS (65 total). The sections below retain the earlier post-push audit/evidence;
their Docker/DB NOT_RUN statuses are superseded by this follow-up.
No commit, push, merge, cherry-pick or cloud deployment performed.

## Sources and safe integration

- Local branch `feature/tv4-risk-core`, HEAD `7a78359` remained unchanged.
- Fetched TV3 `origin/TV03`: `9197a41374802fc096c43ede926bf5440b13f53d`.
- Fetched TV5 `origin/feature/tv5-routes-adapter`:
  `5d65a6ce64edc139e05817940ef29a6e65540be5`.
- Received code through Git objects; exported each branch's `backend/` via
  `git archive` to `C:\dev\SafeRoute-AI-tv4-integration-20261107` (a separate
  source snapshot, NOT a merged Git branch). TV3 exported first, TV5 second,
  then TV4 risk module/tests copied in. Existing TV4 artifacts were additionally
  backed up in the snapshot's `tv4-preserved/` directory before this work.
- TV4 scorer and original tests retain identical SHA256 before/after:
  `6114c1b9fc82a6d7fb4f9806ab6dfebef84ccbb708a9baf9c8495353167eac3c`
  and `7bbfdd0f462943fac9d2816ceb852bacba3a9cb2b1f0996985716df80eae0430`.
- Main workspace does not yet contain imported TV3/TV5 dependencies; new adapter
  tests must run in the integrated snapshot, not this scaffold alone.

Compared complete changed-path inventories against HEAD. Inspected TV3 entities,
session, geo, init_db, config, main, API router, migration/env, dependency manifests,
Dockerfile/Compose and health/model tests; inspected TV5 schemas, adapter, service,
polyline, package imports and routing tests. Only root AGENTS exists in TV3 tree.

## Collisions and import errors

| Path / boundary | Finding / resolution in snapshot |
|---|---|
| routing/schemas.py | TV3 zero-byte scaffold vs real TV5 schema; preserved TV5 implementation |
| tests/test_risk_scoring.py | TV3 zero-byte scaffold vs TV4's 16 tests; preserved TV4 implementation |
| routing geometry/google_routes/risk_scoring/router | TV3 zero-byte placeholders; not functioning replacements for TV5 adapter/service/polyline |
| app.* vs backend.app.* | TV3 uses app.*, TV5 uses backend.app.*; normal backend-cwd test collection fails with ModuleNotFoundError: backend |
| TV5 list results | service uses zip(candidates, risk_scores), silently truncating missing results and assigning reordered results positionally |

Original TV5 tests passed from snapshot root (19 tests), but failed from backend
cwd. Mixed imports can also create separate Python/Pydantic class identities.
The proposal `proposals/tv5-canonical-imports.patch` normalizes TV5 imports/tests
to `app.*`. Applied ONLY to the snapshot; TV5 approval required before integration.
The stored proposal uses zero-context hunks to avoid whitespace-only context
lines; apply with `git apply --unidiff-zero` after owner review. Its reverse
application was checked against the tested snapshot without modifying files.
It changes adapter.py, service.py, routing/__init__.py and test_routing.py imports,
not their business logic.

Reproduced service bug with one candidate and scorer returning []: service
returned **0 routes**. New TV4 adapter validates its own ID set, but does not fix
arbitrary providers passed to TV5 service. TV5 must validate exact ID coverage and
adopt an ID-bearing result protocol. No false claim that this generic bug is fixed.

## DB review of actual TV3 implementation

- Eight mapped tables: users, reports, report_images, ai_predictions,
  ai_detections, community_votes, review_logs, risk_weights. SQLAlchemy mapper
  configuration succeeded without a database connection.
- Report ID is Integer; adapter explicitly maps it to a decimal string for TV4.
  Status is SQLAlchemy ReportStatus enum with the three expected values.
  Severity is unconstrained String(20), Python default MEDIUM; no approved
  severity-weight policy follows from that default.
- Report.location is non-null Geography POINT/4326; metadata and migration use
  `idx_reports_location` GiST. Migration disables the type's automatic spatial
  index and creates that index explicitly; no duplicate spatial index introduced.
- Report images/predictions/votes/review logs have report relationships and
  delete-orphan cascade; detections belong to predictions. Report owner FK and
  user vote FK cascade deletes; review actor FK restricts deletion.
- Unique email, unique image object_key and unique (report_id,user_id) vote are
  present. Review logs are described as append-only, but neither model nor
  migration enforces immutability; report deletion cascades into audit logs.
  Retention/deletion policy requires TV3 review, not an invented TV4 rule.
- Missing DB validation includes severity policy, nonnegative finite risk weights,
  risk-weight key uniqueness, confidence range, bbox structure and positive image
  dimensions. These need approved semantics before adding constraints.
- make_point uses longitude/latitude correctly but does not validate bounds.
  point_to_coords silently returns (0,0) on invalid data: handoff to TV3 to replace
  ambiguous fallback with an explicit missing/error contract.
- Initial migration was compared with metadata: table/column types, nullability,
  foreign keys, declared unique constraints and indexes appear aligned on review.
  Python-side defaults are not server defaults in either DDL. This is a static
  review, NOT a live Alembic autogenerate/check or schema-parity PASS.
- Offline SQL emits one definition per enum and a GiST index. Downgrade drops
  dependent tables before parents, then enum types; it retains PostGIS extension.
  No proven migration defect warranted editing the initial migration in this task.
- init_db creates tables via create_all without stamping Alembic. Compose invokes
  it at startup; subsequent `alembic upgrade head` against that DB can collide
  with existing tables. Choose Alembic as schema owner; baseline existing DBs only
  after schema verification. Never blindly stamp an unknown DB.
- Session reads os.environ directly while Settings loads .env. App and Alembic
  can therefore target different URLs when configuration exists only in .env.
  TV3 must unify configuration before deployment.

## Draft adapter contract and implementation

New `backend/app/modules/risk/postgis_adapter.py` reuses TV3 Report and injected
session; it creates no Base, engine, table or migration. No public API wired.

RouteCandidate.coordinates (lat,lon) -> validated GeoJSON [lon,lat] -> geography
LINESTRING/4326 -> ST_DWithin(Report.location, route_geography, radius_m) ->
NearbyReport -> unchanged score_routes -> TV5 RouteRiskScore.

- One UNION ALL SQL statement gives all candidates one statement snapshot.
  Query is parameterized; geography operands use meters, location stays unwrapped
  for its GiST index. SQL compilation is tested; execution is not yet verified.
- Empty candidates avoid DB access. Empty-hazard candidates remain in output.
  All query results are grouped by route ID, then returned in original order.
  Duplicate candidate IDs, invalid geometry, unknown result IDs, missing results,
  conflicting report snapshots and database failures raise explicitly.
- Identical report IDs deduplicate once per route; shared hazards affect each
  nearby route. Distinct IDs at same location are not heuristically merged.
- Rejected never contributes. Caller must supply RiskPolicy, pending multiplier,
  radius and medium/high thresholds. No default seed-weight conversion occurs.
- Proposed count semantics: hazards_count = verified + pending nearby distinct
  reports (including pending excluded from scoring). Rejected excluded from count.
  Reason states revision, counts, pending multiplier, rejected exclusion and
  recorded-data limitation. Full per-report contributions stay in core output;
  current TV5 DTO has no structured fields for them.
- evaluate_by_id is the proposed robust boundary. evaluate_routes is the current
  TV5 positional facade, reordering validated results by candidate ID internally.

CONFIRMED from source: integer report ID, status enum, geography representation,
candidate coordinate order and TV5 output field names. DRAFT/owner approval:
severity-only policy versus category-specific RiskWeight rows, pending multiplier,
radius, thresholds, count meanings, structured reasons and keyed scorer protocol.
TV3's seeded Repair/category verification weights must not silently become this
policy. The adapter is executable for explicit test policies, not production-ready.

## Verification evidence

Environment: isolated Python 3.11 venv installed TV3 requirements ranges plus
requests (declared in pyproject but missing from requirements). No existing
project environment was upgraded. Resolved versions recorded in snapshot
`resolved-test-environment.txt`; dependency ranges are not reproducible locks.

| Check | Result |
|---|---|
| TV3 health/model | UNIT_PASS 6; health returning 200 can be degraded, not proof DB is connected |
| TV4 unchanged scorer | UNIT_PASS 16 |
| TV5 original tests from snapshot root | UNIT_PASS 19; mocked Routes responses, no live Google call |
| Original backend-cwd routing/adapter collection | FAIL: backend import unavailable |
| Draft imports patch + complete snapshot suite | UNIT_PASS 61; 4 live DB tests skipped; one dependency deprecation warning |
| New adapter tests within 61 | UNIT_PASS 20; MOCK_FOR_DEV repository rows, SQL compilation, real TV5 DTO/service classes |
| Alembic offline upgrade/downgrade SQL | Generated successfully; NOT a database upgrade/rollback PASS |
| PostGIS spatial tests, DB migration lifecycle, live metadata parity | NOT_RUN / BLOCKED |
| Google Routes live, cloud, E2E | NOT_RUN |

After applying the imports proposal and copying new TV4 files, from snapshot backend:

```powershell
..\.venv\Scripts\python.exe -m pytest tests -q
..\.venv\Scripts\python.exe -m alembic upgrade head --sql
..\.venv\Scripts\python.exe -m alembic downgrade 0001_initial_schema:base --sql
```

Suite output: `61 passed, 4 skipped, 1 warning, 12 subtests passed in 5.45s`.
PostGIS integration PASS count is **zero**. Docker Desktop engine remains
unavailable; no supplied test database. New `test_risk_postgis_live.py` requires
SAFEROUTE_TEST_DATABASE_URL pointing at an already migrated PostgreSQL database
named saferoute_test_*. It never falls back to SQLite, creates schemas, or resets
the database; inserted fixtures roll back.

When a disposable DB is available: set DATABASE_URL and SAFEROUTE_TEST_DATABASE_URL
to that same test DB; run upgrade head, tests/test_risk_postgis_live.py, downgrade
base, verify removed tables/enums and retained extension, upgrade head again and
run Alembic metadata comparison. Record actual results before claiming PASS.

## Security / cloud handoff (TV3)

- Config/Compose contain a fixed development JWT secret; production must require
  injected secret, with no fallback to the public development value.
- init_db seeds USER/REVIEWER/ADMIN with the same fixed development password.
  Compose invokes it automatically. Production must not create these accounts.
- /uploads is mounted as public StaticFiles, bypassing any future private-photo
  access policy. Local upload storage also lacks a durable multi-instance cloud
  design. Resolve authorized access and object storage before deployment.
- Main hardcodes wildcard CORS with credentials and ignores configured origins.
- Compose has fixed development DB credentials, exposed DB port, source bind
  mount and reload server. This is a development setup, not a production recipe.
- Dockerfile copies the entire backend; no backend .dockerignore present in
  received tree. Exclude .env, uploads, venv and private artifacts before builds.
- Container command hardcodes port 8000 despite defining PORT. Cloud Run port,
  CPU/RAM, concurrency, connection pool budgets and persistence are unverified.
- /health exposes truncated database exception text and only tests SELECT 1,
  not PostGIS capability or migration readiness. No secret values repeated here.
- Auth/reports/review/AI/routing routes are not wired; several tests are empty.
  Foundation test PASS does not establish RBAC or P0 E2E readiness.

No security changes to TV3 modules were made; these are recipient action items.

## Handoff and next P0

TV3: approve ID/status/severity mappings and policy source; unify URL/session
configuration; resolve create_all/Alembic ownership and seed/security findings.
TV5: review imports patch, keyed scorer results, counts/thresholds and explicit
handling of missing scores. Also correct single-route/no-scorer explanation:
absence of scoring currently gets described as no recorded hazards.
TV4: after contract approval and DB availability, execute real migration cycle,
spatial/index/constraint checks and live adapter tests. Only then wire via TV3/TV5.

Files added: postgis_adapter.py; test_risk_postgis_adapter.py;
test_risk_postgis_live.py; this report; proposals/tv5-canonical-imports.patch.
Handoff, DB audit and tracker updated. No scorer rewrite, duplicate models or
competing initial migration. Rubric evidence: implementation quality (20-point
category), partial unit evidence only, not a points or cloud-readiness claim.
