# Google provider setup — verified 2026-11-07

Authorized Google Cloud account/project was inspected in the Console. Project:
saferoute-AI (gen-lang-client-0688968980). Maps SDK for Android and Routes API
were already enabled; existing Pay as you go billing was unchanged. Existing
Gemini credential and service account were not changed.

Two new keys were created after explicit action-time user confirmation:

- saferoute-android-maps: only Maps SDK for Android, Android package
  com.comeforfood.saferoute, local debug SHA-1
  EE:A7:B6:C9:45:45:F3:EA:E8:02:89:B3:2C:70:90:44:A7:47:02:6B.
- saferoute-backend-routes-local: only Routes API, restricted to the confirmed
  local public IPv4. Exact IP is kept in local configuration/Cloud Console, not
  required for teammates. This is not a production Cloud Run key.

Key values are only in ignored android/local.properties, backend/.env and the
ignored local key transfer file. No secret is stored in tracked evidence.

## Observed validation

- Actual adapter via allowed IPv4: two Google route candidates, decoded geometry.
- Actual Android -> FastAPI Docker/PostGIS/AI E2E: registration, image AI/manual
  report, vote, reviewer, audit, rendered Google Maps and real Routes PASS.
- Unavailable risk policy stays explicit; no invented numeric score.
- Backend restart persistence PASS; disposable resources cleaned.
- Maps screenshot: evidence/live-map.png. Execution: evidence/android-live-providers.txt.

Windows can prefer IPv6; the server key correctly denies unlisted IPv6. Docker
E2E works using the authorized egress. Review restrictions when changing network
or deploying. Do not remove IP restrictions as a workaround. Other signing
certificates require their own Android restriction entries.

An initial E2E attempt failed before provider assertions due to querying the vote
button before report detail loaded. A bounded wait was added; the assertion and
all subsequent live checks were retained and passed.
