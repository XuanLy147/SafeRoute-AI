# Known limitations

- Main merge is conditional; see RELEASE_READINESS.md for current evidence.
- Production risk policy remains unapproved. Default risk is UNAVAILABLE and
  recommendation is ETA-based. Never enable demo policy implicitly.
- Cloud Run/IAM/private persistent storage and cost are not verified or deployed.
- Maps and Routes live now pass with new restricted keys; see GOOGLE_PROVIDER_SETUP.md.
  Routes local key permits the configured IPv4 egress only. Native Windows requests
  preferring IPv6 are denied; tested Docker path works. Changing network/server
  requires reviewing the allowed egress IP. Map loading can remain pending after
  future SDK authorization failures; report list remains available.
- No social authentication, refresh-token or password-reset contract exists.
- Admin users/metrics/role management and aggregate review history lack endpoints;
  UI remains explicitly unavailable/read-only. Report detail has actual audit logs.
- Draft uses SavedStateHandle and persisted gallery URI grants. Force-stop/clear-data,
  revoked URI access and deleted camera cache may require selecting the image again.
- On an uncertain report write, check My Reports before starting another report;
  no server idempotency key contract exists. A canceled upload may leave an orphan
  private image; it cannot be displayed publicly or assigned to another user.
- Map list is currently server default page size (200); it does not claim exhaustive
  road coverage. No recorded hazards never means guaranteed safety.
- Local synthetic image/coordinate tests do not establish accuracy, field safety,
  production latency, cloud cost or accessibility across all devices.

## Provider retest and local cleanup — 2026-11-07

User supplied a Routes key, now stored only in ignored backend/.env. Direct
Google Routes request returned HTTP 403 / API_KEY_ANDROID_APP_BLOCKED; the real
backend adapter raised RouteServiceAuthError (HTTP 502 mapping). No live Routes
success is claimed. This supersedes earlier missing-key status. See
evidence/routes-provider-auth.txt. Use a separate server-authorized Routes key.

At user choice, C:/dev retains SafeRoute-AI (original dirty repository) and
SafeRoute-AI-full-release (most complete integration). Other SafeRoute backups
and the backend runtime worktree were relocated under .local-archive, ignored
locally; frontend duplicate worktrees were unregistered. A baseline directory
remnant was archived after Windows long-path deletion failed. Other projects
were untouched. Main is still not merged.
