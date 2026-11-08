# SafeRoute backend — TV345 integration

Python 3.11/FastAPI, real PostgreSQL/PostGIS, approved YOLO checkpoint and Google
Routes adapter. Current assignments: TV3 API/AI HTTP; TV4 DB/risk/infrastructure;
TV5 routing. Android is unchanged.

Start with [local setup](../docs/handoff/LOCAL_DEVELOPMENT.md) and
[release evidence](../docs/handoff/RELEASE_READINESS.md). API contract is generated
in docs/api/openapi.json; handoff documents are in docs/handoff/.

From repository root, supply random POSTGRES_PASSWORD and SECRET_KEY then run:
`docker compose -f backend/docker-compose.yml up --build`.
Root build context is required to package model ai/weights/best.pt. Alembic owns schema;
no create_all, implicit demo accounts or in-memory persistence fallback exists.

Host development: install requirements.txt plus requirements-ai.txt in an isolated
Python 3.11 venv. Configure DATABASE_URL/SECRET_KEY in environment or backend/.env,
then from backend run `python -m alembic upgrade head` and
`python -m uvicorn app.main:app --host 0.0.0.0 --port 8000`.

/health is liveness; /ready checks DB/schema and reports AI/risk readiness.
Private image URLs require uploader/reviewer Bearer. Risk configuration is absent
by default; production numeric approval remains outstanding. Google Routes key
must be provided explicitly; no fixture fallback. AI unavailable returns 503 and
manual reporting remains available.

Verification from repository root: `python scripts/qa/verify_postgis.py --backend
backend --runxfail`, `python scripts/qa/verify_ai.py`, `python scripts/qa/verify_contract.py`,
and `ruff check backend`. Do not run HTTP suites against a personal/production DB.
Cloud template and operational constraints are in infra/cloud-run/RUNBOOK.md.
