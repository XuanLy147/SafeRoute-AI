# TV1 — Map and routing handoff

Backend branch: `integration/tv345-backend-handoff`.
Implementation snapshot: `a11875074dd14da9cc1af18fa10099c45e08ba72` (source/security/runtime commits b7c0b87, 828f1ef, 005366b).
Final delivery SHA is reported by Git and the final handoff message; subsequent
contract documentation changes do not change this provenance anchor.
Contract revision: `tv345-contract-2026-11-07`, API `/api/v1`.

Local URLs when started per LOCAL_DEVELOPMENT: host `http://127.0.0.1:8000`,
Android Emulator `http://10.0.2.2:8000`. No staging URL has been deployed.

## Map

GET `/api/v1/reports` returns a JSON array. Optional `status`, `category`,
`west,south,east,north` (all four required, west<east/south<north), `limit` (default
200, maximum 1000), `offset`. Coordinates use latitude/longitude named fields.
Bounds do not wrap the antimeridian; split such queries client-side. REJECTED
is always excluded, even if explicitly requested. PENDING_VERIFICATION markers
must visibly say unverified. VERIFIED markers are reviewed recorded hazards.
Empty 200 array means no recorded matches, not guaranteed safe conditions.

Map item fields: numeric id/user_id, category_user, severity LOW/MEDIUM/HIGH,
status, latitude/longitude, observed_at and created_at ISO timestamps, nullable
description/image_url, votes totals and support_votes/oppose_votes/unsure_votes.
Images are private: a URL does not grant access; show a placeholder on 403.
No fabricated reporter/address/geocoding fields are supplied.

## Routing

POST `/api/v1/routes/compare` request (validated by HTTP fixture tests):

```json
{"origin":{"latitude":10,"longitude":106},"destination":{"latitude":10.01,"longitude":106},"travel_mode":"TWO_WHEELER"}
```

Modes: TWO_WHEELER, DRIVE, WALK, BICYCLE. Real Google key required. Missing key
returns 502; runtime never injects fixture routes. Rate limit 429, timeout 504,
upstream/auth failure 502, invalid/same coordinates 400 (range validation 422),
no candidates 404, invalid risk configuration 503.

Response has origin/destination/travel_mode, routes[], recommended_route_id,
recommendation_reason, disclaimer, risk_assessment_status, recommendation_basis,
risk_diagnostic, risk_policy_revision and risk_policy_status. Each route:
`id, summary, distance_meters, duration_seconds, polyline, risk`.
Risk is nullable; when present: score, level, hazards_count,
verified_hazards_count, pending_hazards_count, reason. Seconds/meters are raw
units; decode Google encoded polyline to (latitude,longitude), never swap them
with GeoJSON's [longitude,latitude]. Google computeRoutes has no stable route ID
in this field mask: adapter assigns route_1..N in response order. IDs are scoped
to one comparison and preserved through scoring; do not reuse across requests.
Exactly one Google candidate produces one UI route; no invented alternatives.

| Assessment | UI behavior |
|---|---|
| AVAILABLE | All candidates scored; lowest recorded risk, tie-break ETA/distance/ID |
| PARTIAL | Some scores missing; ETA-only suggestion, never claim lowest risk overall |
| UNAVAILABLE | No usable risk; display unknown, ETA-only suggestion |
| INVALID | Contract violation; all risk objects cleared, ETA-only suggestion |

One candidate uses SINGLE_ROUTE basis. Multiple available candidates use
SAFETY_LOWEST_RISK; other comparisons use DURATION_ETA_ONLY. Score 0 with
hazards_count>0 means zero weighted score, not no reports. Missing risk is null,
never numeric 999 or zero. policy status DEMO_UNAPPROVED must be labeled demo.
Production numeric approval is outstanding and risk is disabled by default.
Disclaimer: recommendation reflects recorded hazards, never absolute safety.

## Android mapping / remaining client work

The inspected frontend Route/RouteComparison are presentation models, not Retrofit
JSON DTOs. TV2's shared HTTP adapter should map summary→name,
duration_seconds→durationSeconds, distance_meters→distanceMeters, risk.score→
riskScore, risk verified/pending counts→counts, decoded polyline→points;
recommended_route_id→recommendedId and reason+disclaimer→scoreDescription.
Carry availability/basis into UI; a bare nullable score cannot explain partial
risk. MapRoutingDataSource failures map 401/403/429/network/5xx separately. No Android
source was changed. GPS denial/manual coordinate choice stays client-side.

Fixtures: backend/app/modules/routing/fixtures.py is MOCK_FOR_DEV only;
test_tv345_integration.py proves HTTP→real PostGIS→keyed risk joins with synthetic
routes near/far, scores 6/0 and far recommendation. See API_CONTRACT_LOCK and
runtime-generated OpenAPI for exact nullable fields and enums.
