# Local backend development for TV1/TV2

Backend branch: `integration/tv345-backend-handoff`.
Implementation snapshot: `005366b` (source/security/runtime commits b7c0b87, 828f1ef, 005366b).
Final delivery SHA is reported by Git and the final handoff message; subsequent
contract documentation changes do not change this provenance anchor.
Contract revision: `tv345-contract-2026-11-07`, API `/api/v1`.

1. Clone and checkout integration/tv345-backend-handoff; do not replace a dirty
   existing checkout. Requires Python 3.11 and Docker Desktop Linux containers.
2. From repo root create a venv, activate, and install:
   `python -m pip install -r backend/requirements.txt -r backend/requirements-ai.txt`.
   CPU Linux runtime: install torch==2.6.0 torchvision==0.21.0 from
   https://download.pytorch.org/whl/cpu first. Ultralytics is pinned to 8.4.21,
   matching checkpoint metadata; do not replace weights or checksum.
3. Generate local random POSTGRES_PASSWORD and SECRET_KEY (>=32 chars). In
   PowerShell: `$env:SECRET_KEY = python -c "import secrets; print(secrets.token_hex(32))"`.
   Set POSTGRES_PASSWORD similarly. These are process variables, never commit them.
4. `docker compose -f backend/docker-compose.yml up --build` starts private local
   PostGIS, runs Alembic and serves backend port 8000. Photos and DB use named
   volumes. No hardcoded production secrets and no implicit demo seed.
5. Alternative host Python: start only Compose db, set DATABASE_URL to
   `postgresql+psycopg2://postgres:PASSWORD@127.0.0.1:5432/saferoute`; cd backend;
   `python -m alembic upgrade head`; `python -m uvicorn app.main:app --host 0.0.0.0 --port 8000`.
   Never use create_all or stamp a missing migration to fake readiness.
6. Check host /health and /ready; Swagger /docs; actual OpenAPI
   /api/v1/openapi.json. /health is liveness and may say database offline;
   /ready requires migrated PostGIS, separately reports AI/risk readiness.
7. Android Emulator base origin http://10.0.2.2:8000; API prefix /api/v1/.
   Physical device uses your host's real LAN address, same network and firewall
   permission. Compose binds loopback by default: deliberately change binding
   for LAN development. Never invent a staging URL or expose demo credentials.
8. Create ordinary user via registration. For explicitly local reviewer accounts,
   set ENABLE_DEMO_ACCOUNTS=true and ENVIRONMENT=development and run
   `python -m app.db.init_db` AFTER migration. This intentionally creates local
   user/reviewer/admin @saferoute.ai accounts with password 123456; never use on
   shared/public infrastructure. Prefer dedicated test accounts.

Google routing requires GOOGLE_ROUTES_API_KEY with Routes API and billing enabled;
no key gives 502 (no fabricated routes). Numeric risk policy is absent by default.
For a clearly labeled demo set RISK_POLICY_JSON to the DEMO-2026-11-07 JSON in
TV345_RISK_DECISION: explicit LOW=1/MEDIUM=2/HIGH=4, pending=.5, radius=25,
medium_min=2/high_min=6, approved=false. Never call this field-validated policy.

## Verification

From root: `python scripts/qa/verify_postgis.py --backend backend --runxfail` creates
its own disposable PostGIS container, migrations, rollback/data preservation,
full HTTP/regression suite, then cleans only its own resources. No external DB
URL or production database is needed. `python scripts/qa/verify_ai.py` exercises
the real approved checkpoint on a synthetic gray image (not accuracy evaluation).
`python scripts/qa/verify_contract.py` checks generated OpenAPI/handoff artifacts.
`ruff check backend` is the lint gate. `docker build -f backend/Dockerfile -t
saferoute-tv345:integration .` then `python scripts/qa/verify_container.py` tests
non-root runtime, real network HTTP and process restart/volume persistence.

Troubleshooting: 503 DB→check DB/migrations/network; 503 AI→checkpoint checksum/
runtime path, do not substitute a model; 403 image→send uploader/reviewer Bearer;
422 bbox→normalized xyxy, not pixels; 502 routes→key/API permission; unknown risk→
configure an explicit demo policy or obtain production approval. Legacy metadata
without a known uploader remains reviewer-only. Cloud guidance is in
infra/cloud-run/RUNBOOK.md; cloud storage template remains unverified in staging.
