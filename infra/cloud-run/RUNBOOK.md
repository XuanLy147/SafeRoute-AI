# Staging deployment runbook — not deployed

Prerequisites: explicit cost authorization, project/region, Artifact Registry,
Cloud SQL PostgreSQL with PostGIS enabled, private Cloud Storage bucket, runtime
service account, Secret Manager versions and approved access policy. No public
bucket ACL; grant the runtime account only bucket object access, SQL client and
specific secret accessor permissions. Database role must have schema privileges
required by Alembic; runtime role should not be DB owner in production.

1. Build from repository root: `docker build -f backend/Dockerfile -t IMAGE:SHA .`.
   Run local gates first, then push immutable image to the authorized registry.
2. Backup DB; run `alembic upgrade head` as a separate Cloud Run Job using this
   image and DATABASE_URL secret/Cloud SQL connection. Never migrate in parallel
   in every serving instance. Test downgrade only on disposable databases.
3. Fill service.template.yaml placeholders and pinned secret versions. Configure
   production JWT key (random >=32 characters), DB URL and Google Routes key.
   Leave RISK_POLICY_JSON absent until numeric policy approval; absence means
   honest ETA-only behavior, not RISK_READY. Approved policy can be injected as
   a Secret Manager version with revision and approved=true after owner review.
4. Persist photos through the private GCS volume; local container filesystem is
   ephemeral. PERSISTENT_UPLOADS=true is an operator assertion and must be backed
   by the mounted volume. Validate write/read/restart on staging before release.
   This local-file interface uses a private mounted object volume; direct GCS SDK
   storage and signed public URLs are not implemented or claimed.
5. Deploy revision without traffic first using approved gcloud credentials.
   Keep IAM/invoker and ingress restricted. Android cannot call an IAM-private
   Cloud Run URL using only application JWT; configure an approved HTTPS load
   balancer/IAP/service gateway policy before handing out a staging base URL.
6. Verify /health, /ready, real AI CPU inference, authorized image read, rejected
   image access, register/report/vote/review, Google API quota/error handling and
   restart durability. Log correlation metadata only; do not log tokens/photos,
   raw DB exceptions or precise user positions.
7. Canary traffic only after gates and policy sign-off. Roll back traffic to the
   previous immutable revision if readiness/error rate regresses. Do not blindly
   downgrade schema on production data; keep migration 0002 backward compatible,
   restore from a tested backup if data repair is needed.

Resource settings (2 CPU, 4Gi, concurrency 2, max 2 instances) are initial bounds,
not measured capacity or cost. One inference lock/worker bounds parallel model
memory. Benchmark cold start, p95 latency, memory and billing in approved staging.
Cloud credentials/cost approval were not supplied; deploy, URL, IAM and durability
of this template remain NOT_RUN, not production-ready evidence.
