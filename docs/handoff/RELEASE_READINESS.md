# Release readiness — 2026-11-07

Verdict: **READY_WITH_DOCUMENTED_LIMITATIONS** for backend/Android integration;
**not Production Ready**. Branch `integration/tv345-backend-handoff`.
Implementation snapshot: `005366bfb9dde5a6512195ee27768a4273db4592`.
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
| Combined regression/HTTP/PostGIS | PASS 172 tests + 12 subtests; 0 failures/skips/xfail |
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
| GitHub Actions | NOT_RUN at document creation; final delivery reports remote outcome |
| Cloud Run | NOT_RUN; no credentials/project/cost approval |
| Android device | NOT_RUN; read-only source audit and handoff only |

Evidence in `docs/integration/evidence/`: postgis-http.txt, container-http.txt,
docker-build.txt, ai-cpu.txt, secrets.txt. Container verified image:
`sha256:02261033c32b06b1dfab3ee6f04084da47812bb58483ee091f1ce2230b16226c`.
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
