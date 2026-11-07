"""TV4 contract probes against unchanged TV5 484721d; fixtures, not live Routes."""

from unittest.mock import Mock

import pytest

from app.modules.risk.postgis_adapter import KeyedPostgisRiskAdapter, RiskIntegrationError, route_geojson
from app.modules.risk.scoring import RiskPolicy
from app.modules.routing.schemas import LatLng, RouteCandidate, RouteRiskScore
from app.modules.routing.service import RoutingService


def candidates(n=2):
    return [
        RouteCandidate(
            id=str(i),
            distance_meters=100,
            duration_seconds=10 + i,
            polyline="fixture",
            coordinates=[(10, 106), (10.01, 106)],
        )
        for i in range(n)
    ]


def risk(score):
    return RouteRiskScore(score=score, level="LOW", hazards_count=int(score > 0), reason="MOCK_FOR_DEV")


def compare(values=None, n=2, error=False):
    google, scorer = Mock(), Mock()
    google.compute_routes.return_value = candidates(n)
    if error:
        scorer.evaluate_routes.side_effect = RuntimeError("database unavailable")
    else:
        scorer.evaluate_routes.return_value = values
    return RoutingService(google, scorer).compare_routes(
        LatLng(latitude=10, longitude=106), LatLng(latitude=10.01, longitude=106)
    )


def adapter(rows=(), weights=None):
    session = Mock()
    session.execute.return_value.mappings.return_value = rows
    return KeyedPostgisRiskAdapter(
        session, RiskPolicy("DRAFT-25m-0.5-2-6", weights or {"HIGH": 4}, 0.5), radius_m=25, medium_min=2, high_min=6
    )


@pytest.mark.parametrize("n", [0, 1, 3])
def test_cardinality_order_and_keyed_adapter(n):
    scorer = adapter()
    google = Mock()
    google.compute_routes.return_value = candidates(n)
    if n == 0:
        from app.modules.routing.exceptions import NoRouteFoundError

        with pytest.raises(NoRouteFoundError):
            RoutingService(google, scorer).compare_routes(
                LatLng(latitude=10, longitude=106), LatLng(latitude=10.01, longitude=106)
            )
        return
    result = RoutingService(google, scorer).compare_routes(
        LatLng(latitude=10, longitude=106), LatLng(latitude=10.01, longitude=106)
    )
    assert [r.id for r in result.routes] == [c.id for c in candidates(n)]
    assert all(r.risk.score == 0 for r in result.routes)


def test_shuffled_keys_and_zero_vs_none():
    response = compare({"1": risk(4), "0": risk(0)})
    assert [(r.id, r.risk.score) for r in response.routes] == [("0", 0), ("1", 4)]
    unavailable = compare({}, error=True)
    assert [r.id for r in unavailable.routes] == ["0", "1"]
    assert all(r.risk is None for r in unavailable.routes)


@pytest.mark.parametrize("mapping", [{}, {"0": risk(0), "unexpected": risk(1)}])
def test_tv4_rejects_missing_or_extra(mapping):
    scorer = adapter()
    scorer.evaluate_by_id = Mock(return_value=mapping)
    with pytest.raises(RiskIntegrationError):
        scorer.evaluate_routes(candidates(1))


def test_candidate_geojson_agrees():
    assert candidates(1)[0].to_geojson() == route_geojson(candidates(1)[0])


def test_draft_pending_weight_thresholds_and_counts():
    rows = [
        dict(route_id="0", report_id=i, status=status, severity="HIGH")
        for i, status in enumerate(["VERIFIED", "PENDING_VERIFICATION", "REJECTED"], 1)
    ]
    result = adapter(rows).evaluate_routes(candidates(1))["0"]
    assert (result.score, result.level, result.hazards_count) == (6, "HIGH", 2)
    assert result.pending_hazards_count == 1
    pending = adapter(rows[1:2]).evaluate_routes(candidates(1))["0"]
    assert (pending.score, pending.level) == (2, "MEDIUM")


def test_unknown_severity_is_unavailable_not_zero():
    row = dict(route_id="0", report_id=1, status="VERIFIED", severity="UNKNOWN")
    scorer = adapter([row])
    with pytest.raises(ValueError):
        scorer.evaluate_routes(candidates(1))
    google = Mock()
    google.compute_routes.return_value = candidates(1)
    response = RoutingService(google, scorer).compare_routes(
        LatLng(latitude=10, longitude=106), LatLng(latitude=10.01, longitude=106)
    )
    assert response.routes[0].risk is None


@pytest.mark.parametrize("weights", [{"HIGH": -1}, {"HIGH": float("nan")}, {"HIGH": float("inf")}, {}])
def test_invalid_severity_weights(weights):
    with pytest.raises(ValueError):
        RiskPolicy("DRAFT", weights, 0.5)


def test_single_unavailable_not_no_hazards():
    response = compare({}, n=1, error=True)
    assert "Chưa ghi nhận" not in response.recommendation_reason


def test_partial_risk_does_not_claim_comparative_safety():
    response = compare({"0": risk(4)})
    assert response.risk_assessment_status == "PARTIAL"
    assert response.recommendation_basis == "DURATION_ETA_ONLY"
    assert "không thể so sánh" in response.recommendation_reason


def test_unavailable_cannot_beat_known_score():
    response = compare({"0": risk(1000)})
    assert response.recommended_route_id != "1"


def test_extra_risk_keys_rejected():
    result = compare({"0": risk(1), "1": risk(2), "extra": risk(3)})
    assert result.risk_assessment_status == "INVALID"
    assert all(r.risk is None for r in result.routes)
