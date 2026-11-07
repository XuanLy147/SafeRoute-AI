"""MOCK_FOR_DEV contract tests; SQL compilation is not PostGIS execution."""

from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from sqlalchemy.dialects import postgresql

from app.models.entities import ReportStatus
from app.modules.risk.postgis_adapter import PostgisRiskAdapter, RiskIntegrationError, nearby_statement, route_geojson
from app.modules.risk.scoring import RiskPolicy


def candidate(id_="a", coordinates=None):
    return SimpleNamespace(
        id=id_, coordinates=coordinates if coordinates is not None else [(10.0, 106.0), (10.01, 106.0)]
    )


def adapter(rows=()):
    session = Mock()
    session.execute.return_value.mappings.return_value = rows
    return PostgisRiskAdapter(
        session, RiskPolicy("MOCK_FOR_DEV", {"HIGH": 4}, 0), radius_m=50, medium_min=2, high_min=8
    )


def row(route="a", id_=1, status=ReportStatus.VERIFIED):
    return {"route_id": route, "report_id": id_, "status": status, "severity": "HIGH"}


def test_geojson_lon_lat_and_geography_sql():
    assert route_geojson(candidate())["coordinates"][0] == [106.0, 10.0]
    compiled = nearby_statement([candidate()], 50).compile(dialect=postgresql.dialect())
    assert "ST_DWithin(reports.location" in str(compiled)
    assert "geography(LINESTRING,4326)" in str(compiled)
    assert 50 in compiled.params.values()


def test_zero_candidates_no_db():
    scorer = adapter()
    assert scorer.evaluate_routes([]) == []
    scorer.session.execute.assert_not_called()


def test_id_join_preserves_order_with_empty_route():
    scorer = adapter([row("b")])
    result = scorer.evaluate_routes([candidate("a"), candidate("b"), candidate("c")])
    assert [r.score for r in result] == [0, 4, 0]
    scorer.session.execute.assert_called_once()


def test_duplicate_reports_and_status_counts():
    scorer = adapter(
        [row(), row(), row(id_=2, status=ReportStatus.PENDING_VERIFICATION), row(id_=3, status=ReportStatus.REJECTED)]
    )
    result = scorer.evaluate_routes([candidate()])[0]
    assert result.score == 4
    assert (result.hazards_count, result.verified_hazards_count, result.pending_hazards_count) == (2, 1, 1)
    assert "unverified" in result.reason and "zero does not guarantee safety" in result.reason


def test_conflicting_report_snapshot():
    with pytest.raises(ValueError, match="Conflicting"):
        adapter([row(), row(status=ReportStatus.REJECTED)]).evaluate_routes([candidate()])


@pytest.mark.parametrize(
    "coordinates", [[], [(10, 106)], [(10, 106), (10, 106)], [(91, 106), (10, 106)], [(10, float("nan")), (10, 106)]]
)
def test_invalid_geometry(coordinates):
    with pytest.raises(RiskIntegrationError):
        adapter().evaluate_routes([candidate(coordinates=coordinates)])


def test_duplicate_route_ids():
    with pytest.raises(RiskIntegrationError):
        adapter().evaluate_routes([candidate(), candidate()])


def test_database_failure_propagates():
    scorer = adapter()
    scorer.session.execute.side_effect = RuntimeError("database unavailable")
    with pytest.raises(RuntimeError):
        scorer.evaluate_routes([candidate()])


def test_unknown_route_rejected():
    with pytest.raises(RiskIntegrationError):
        adapter([row("unknown")]).evaluate_routes([candidate()])


def test_missing_score_rejected():
    scorer = adapter()
    scorer.evaluate_by_id = Mock(return_value={})
    with pytest.raises(RiskIntegrationError):
        scorer.evaluate_routes([candidate()])


def test_real_tv5_schema_and_service_accept_adapter():
    from app.modules.routing.schemas import LatLng, RouteCandidate
    from app.modules.routing.service import RoutingService

    candidates = [
        RouteCandidate(
            id=id_, distance_meters=100, duration_seconds=10, polyline="fixture", coordinates=[(10, 106), (10.01, 106)]
        )
        for id_ in ("a", "b")
    ]
    google = Mock()
    google.compute_routes.return_value = candidates
    service = RoutingService(adapter=google, risk_scorer=adapter([row("b")]))
    response = service.compare_routes(LatLng(latitude=10, longitude=106), LatLng(latitude=10.01, longitude=106))
    assert [(r.id, r.risk.score) for r in response.routes] == [("a", 0), ("b", 4)]


@pytest.mark.parametrize("radius", [0, -1, float("inf"), float("nan"), True])
def test_invalid_radius(radius):
    with pytest.raises(RiskIntegrationError):
        nearby_statement([], radius)
