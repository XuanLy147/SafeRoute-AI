# API contract lock

Backend branch: `integration/tv345-backend-handoff`.
Implementation snapshot: `a11875074dd14da9cc1af18fa10099c45e08ba72` (source/security/runtime commits b7c0b87, 828f1ef, 005366b).
Final delivery SHA is reported by Git and the final handoff message; subsequent
contract documentation changes do not change this provenance anchor.
Contract revision: `tv345-contract-2026-11-07`, API `/api/v1`.


Source of truth: `docs/api/openapi.json`, generated from the integrated FastAPI app.
All paths below are relative to the origin; schemas are the exact names in OpenAPI.
Errors: {detail:string}; validation 422: {detail:[...]}. JWT Bearer unless Public.

| Method | Endpoint | Request | Success response | Auth |
|---|---|---|---|---|
| GET | /health | query/path params; see OpenAPI | 200 JSON/file | Public |
| GET | /api/v1/ping | query/path params; see OpenAPI | 200 PingResponse | Public |
| POST | /api/v1/auth/login | application/json: LoginRequest | 200 TokenResponse | Public |
| POST | /api/v1/auth/register | application/json: RegisterRequest | 200 TokenResponse | Public |
| GET | /api/v1/auth/me | query/path params; see OpenAPI | 200 UserProfileResponse | Bearer |
| POST | /api/v1/images | multipart/form-data: Body_upload_image_api_v1_images_post | 200 ImageUploadResponse | Bearer |
| GET | /api/v1/images/{image_id} | query/path params; see OpenAPI | 200 JSON/file | Bearer |
| POST | /api/v1/reports | application/json: ReportCreateRequest | 201 ReportItemResponse | Bearer |
| GET | /api/v1/reports | query/path params; see OpenAPI | 200 ReportItemResponse[] | Public |
| GET | /api/v1/reports/mine | query/path params; see OpenAPI | 200 ReportItemResponse[] | Bearer |
| GET | /api/v1/reports/{report_id} | query/path params; see OpenAPI | 200 ReportDetailResponse | Public |
| POST | /api/v1/reports/{report_id}/votes | application/json: VoteCreateRequest | 201 VoteResponse | Bearer |
| GET | /api/v1/review/queue | query/path params; see OpenAPI | 200 ReportItemResponse[] | REVIEWER/ADMIN |
| PATCH | /api/v1/review/reports/{report_id}/status | application/json: ReviewStatusPatchRequest | 200 ReviewResponse | REVIEWER/ADMIN |
| PATCH | /api/v1/reports/{report_id}/status | application/json: ReviewStatusPatchRequest | 200 ReviewResponse | REVIEWER/ADMIN |
| POST | /api/v1/ai/detect | multipart/form-data: Body_detect_road_hazards_api_v1_ai_detect_post | 200 AiDetectResponse | Public |
| POST | /api/v1/routes/compare | application/json: RouteCompareRequest | 200 RouteCompareResponse | Public |
| GET | /ready | query/path params; see OpenAPI | 200 JSON/file | Public |

## Locked semantics

Status: PENDING_VERIFICATION/VERIFIED/REJECTED. Roles USER/REVIEWER/ADMIN. Votes SUPPORT/OPPOSE/UNSURE.
AI classes D00/D10/D20/D40/Repair; confidence [0,1], bbox normalized xyxy.
Route ETA seconds; distance/radius meters; Google decoded lat/lon, GeoJSON lon/lat.
Nullable image_url/description/full_name/model_version/risk/recommendation metadata remain null, never fabricated.
Numeric DB IDs; timestamp UTC ISO; image_id UUID hex. Array cardinality preserves Google candidates (zero→404).
Risk AVAILABLE/PARTIAL/UNAVAILABLE/INVALID; reasons distinguish policy absence, DB unavailable, scorer fault and invalid contract.
All detail response field schemas, bounds, required fields and HTTP errors are exported in OpenAPI.
Report review aliases have the same ReviewResponse schema.

## Runtime and rollout limits

No staging URL. Production numeric risk policy unapproved; demo values explicitly labeled.
Cloud/IAM/live Google and Android device integration are outside local PASS evidence.
Private images require Bearer; no static public URLs. See TV1/TV2 handoffs for breaking changes.
