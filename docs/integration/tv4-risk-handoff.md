# TV4 risk core — handoff 2026-11-07

Latest follow-up: [real PostGIS verification / contract gate](../db/tv4-postgis-verification.md).
PostgreSQL 16.4 / PostGIS 3.4.3: migration upgrade/rollback/re-upgrade PASS,
metadata diff=[], 61 unit tests + 4 real DB tests PASS. Container cleaned up.
Production policy/import/protocol approval remains DRAFT/BLOCKED. TV5 zip bug
and TV3 standalone import cycle remain unresolved. Earlier missing-source and
DB NOT_RUN statements below are historical, superseded by the linked evidence.

Status: **DRAFT / UNCONFIRMED**, internal Python interface only. Owner: TV4
under the user's Universal Master Prompt V2.0 assignment. TV3 and TV5 have
not reviewed this proposal. No public HTTP/DB contract was changed.

## Verified baseline and scope

- Base: `origin/backend`, `7a78359f2b3a1f57a417cf3bef35e80227b4f73f`.
- Branch: `feature/tv4-risk-core`; working tree was clean before implementation.
- Read: root README/AGENTS/AI_TRACKER, architecture, integration decisions and
  README, API README, DB schema notes, backend/module/migration/test READMEs,
  infra/Cloud Run/workflow READMEs, QA test cases.
- Backend, migrations, infrastructure and workflow directories contained
  documentation scaffolds. No runnable backend or approved DB/risk contract found.
- Existing ownership documentation is stale. User-confirmed assignment:
  TV3 HTTP/backend/AI integration; TV4 DB/migrations/risk/cloud/CI; TV5 Routes.
  TV3/integration lead should update shared governance files; this handoff does
  not rewrite those files or claim approval from other owners.

## Implemented local behavior

`backend/app/modules/risk/scoring.py` supplies `score_routes(routes, policy)`.
It uses Python standard library only; no dependencies or runtime versions changed.

- Input: ordered real-route candidate IDs and nearby report snapshots selected
  by a future PostGIS query. This function does not determine proximity or decode
  geometry, generate candidates, or call Google Routes.
- Policy: explicit revision, severity-to-weight mapping and pending multiplier
  in [0, 1]. There are no production defaults. Proposed formula is the sum of
  per-report severity weights, multiplied by the pending multiplier only for
  `PENDING_VERIFICATION`. This formula still needs recipient review.
- `REJECTED` never contributes. A zero pending multiplier excludes pending.
  Positive pending contributions preserve the unverified status in reasons.
- Each report ID is counted once per route; identical duplicates collapse.
  Conflicting snapshots within/across routes raise `ValueError`, requiring the
  caller to reload a consistent snapshot. Shared reports count independently
  for each affected route.
- Output preserves route order and cardinality (0/1/n), with score, policy
  revision, contribution metadata and excluded counts. No recommendation or
  tie-breaking rule is imposed on the public response.
- Unknown included severity, invalid status/IDs, duplicate route IDs,
  nonfinite/negative weights and score overflow fail explicitly.
- No confidence, AI class, votes or resolution status participates in scoring.
  Zero means no weighted recorded hazards in supplied data, not safe travel.

## Verification evidence

From `C:\dev\SafeRoute-AI\backend`:

```powershell
py -3.11 -m unittest discover -s tests -p test_risk_scoring.py -v
```

PASS on Python 3.11.9: 16 tests, `Ran 16 tests in 0.003s`, `OK`.
Tests cover no reports, pending/rejected, explanations, duplicates/conflicting
snapshots, 0/1/n routes, ordering, shared reports, validation, immutable policy
weights and overflow. All test inputs/weights are **MOCK_FOR_DEV** synthetic
fixtures; these are not Google responses or empirical risk measurements.

NOT_RUN: PostGIS distance/SRID/index checks and migration upgrade/rollback;
no migration was created because schema/transaction contracts are unconfirmed.
Docker CLI 29.9.2 exists, but `docker info --format '{{.ServerVersion}}'` failed
because the Docker Desktop Linux engine pipe was unavailable. No `psql` was
found on PATH. This does not establish whether another DB service is available.

NOT_RUN: HTTP integration, real Google Routes, CI execution, Cloud Run and E2E.
No live API/cloud success or performance/cost result is claimed.

## Recipient decisions and next P0

- TV3 + TV4: agree report ID/severity representation, DB schema, snapshot and
  transaction boundaries, and mappings to HTTP errors. Internal reason IDs
  should not automatically become public fields.
- TV4 + TV5: agree policy revision, weights, pending policy, proximity radius
  in meters, route geometry input, repeated-segment handling and tie behavior.
  Production weights must not be copied from the test fixture without review.
- TV3/integration lead: update governance and record approved contracts in
  `decisions.md`; suggested tracker entry: TV4 risk core **IN_PROGRESS**,
  16 unit tests PASS, DB/routes integration BLOCKED on approved contracts and
  a runnable PostGIS test environment. No commit/PR exists yet.
- Next smallest P0: implement agreed PostGIS schema/migration and nearby-report
  query; verify EPSG:4326, longitude/latitude ordering, meter-based distance,
  GiST index, transaction constraints, upgrade and rollback on a disposable DB.

Score-to-evidence: architecture/implementation (maximum 20 rubric points),
TV4-owned isolated implementation and 16 passing tests. This is partial evidence,
not a claim to have earned points or completed the DB/risk/cloud workstream.

## Database integration follow-up — 2026-11-07

Re-audited the same branch/HEAD; all seven reported TV3 files are absent locally,
including entities, session, geo, init_db and OpenAPI. TV3's 6/6 pytest is
user-reported and has not been reproduced here. Existing TV4 code/tests remain
unchanged. Regression rerun: 16/16 PASS in 0.001s using the command above.

See [contract matrix and PostGIS verification plan](../db/tv4-postgis-contract-audit.md)
for CONFIRMED/DRAFT/CONFLICT/BLOCKED classifications and recipient deliverables.
Database integration is BLOCKED on actual TV3 source, shared contract decisions
and a usable PostGIS test environment. Docker engine probe still fails.
Migrations, spatial queries, DB integration and cloud remain NOT_RUN.

The user explicitly authorized this follow-up to update AI_TRACKER.md; a scoped
status section and journal entry were added, preserving historical task rows.
No entities, duplicate session, guessed schema or replacement scorer were added.
No commit, push, merge or deployment was performed.
