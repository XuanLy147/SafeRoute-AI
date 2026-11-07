# Android/backend E2E evidence

Status: **P0-A PASS (map data); Maps live FAIL; Routes live NOT_RUN**.
Do not treat map data refresh as rendered Google tiles.

Harness: scripts/qa/verify_android_e2e.py. A dedicated API 35 Google APIs x86_64
emulator is provisioned; host WHPX is available. The machine has limited free RAM,
so Gradle and emulator verification are sequenced. Real test backend uses the
integrated Docker image and fresh PostGIS with migrations; no SQLite or shared DB.

The test registers a USER through Android UI, uses a synthetic gray image and
explicit coordinates, runs actual AI/manual confirmation, uploads, creates report,
votes, recreates activity, logs in to a real reviewer, reviews, confirms audit and
public map data refresh. Server restart then verifies durable status/audit.
The reviewer is registered through HTTP and assigned a DB role only in the
new disposable database. It is never a fake token or client-assigned role.

If a server key is configured, the same test calls actual Google Routes through
Android/backend and validates candidate geometry and unavailable-risk semantics.
This default policy is not silently replaced with demo values. Maps visual
rendering requires a separate configured Android key and observed device evidence.

All test inputs are synthetic and explicitly labeled. Final execution output and
failures must be appended here; test implementation alone is not PASS evidence.

## Observed runs

At implementation 7c167eb, Android 15 / sdk_gphone64_x86_64 completed
`OK (1 test)` and backend restart persistence PASS. This run had no provider keys;
its map assertion verifies the actual VERIFIED report returned into the view model.
See evidence/android-e2e.txt. Regression separately passed 8/8 locally and in CI.

After importing the user-supplied local.properties, a second run enabled the
explicit liveMaps assertion. The key was verified present in merged manifest
without printing it. Google logged Authorization failure and map-ready timed out;
this run is FAIL, not a second P0-A PASS. See evidence/maps-authorization.txt and
evidence/android-e2e-maps.txt. Disposable containers/network/volume were cleaned.

User confirms no Routes server key. No server .env/key was fabricated and the
Android key was not reused. Real Google candidate/risk/UI verification remains
BLOCKED. CI regression excludes opt-in provider/backend tests explicitly and
must not be used as a substitute for these gates.
