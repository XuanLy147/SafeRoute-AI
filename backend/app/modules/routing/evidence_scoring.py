"""Versioned experimental policy authorized by the project owner; not a safety standard."""
from collections import Counter
from datetime import UTC, datetime
from decimal import Decimal

from app.modules.routing.schemas import RouteRiskScore

REVISION = "recorded-hazards-v1-2026-11-08"
CATEGORY_WEIGHTS = {"D00": 1, "D10": 1, "D20": 2, "D40": 3,
                    "REPAIR": 1, "NỨT ĐƯỜNG": 1, "VẬT CẢN": 3,
                    "NGẬP NƯỚC": 3, "ĐƯỜNG GỒ GHỀ": 2}
SEVERITY_WEIGHTS = {"LOW": Decimal("1"), "MEDIUM": Decimal("1.5"), "HIGH": Decimal("2")}

def normalize(category):
    return category.strip().upper()

def contribution(hazard, now):
    weight = CATEGORY_WEIGHTS.get(normalize(hazard.category))
    severity = SEVERITY_WEIGHTS.get(hazard.severity)
    observed = hazard.observed_at
    if weight is None or severity is None or observed is None or observed.tzinfo is None or observed > now:
        return None
    age = (now - observed).total_seconds() / 86400
    freshness = Decimal("1") if age <= 7 else Decimal("0.7") if age <= 30 else Decimal("0.4")
    confidence = Decimal("1") if hazard.status == "VERIFIED" else Decimal("0.3")
    return Decimal(weight) * severity * freshness * confidence

def score_route(route, now):
    if route.nearby_hazards is None:
        return
    groups = {}
    for h in route.nearby_hazards:
        groups.setdefault((normalize(h.category), h.latitude, h.longitude), []).append(h)
    evidence = []
    for group in groups.values():
        # No spatial guess: only identical category + coordinates are grouped.
        # Unknown severity/category/time is retained, never hidden by a known duplicate.
        unknown = [h for h in group if contribution(h, now) is None]
        verified = [h for h in group if h.status == "VERIFIED"]
        pool = unknown or verified or group
        h = max(pool, key=lambda x: (SEVERITY_WEIGHTS.get(x.severity, Decimal("0")),
                                    contribution(x, now) or Decimal("0"), x.id)).model_copy(deep=True)
        h.source_report_ids = sorted(x.id for x in group)
        value = contribution(h, now)
        h.score_contribution = None if value is None else float(value)
        evidence.append(h)
    route.nearby_hazards = sorted(evidence, key=lambda h: h.id)
    levels = Counter(h.severity if h.severity in SEVERITY_WEIGHTS else "UNKNOWN" for h in evidence)
    route.severity_counts = {k: levels[k] for k in ("LOW", "MEDIUM", "HIGH", "UNKNOWN")}
    route.verified_high_count = sum(h.severity == "HIGH" and h.status == "VERIFIED" for h in evidence)
    route.unscored_hazards_count = sum(h.score_contribution is None for h in evidence)
    if route.unscored_hazards_count:
        route.risk = None
        return
    total = sum((Decimal(str(h.score_contribution)) for h in evidence), Decimal("0"))
    verified_count = sum(h.status == "VERIFIED" for h in evidence)
    density = total / Decimal(str(route.distance_meters / 1000)) if route.distance_meters > 0 else None
    route.risk = RouteRiskScore(
        score=float(total), level=None, hazards_count=len(evidence),
        verified_hazards_count=verified_count, pending_hazards_count=len(evidence)-verified_count,
        reason=(f"{levels['HIGH']} mức cao · {levels['MEDIUM']} mức trung bình · {levels['LOW']} mức thấp. "
                + (f"Mật độ: {density:.2f} điểm/km. " if density is not None else "")
                + "Điểm thấp hơn nghĩa là ít nguy cơ được ghi nhận hơn; thang điểm thử nghiệm."),
    )
    route.risk_density_per_km = None if density is None else float(density)

def assess_comparison(result, now=None):
    now = now or datetime.now(UTC)
    result.assessed_at = now
    result.risk_policy_revision = REVISION
    result.risk_policy_status = "EXPERIMENTAL"
    for route in result.routes:
        score_route(route, now)
    count = sum(r.risk is not None for r in result.routes)
    complete = count == len(result.routes) and bool(result.routes)
    result.risk_assessment_status = "AVAILABLE" if complete else "PARTIAL" if count else "UNAVAILABLE"
    result.risk_diagnostic = None if complete else "INCOMPLETE_EVIDENCE"
    if not result.routes:
        return
    fastest = min(result.routes, key=lambda r: (r.duration_seconds, r.distance_meters, r.id))
    if complete:
        eligible = [r for r in result.routes if r.duration_seconds * 5 <= fastest.duration_seconds * 6]
        best = min(eligible, key=lambda r: (r.verified_high_count, r.risk.score,
                                          r.duration_seconds, r.distance_meters, r.id))
        result.recommendation_basis = "SAFETY_LOWEST_RISK"
        result.recommendation_reason = (
            "Trong các tuyến không chậm hơn quá 20% so với tuyến nhanh nhất, ưu tiên ít nguy cơ mức cao "
            "đã xác minh, sau đó điểm nguy cơ thấp hơn. "
            f"Tuyến đề xuất có {best.verified_high_count} nguy cơ mức cao đã xác minh, "
            f"{best.risk.score:g} điểm; thêm {(best.duration_seconds-fastest.duration_seconds)/60:.1f} phút. "
            "Cảnh báo trong bán kính 30 m có thể thuộc đường bên cạnh. Không có cảnh báo không đồng nghĩa an toàn."
        )
    else:
        best = fastest
        result.recommendation_basis = "DURATION_ETA_ONLY"
        result.recommendation_reason = (
            "Chưa đủ dữ liệu để so sánh nguy cơ trên tất cả tuyến (thiếu loại, mức độ, thời điểm hoặc dữ liệu cảnh báo). "
            "Tạm đề xuất theo thời gian di chuyển ngắn nhất."
        )
    result.recommended_route_id = best.id
    if len(result.routes) == 1:
        result.recommendation_basis = "SINGLE_ROUTE"
        result.recommendation_reason = "Chỉ tìm được một tuyến. " + (
            "Điểm chỉ phản ánh cảnh báo đã ghi nhận gần tuyến." if complete else result.recommendation_reason)
