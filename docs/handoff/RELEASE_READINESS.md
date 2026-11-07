# Release readiness — 2026-11-07

Verdict: **READY_WITH_DOCUMENTED_LIMITATIONS** for backend/Android integration;
**not Production Ready**. Branch `integration/tv345-backend-handoff`.
Implementation snapshot: `a11875074dd14da9cc1af18fa10099c45e08ba72`.
CI refinement: `c4ba25d`. Final delivery SHA is in Git/final handoff message;
documentation and evidence commits follow this implementation snapshot.

## Sources

- TV3 ac607a24c35b6b8739fdb1cc97186003b6aeef04
- TV4 38df244da6a3b0cf8ce2e627b13d538bfde6e83f plus preserved local changes (702af0e)
- TV5 b5c81b4ecf5298ec6dcd8923d4abb848260092ac

Actual merge commits retain all sources. Original dirty worktree untouched and
backed up separately. No main/frontend/Android edits or pushes.

## Verification

| Gate | Observed result |
|---|---|
| Combined regression/HTTP/PostGIS | PASS 179 tests + 12 subtests; 0 failures/skips/xfail |
| Original live spatial suite | PASS 5 tests within combined count, not added twice |
| Alembic | PASS upgrade/rollback/re-upgrade, existing-row preservation, metadata diff=[] |
| PostGIS | PostgreSQL 16.4/PostGIS 3.4.3; geography SRID4326/GiST/ST_DWithin meters PASS |
| Auth/private images/transactions | PASS DB role/deletion/error, PNG/JPEG/WEBP ownership, duplicate vote, audit rollback |
| Docker | Build, non-root startup, actual network HTTP and process restart/volume persistence PASS |
| Real AI CPU | PASS PyTorch 2.6.0+cpu, Ultralytics 8.4.21, checkpoint SHA and class map |
| GPU | NOT_RUN; CUDA unavailable |
| Ruff | PASS ruff check backend |
| Secrets | PASS Gitleaks 8.24.3 scan of tracked release content |
| Contract | Runtime-generated OpenAPI and schema-validated examples |
| Google Routes live | NOT_RUN; no authorized configured key/billing |
| GitHub Actions | PASS [run 42364615627](https://github.com/XuanLy147/SafeRoute-AI/actions/runs/42364615627) on 3847148; current branch triggers verification again |
| Cloud Run | NOT_RUN; no credentials/project/cost approval |
| Android device | NOT_RUN; read-only source audit and handoff only |

Evidence in `docs/integration/evidence/`: postgis-http.txt, container-http.txt,
docker-build.txt, ai-cpu.txt, secrets.txt. Container verified image:
`sha256:144ae9d93322e2432f90454af706550d9c7c1b77f49fc2eb270baad25a7e3f39`.
The AI input is a synthetic gray image, not an accuracy benchmark or cloud latency
measurement. Runtime matches checkpoint metadata; see official YOLO12 guidance
https://docs.ultralytics.com/models/yolo12/ . No model substitution or training.
One Starlette/AnyIO deprecation warning remains; it does not hide failed tests.

## External limitations and rollout

Production numeric risk policy is unapproved. Default risk is unavailable; demo
revision DEMO-2026-11-07 is explicitly synthetic, and production rejects an
unapproved configured policy. Live Google quota/billing requires authorized key.
Cloud IAM/HTTPS/private GCS-volume durability requires authorized staging execution;
local filesystem is never claimed durable on Cloud Run. See infra/cloud-run/RUNBOOK.md.

TV1/TV2 must connect shared HTTP/image clients and map nullable risk/status/basis;
no Android device E2E claimed. Private image Bearer requirement, normalized bbox,
full_name, saved confirmed detections and audit/timestamp aliases are documented.
Legacy metadata without known ownership stays reviewer-only; missing objects 404.
Historical incorrect MIME/object metadata requires operator reconciliation against
stored objects before a production data migration. No reporter/address is invented.

Delivery: TV1_MAP_ROUTING_HANDOFF.md, TV2_REPORT_COMMUNITY_HANDOFF.md,
API_CONTRACT_LOCK.md, LOCAL_DEVELOPMENT.md, examples.json and generated OpenAPI.
See source audit/risk decision for root causes and policy formula. Local gates do
not authorize production deployment. Final committed HEAD is reverified before push.

CI run 42364384922 failed before tests: Docker pull stderr was appended to container ID.
Runner now returns stdout only for container creation; no assertions changed.

Remote CI evidence is pinned to source 3847148: 178 tests plus 12 subtests and
all runtime gates PASS. Later HTTPBearer/OpenAPI correction adds one local test
(179 total) and has passed the same local suite/container gates. Each push runs
GitHub Actions again; the final delivery message links the final HEAD run.
The prior failed first run is retained as history; no failure was hidden.
