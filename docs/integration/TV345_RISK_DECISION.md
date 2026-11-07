# Decision: versioned recorded-risk policy

Revision of API decision: tv345-contract-2026-11-07. Owner scopes TV3/TV4/TV5.
Production numeric policy approval: **NOT PROVIDED**.

Formula per route: sum over distinct nearby report IDs of severity weight,
multiplied by 1 for VERIFIED or pending_multiplier for PENDING_VERIFICATION;
REJECTED contributes neither score nor hazard count. Radius uses geography
ST_DWithin meters. Same report may contribute to two different routes. Counts
include pending even when its multiplier is zero. Score zero is not no reports.

Explicit demo profile DEMO-2026-11-07: radius=25m; severity LOW=1, MEDIUM=2,
HIGH=4; pending=.5; LOW<2, MEDIUM [2,6), HIGH>=6. These are synthetic test settings,
not field-validated production weights. risk_weights DB category seeds are not
consumed: category and severity are independent and no approved mapping exists.

RISK_POLICY_JSON is absent by default: risk UNAVAILABLE, honest ETA fallback.
Malformed policy yields HTTP 503 instead of silent fallback. A configured policy
must validate every weight/radius/threshold and supply revision. Production rejects
approved=false; only accountable owner approval may set approved=true.

Database/scorer failures yield UNAVAILABLE with no fabricated zero. Invalid output
keys/types/cardinality yield INVALID, all scores cleared. Missing scores yield
PARTIAL (or UNAVAILABLE if none). Only AVAILABLE compares risk across candidates;
otherwise ETA tie-break (duration, distance, ID) with caveat. Empty candidates 404.
