"""Real PostGIS only. Opt in with a disposable, already migrated test DB.

No SQLite/mock fallback. Migrations must run separately; tests roll back inserts.
Set SAFEROUTE_TEST_DATABASE_URL to a database named saferoute_test_*.
"""

import os

import pytest
from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url

from app.db.geo import make_point
from app.db.session import SessionLocal
from app.models.entities import Report, ReportStatus, User, UserRole
from app.modules.risk.postgis_adapter import KeyedPostgisRiskAdapter, PostgisRiskAdapter
from app.modules.risk.scoring import RiskPolicy
from app.modules.routing.schemas import RouteCandidate


@pytest.fixture
def db():
    url = os.environ.get("SAFEROUTE_TEST_DATABASE_URL")
    if not url:
        pytest.skip("BLOCKED: explicit disposable PostGIS database URL required")
    parsed = make_url(url)
    if parsed.get_backend_name() != "postgresql" or not (parsed.database or "").startswith("saferoute_test_"):
        pytest.fail("Require PostgreSQL database named saferoute_test_*; no fallback")
    engine = create_engine(url)
    with engine.connect() as conn:
        transaction = conn.begin()
        session = SessionLocal(bind=conn, join_transaction_mode="create_savepoint")
        try:
            assert conn.scalar(text("SELECT PostGIS_Version()"))
            assert conn.scalar(text("SELECT version_num FROM alembic_version")) == "0003_role_audit"
            yield session
        finally:
            session.close()
            transaction.rollback()
    engine.dispose()


def route(id_, lon):
    # SYNTHETIC spatial fixture, not a Google Routes live response.
    return RouteCandidate(
        id=id_, distance_meters=1000, duration_seconds=60, polyline="", coordinates=[(10.0, lon), (10.01, lon)]
    )


def insert_reports(db):
    user = User(email="tv4-spatial-fixture@example.invalid", password_hash="not-a-login", role=UserRole.USER)
    db.add(user)
    db.flush()
    for status in ReportStatus:
        db.add(
            Report(
                user_id=user.id, category_user="D40", severity="HIGH", status=status, location=make_point(106, 10.005)
            )
        )
    db.flush()


def scorer(db, radius=50):
    return PostgisRiskAdapter(
        db, RiskPolicy("MOCK_FOR_DEV-spatial", {"HIGH": 4}, 0), radius_m=radius, medium_min=2, high_min=8
    )


def test_real_near_far_parallel_and_statuses(db):
    insert_reports(db)
    result = scorer(db).evaluate_by_id([route("near", 106), route("parallel", 106.01)])
    assert result["near"].score == 4
    assert result["near"].hazards_count == 2
    assert result["near"].pending_hazards_count == 1
    assert result["parallel"].score == 0
    assert set(result) == {"near", "parallel"}


def test_shared_hazard_counts_once_per_route(db):
    insert_reports(db)
    a, b = route("a", 106), route("b", 106.001)
    a.coordinates = a.coordinates + list(reversed(a.coordinates))
    result = scorer(db, radius=200).evaluate_routes([a, b])
    assert [item.score for item in result] == [4, 4]


def test_geography_srid_axis_and_index(db):
    insert_reports(db)
    row = db.execute(
        text(
            "SELECT ST_X(location::geometry), ST_Y(location::geometry), ST_SRID(location::geometry) FROM reports WHERE description IS NULL AND category_user='D40' LIMIT 1"
        )
    ).one()
    assert tuple(row) == (106, 10.005, 4326)
    definition = db.scalar(
        text("SELECT indexdef FROM pg_indexes WHERE tablename='reports' AND indexname='idx_reports_location'")
    )
    assert "USING gist (location)" in definition


def test_meter_distance_with_projected_point(db):
    # 40/60m offsets away from a 50m boundary avoid floating-point ambiguity.
    result = db.execute(
        text("""
        SELECT ST_DWithin(p, ST_Project(p, 40, 0), 50),
               ST_DWithin(p, ST_Project(p, 60, 0), 50)
        FROM (SELECT ST_SetSRID(ST_MakePoint(106,10),4326)::geography p) s
    """)
    ).one()
    assert tuple(result) == (True, False)


def test_keyed_draft_policy_25m_with_boundary_reports(db):
    insert_reports(db)
    user_id = db.scalar(text("SELECT id FROM users WHERE email='tv4-spatial-fixture@example.invalid'"))
    for meters in (24, 26):
        lon, lat = db.execute(
            text("""
            SELECT ST_X(p::geometry), ST_Y(p::geometry)
            FROM (SELECT ST_Project(ST_SetSRID(ST_MakePoint(106,10.005),4326)::geography,
                                   :meters, pi()/2) p) s
        """),
            {"meters": meters},
        ).one()
        db.add(
            Report(
                user_id=user_id,
                category_user="D40",
                severity="HIGH",
                status=ReportStatus.VERIFIED,
                location=make_point(lon, lat),
            )
        )
    db.flush()
    # These are test policy values, not an approved production configuration.
    keyed = KeyedPostgisRiskAdapter(
        db, RiskPolicy("DRAFT-25m-0.5-2-6", {"HIGH": 4}, 0.5), radius_m=25, medium_min=2, high_min=6
    )
    result = keyed.evaluate_routes([route("far", 106.01), route("near", 106)])
    assert list(result) == ["far", "near"]
    assert result["far"].score == 0
    # Base verified 4 + pending 2 + report 24m away 4; 26m/rejected excluded.
    assert (result["near"].score, result["near"].level) == (10, "HIGH")
    assert result["near"].hazards_count == 3
    assert result["near"].pending_hazards_count == 1


def test_route_evidence_without_scoring_policy(db):
    from app.modules.routing.hazards import attach_route_hazards
    from app.modules.routing.polyline import encode_polyline
    from app.modules.routing.schemas import EvaluatedRoute
    insert_reports(db)
    routes = [EvaluatedRoute(id=id_, summary="fixture", distance_meters=1000,
        duration_seconds=60, polyline=encode_polyline([(10, lon), (10.01, lon)]))
        for id_, lon in [("near", 106), ("far", 106.01)]]
    attach_route_hazards(routes, db)
    assert routes[0].risk is None
    assert {h.status for h in routes[0].nearby_hazards} == {"VERIFIED", "PENDING_VERIFICATION"}
    assert len(routes[0].nearby_hazards) == 2
    assert routes[0].nearby_hazards[0].latitude == pytest.approx(10.005)
    assert routes[0].nearby_hazards[0].longitude == pytest.approx(106)
    assert routes[1].nearby_hazards == []
    assert routes[0].hazard_radius_meters == 30


def test_route_evidence_query_failure_is_not_empty():
    from app.modules.routing.hazards import attach_route_hazards
    from app.modules.routing.polyline import encode_polyline
    from app.modules.routing.schemas import EvaluatedRoute
    class BrokenSession:
        def execute(self, statement):
            raise RuntimeError("database unavailable")
    route = EvaluatedRoute(id="a", summary="fixture", distance_meters=1000,
        duration_seconds=60, polyline=encode_polyline([(10, 106), (10.01, 106)]))
    with pytest.raises(RuntimeError):
        attach_route_hazards([route], BrokenSession())
    assert route.nearby_hazards is None
