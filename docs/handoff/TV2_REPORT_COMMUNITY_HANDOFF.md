# TV2 — Auth, reports, images and community

Backend branch: `integration/tv345-backend-handoff`.
Implementation snapshot: `aefef2acc2046de37076455d06fe5774040648e1` (source/security/runtime commits b7c0b87, 828f1ef, 005366b).
Final delivery SHA is reported by Git and the final handoff message; subsequent
contract documentation changes do not change this provenance anchor.
Contract revision: `tv345-contract-2026-11-07`, API `/api/v1`.

## Authentication

POST `/auth/register`: email, password (6+ characters, at most 72 UTF-8 bytes),
optional full_name (<=200 chars), optional role=USER only. Example:
`{"email":"person@example.com","password":"development-only-password","full_name":"Test User"}`.
POST `/auth/login`: email/password. Responses: access_token, token_type=bearer,
user_id (integer), email, role, nullable full_name. GET `/auth/me` adds id and
created_at. Use `Authorization: Bearer TOKEN`; current DB role is authoritative,
not the token's cached role. Expired/invalid/deleted user gives 401; role denial
403; duplicate email 409; DB outage 503. No refresh endpoint: clear expired session
and prompt login. No implicit demo accounts; seed only explicitly in development.

## Image and AI

POST `/images` multipart field `file`, Bearer required. Accepts actual JPEG/PNG/
WEBP, <=5MiB and <=25 million pixels. Filename is ignored for format. Response:
image_id (UUID hex), filename, mime, size_bytes, url. GET returned url with the
same Bearer: uploader or REVIEWER/ADMIN only. Unauthenticated 401, other user 403,
unknown 404. Private/no-store caching; old /uploads URLs now 404. Attach tokens to
image-loader requests (Glide/OkHttp) without placing tokens in URL query strings.
Relative URLs resolve against base origin, not `/api/v1/api/v1`.

POST `/ai/detect` multipart file is public in this API revision. Response:
model_version=SafeRoute-YOLOv12s-v1.1, device=cpu, inference_time_ms, detections[].
Each item: class_code D00/D10/D20/D40/Repair, optional class_name, confidence [0,1],
**bbox normalized [xmin,ymin,xmax,ymax]**, all coordinates [0,1] with positive area.
Empty detections is successful real inference; unavailable runtime=503, inference
failure/OOM=500. Invalid/truncated/MIME mismatch=400; byte/pixel limit=413.
Always allow manual correction/report creation when AI is unavailable or empty.
Confidence is not severity; Repair is a class, never RESOLVED.

## Report, vote and review

POST `/reports` Bearer, 201; example tested body:

```json
{"latitude":10.005,"longitude":106,"category_user":"Repair","severity":"MEDIUM","description":"User confirmed road condition","ai_detections":[{"class_code":"D40","confidence":0.8,"bbox":[0.1,0.2,0.6,0.7]}]}
```

Optional image_id must reference your own unbound upload. Optional ai_detections
(including []) are persisted atomically; they are **USER_CONFIRMED input**, not
proof of server inference. Optional model_version is client provenance; default
USER_CONFIRMED avoids pretending the server ran YOLO. New status always
PENDING_VERIFICATION. Image reuse 409, other owner's upload 403, unknown 404.
GET `/reports/mine` Bearer; GET `/reports/{integer_id}` returns detail, images,
ai_predictions with source USER_CONFIRMED, flat ai_detections, nullable model_version,
review_logs and Android audit_logs aliases. created_at aliases observed_at (UTC
ISO timestamp). Numeric IDs are canonical; TV2 String IDs require Gson mapping.
No invented reporter_name/address; remove client hardcoded address/model fallbacks
before a real demo. Severity is independently supplied LOW/MEDIUM/HIGH.

POST `/reports/{id}/votes` Bearer, 201:
`{"vote_type":"SUPPORT","note":"Observed the hazard"}`. OPPOSE/UNSURE also valid;
legacy vote field accepted. Response includes both vote and vote_type. Duplicate
user/report=409. Vote never verifies a report.

GET `/review/queue` REVIEWER/ADMIN. PATCH `/reports/{id}/status` or
`/review/reports/{id}/status`: `{"status":"VERIFIED","reason":"Evidence checked"}`
or REJECTED; reason >=5 non-whitespace characters for BOTH decisions. Only pending
reports transition; repeated review=409. Status and audit are one transaction.
Response actor_id/report_id/status/reason/created_at. Android audit_logs fields:
reviewer_id (string), old_status, new_status, reason, timestamp. reviewer_note is
not a separate persisted field; put meaningful review explanation in reason.

## Error and compatibility notes

Error body is {detail:string}; Pydantic 422 uses detail:[validation entries].
Retry only safe reads automatically. After a write timeout, reload state before
retrying; duplicate votes and image reuse are rejected. 503 is not successful
local queue persistence. Explicit offline queueing is Android work.

Breaking changes from unsafe baseline: authenticated private images, no invented
image IDs, normalized bbox, no filename heuristics/demo-login fallback, DB outage
503, terminal review conflict 409. These are requested contract/security fixes.
AuthDto full_name, DetectionDto bbox and ReportDto ai_detections/audit_logs/
created_at now have corresponding backend fields. Android HTTP/image integration
and device E2E were not executed or modified here.
