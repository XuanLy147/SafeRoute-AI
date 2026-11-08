# Full release handoff — BLOCKED

Main is unchanged. Draft PR: https://github.com/XuanLy147/SafeRoute-AI/pull/3.
Branch integration/saferoute-full-release; worktree C:/dev/SafeRoute-AI-full-release.
Source ancestry and backend revert reconciliation: MERGE_PROVENANCE.md.
Original dirty TV4 worktree is preserved. No cloud deployment or main merge.

## Implementation

One native Java/XML app and one authenticated Retrofit client now connect auth/me,
private images, upload/AI/manual report, detail/vote/review, map data and route
comparison to the locked backend. Keystore token protection, lifecycle cleanup,
bounded photo decoding, ambiguous-write recovery and profile back navigation
are implemented. Route counts/geometry/risk availability remain backend-owned;
no synthetic routes or numeric risk are substituted. Owners remain TV1–TV5.

## Verification

- Baseline frontend: 53 JVM/Robolectric tests PASS.
- Integrated Android: 63 JVM/Robolectric tests, debug APK, test APK and lint PASS.
- Device regression: 8/8 PASS locally and CI on API 35.
- Real Android/backend: register/photo/AI/manual/create/vote/review/audit/map-data
  refresh and backend restart persistence PASS; synthetic image, no accuracy claim.
- Backend: 179 tests + 12 subtests PASS, real PostGIS/migrations, runtime OpenAPI,
  approved CPU AI, Docker HTTP/restart persistence and secret scan PASS.
- GitHub Actions at implementation 7c167eb: Android 42370556938 and backend
  42370557088 PASS. Provider tests are separate from CI regression.
- Maps live PASS: rendered Google tiles and report marker observed on Android 15.
- Routes live PASS: Android -> real Docker backend -> Google candidates; unavailable
  numeric risk remains explicit. Backend restart persistence PASS. PR review pending.

## Delivery and remaining gates

Normal local debug APK: frontend/app/build/outputs/apk/debug/app-debug.apk,
backend origin http://10.0.2.2:8000/. It is not a signed production release.
User's Maps configuration is in ignored frontend/local.properties. Key values
are not tracked. See ANDROID_CONFIGURATION.md for the observed package/SHA-1
and Google authorization diagnostic history. New separate restricted keys are
configured; see GOOGLE_PROVIDER_SETUP.md.

See RELEASE_READINESS.md, ANDROID_BACKEND_E2E.md and evidence/ for detailed
results; README, ANDROID_CONFIGURATION.md, DEMO_SCRIPT.md, KNOWN_LIMITATIONS.md
and ../api/openapi.json provide the runbook and contract.

Next gates: complete PR review/protection and final CI checks, merge only after every
mandatory gate passes, then verify main HEAD and CI. Production risk approval,
Cloud Run/IAM and measured cost remain unverified separate work.

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

Latest update: new authorized Maps/Routes keys and complete live E2E PASS. Earlier
provider-retest failures below/above are historical and superseded. See
GOOGLE_PROVIDER_SETUP.md and evidence/android-live-providers.txt.
