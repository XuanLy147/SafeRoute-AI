# Known limitations

- Main merge is conditional; see RELEASE_READINESS.md for current evidence.
- Production risk policy remains unapproved. Default risk is UNAVAILABLE and
  recommendation is ETA-based. Never enable demo policy implicitly.
- Cloud Run/IAM/private persistent storage and cost are not verified or deployed.
- Android device/provider tests must complete on final code; fixtures do not prove
  Google billing, Maps rendering or live routes.
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
