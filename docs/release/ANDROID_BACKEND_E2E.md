# Android/backend E2E evidence

Status: **IN_PROGRESS**. Do not treat JVM or backend HTTP PASS as device E2E.

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
