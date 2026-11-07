# Full release readiness — 2026-11-07

Verdict: **BLOCKED**. Main is unchanged. Integrated code is committed; main merge
is not authorized until all mandatory gates, PR checks and review pass.
Implementation: 7c167eb (after reconciliation 3b70333, frontend merge d42c939).
Subsequent changes add the explicit Maps live assertion and sanitized evidence.

| Gate | Current observed evidence |
|---|---|
| Source ancestry/content | PASS both source histories retained, Android/backend files preserved |
| Frontend baseline | PASS 53 JVM/Robolectric, debug APK, androidTest APK, lint |
| Integrated Android build | PASS 63 JVM/Robolectric, debug APK, androidTest APK, lint 0 errors (warnings remain) |
| Backend/PostGIS/migration | PASS 179 tests + 12 subtests, real isolated PostGIS; migration cycle and metadata diff=[] |
| Runtime OpenAPI | PASS generated schema matches locked artifact and examples |
| AI CPU | PASS approved checkpoint, PyTorch 2.6.0+cpu / Ultralytics 8.4.21; synthetic gray image, not accuracy |
| Docker | PASS build/non-root/startup/real HTTP/restart persistence |
| Secrets | PASS Gitleaks 8.24.3 tracked source scan, no leaks |
| Android instrumentation | PASS 8/8 regression tests on API 35 locally and in CI; fixtures are explicit |
| Android -> backend E2E | PASS report/review/audit/map rendering/live Routes and backend restart; isolated real PostGIS/AI |
| Google Maps/Routes live | PASS: new restricted keys, actual rendered Maps and Android/Docker/Google Routes on API 35; evidence/android-live-providers.txt |
| GitHub Actions on release | PASS Android 42373508154 and backend 42373508117 at eacb0b9; subsequent test synchronization/evidence changes are locally verified |
| PR review/protection | Draft PR #3 created; review/merge requirements remain pending |
| Production cloud / risk policy | NOT_RUN / unapproved; separate from integrated demo release |

Evidence: evidence/android-test-counts.json and *.txt; source checkpoints named
above. Failed intermediate runs were diagnosed (disconnected UI fixture needed
explicit injection); no existing test assertions removed or weakened. New final
changes must rerun relevant tests. No mock result is claimed as provider evidence.

CI first attempt 42369835242 failed before build: setup-android v3 tried to install
removed SDK package `tools`. Workflow now explicitly requests platform-tools;
Gradle resolves the project's locked platform/build tools. No test was skipped
to make a job green. Draft PR: https://github.com/XuanLy147/SafeRoute-AI/pull/3.

Final local rebuild also corrected Windows SDK drive-colon escaping in ignored
local.properties after lint PropertyEscape identified it. No lint gate disabled.

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

## Latest provider result — supersedes earlier authorization failures

Both live gates PASS with newly created keys in the authorized saferoute-AI
project. See GOOGLE_PROVIDER_SETUP.md. Initial retry exposed a test race: the
vote button was queried before asynchronous report detail finished. Added a
bounded wait for that button, retaining the assertion; full live E2E then passed.
Main remains unchanged pending PR review and final merge requirements.
