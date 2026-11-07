# TV4 real PostGIS verification and contract gate — 2026-11-07

## Pre-commit recheck — authorized TV4 commit/push

Fresh rerun: 65 tests PASS (61 unit + 4 real PostGIS), 0 skipped; upgrade ->
rollback -> upgrade PASS; metadata diff=[] twice; container cleaned up.
Output: [pre-commit evidence](evidence/tv4-precommit-verification.txt),
`65 passed, 1 warning, 12 subtests passed in 2.01s`.
Main-workspace standalone risk core also reran: 16/16 PASS.
Fetch now shows TV3 at `bf29861`; this verification remains pinned to `9197a41`
and TV5 `5d65a6c` in the preserved snapshot. Compatibility/security findings
have NOT been reassessed against newer TV3 code. No TV3/TV5 source is included
in the TV4 commit; the existing import patch is an explicit review proposal.
The reports below describe pre-commit historical states, not a claim that this
authorized commit/push has not occurred. Current SHA is available in Git history.

**DB verification: PASS. Production contract finalization: BLOCKED on owner decisions.**
Scope: isolated snapshot of TV3 `9197a41`, TV5 `5d65a6c`, local TV4 code and
the previously proposed canonical-import patch. No merge/commit/push/deploy.

## Repository and snapshot safety

Main workspace remains `feature/tv4-risk-core` at `7a78359`. Existing uncommitted
code and documents were retained. Re-read the three TV4 reports, AGENTS/README,
actual migration, entities, session, routing schemas/service and adapter/tests.
Snapshot: `C:\dev\SafeRoute-AI-tv4-integration-20261107`.

Compared snapshot entities.py, db/session.py and 0001_initial_schema.py against
TV3 Git blobs: unchanged. TV4 scorer, adapter and three test files match the
main workspace byte-for-byte. No TV3/TV5 code or migration was changed this turn.
Snapshot still contains the earlier import-only proposal; it is not owner-approved.

## Contract gate — current decisions

CONFIRMED below identifies user-required semantics or source-verified shapes;
it does not imply that all owners approved the complete integration contract.
All numeric test values are synthetic fixtures, not production decisions.

| Contract | Status | Current evidence / decision needed | Owner |
|---|---|---|---|
| Distance unit | CONFIRMED | Geography ST_DWithin uses meters; verified on PostGIS | TV4 |
| Radius value | DRAFT | 50m and 200m only in tests; production radius/revision unset | TV4 + TV5 |
| Pending verification multiplier | DRAFT | Test policy uses 0; no production value approved | TV4 + TV3/TV5 |
| Rejected | CONFIRMED | Excluded from score/count; verified with persisted REJECTED report | TV4 + TV3 |
| Hazard counts | DRAFT | Adapter counts verified + pending nearby distinct reports, including unscored pending; rejects excluded. Consumer semantics need approval | TV4 + TV5/TV3 |
| Duplicate handling | DRAFT | Current behavior: one report ID per route; same report can affect several routes; conflicts fail. Live repeated-segment test PASS. Distinct-ID duplicates need a separate agreed policy | TV4 + TV3/TV5 |
| Severity weights | DRAFT | HIGH=4 only a fixture; category-based TV3 RiskWeight seeds are not converted automatically | TV4 + TV3 |
| Risk thresholds | DRAFT | medium_min=2/high_min=8 only fixtures; production LOW/MEDIUM/HIGH thresholds unset | TV4 + TV5 |
| Route ID / cardinality | CONFIRMED | User requires exact candidate IDs/count; TV4 ID mapping and missing-result guard pass | TV4 + TV5 |
| Geometry shape | CONFIRMED from source | TV5 decoded (lat,lon) -> GeoJSON [lon,lat] -> geography LINESTRING/4326; real spatial tests PASS. No generated alternatives | TV4 + TV5 |
| RiskScorerProtocol | BLOCKED | Current TV5 list-only scores have no route ID; zip can silently truncate/misassign. Proposed keyed result boundary awaits TV5 approval | TV5 + TV4 |
| Canonical imports | DRAFT | app.* works in TV3/backend layout and tested snapshot; original TV5 backend.app.* remains inconsistent. Proposal awaits owners | TV3 + TV5 |

Do not wire production API using these fixture policies. DB correctness can be
tested independently of selecting the production risk model, as done here.

## Real database environment and reproducible evidence

Initially Docker daemon was unavailable. No PostgreSQL service, psql/pg_ctl/postgres
on PATH, standard PostgreSQL installation directory, or listener on port 5432
was found. Started the installed Docker Desktop hidden; daemon became available.

Used the repository's `postgis/postgis:16-3.4` image:

- Image ID/digest: `sha256:44126d872ac91993766c341e369c539e8196614321765d36a6f1bab0419a5fa5`.
- PostgreSQL: `16.4 (Debian 16.4-1.pgdg110+2)`.
- PostGIS: `3.4.3 e365945`; GEOS 3.9.0, PROJ 7.2.1.
- Dedicated random `saferoute_test_tv4_*` database in a new auto-removed container.
- Random ephemeral password passed in environment, never written to repository.
  Port bound to 127.0.0.1 with a random host port; no host data mount.
- Did not run TV3 Compose, init_db, create_all or account seeding.

From the main workspace:

```powershell
& 'C:\dev\SafeRoute-AI-tv4-integration-20261107\.venv\Scripts\python.exe' `
  scripts/qa/verify_postgis.py `
  --backend 'C:\dev\SafeRoute-AI-tv4-integration-20261107\backend'
```

The runner creates only its own disposable container, uses that connection for
Alembic/tests, and stops that specific returned container ID in finally. Both
attempts cleaned up. Docker Desktop and downloaded image remain available.

Full sanitized output: [successful verification](evidence/tv4-postgis-verification.txt).
Initial harness failure: [first attempt](evidence/tv4-postgis-first-attempt.txt).
The first attempt upgraded successfully but compared extension relations visible
through the image's tiger/topology search_path. Fixed the runner to compare the
application's public schema with `SET LOCAL search_path TO public`, excluding
spatial_ref_sys. No extension tables were removed; no migration change was needed.

## Results

| Verification | Result / scope |
|---|---|
| Alembic initial upgrade head | PASS against real PostgreSQL |
| Core tables | PASS: all 8 actual model tables present |
| Foreign keys / enums | PASS: 8 FKs; 3 enums with expected labels |
| Geography / GiST | PASS: geography(Point,4326); idx_reports_location USING gist(location) |
| Metadata comparison | PASS: diff=[] for public application schema, types/server defaults included; extension tables excluded |
| Rollback to base | PASS: core tables and 3 enums removed; version row empty; PostGIS retained |
| Upgrade after rollback | PASS; schema and metadata comparison rerun successfully |
| Unit tests | 61 PASS: TV3 6, scorer 16, TV5 19, adapter 20 |
| Real PostGIS tests | 4 PASS, 0 FAIL, 0 SKIPPED |
| Total suite | 65 passed, 1 dependency warning, 12 subtests passed in 1.94s |
| Cloud / Google Routes live / full HTTP P0 | NOT_RUN |

The four live tests exercise persisted VERIFIED/PENDING_VERIFICATION/REJECTED
reports through the real ST_DWithin query and unchanged scorer: near/far parallel
routes, shared hazard on multiple routes, repeated route segments without double
counting, lon/lat and SRID, GiST definition, and geography offsets at 40/60 meters
against a 50-meter threshold. Near route scores 4 under the fixture policy; far
route remains in output with 0. Pending remains visible in counts and unweighted;
rejected contributes nothing. These are synthetic data on real PostgreSQL/PostGIS,
not mocked queries and not real Google Routes evidence.

No performance/index-plan claim is made: index definition was verified, not a
representative production query plan or latency benchmark. No broader schema
validation beyond existing metadata is implied by diff=[].

## TV5 handoff — concrete failure evidence

Reproduced again with the actual RoutingService in the snapshot, a mocked
Google provider returning one RouteCandidate and a scorer returning []:

```text
TV5 BUG: input=1 risk_results=0 output=0
TV4 GUARD: missing result raises RiskIntegrationError
```

Service's `zip(candidates, risk_scores)` silently truncates. Equal-length reordered
scores are also not verifiable because RouteRiskScore lacks route_id. This is an
unresolved TV5 issue, despite its current 19 tests passing. TV4 evaluate_by_id
validates exact key coverage and its positional facade restores candidate order.
TV5 should approve keyed results, reject missing/extra/duplicate IDs explicitly,
and review the existing import patch. No TV5 module was modified this turn.

## TV3 handoff — new import finding and security blockers

Fresh process `python -c "import app.modules.risk.postgis_adapter"` fails with a
circular import: entities -> db.__init__ -> init_db -> partially initialized
entities. Importing db.session first (the existing app/Alembic startup order)
works; health tests first mask this. The successful suite does not establish
standalone model/adapter import safety. TV3 should remove eager initialization
coupling in package imports; no owner module was patched here.

Earlier security blockers remain: fixed JWT secret and seeded privileged account
password, automatic create_all/seed startup, public uploads, wildcard credentialed
CORS, differing .env/session URL handling, missing Docker build exclusions,
unverified cloud persistence/resources and exception details in health responses.
Live tests used explicit environment URLs and no init_db, so PASS does not resolve
those production paths. Audit-log deletion/immutability and value constraints
still require business approval.

## Next P0

TV3/TV5 approve the contract rows, canonical imports and keyed scorer protocol;
TV3 resolves import/init_db/security blockers. Then wire the reviewed adapter into
the actual API and run HTTP + Routes integration. Current DB path is verified,
but the complete integration task remains IN_PROGRESS/BLOCKED on those decisions.

Files changed this turn: verification runner, raw evidence, this report and the
four requested tracker/handoff/audit/report documents. No scorer, migration,
models, DB session, TV5 service or schema was changed. No commit/push/merge/deploy.
