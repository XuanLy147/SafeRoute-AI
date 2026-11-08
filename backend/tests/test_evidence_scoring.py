from datetime import UTC, datetime, timedelta

import pytest

from app.modules.routing.evidence_scoring import assess_comparison, contribution
from app.modules.routing.schemas import EvaluatedRoute, LatLng, RouteCompareResponse, RouteHazard, TravelMode

NOW = datetime(2026, 11, 8, tzinfo=UTC)

def hazard(id="1", severity="HIGH", category="D40", status="VERIFIED", days=0, lat=10):
    return RouteHazard(id=id, category=category, status=status, severity=severity,
                       latitude=lat, longitude=106, observed_at=NOW-timedelta(days=days))

def route(id, seconds, hazards):
    return EvaluatedRoute(id=id, summary=id, duration_seconds=seconds, distance_meters=1000,
                          polyline="fixture", nearby_hazards=hazards)

def compare(routes):
    result = RouteCompareResponse(origin=LatLng(latitude=10, longitude=106),
        destination=LatLng(latitude=11, longitude=106), travel_mode=TravelMode.DRIVE, routes=routes)
    assess_comparison(result, NOW)
    return RouteCompareResponse.model_validate(result.model_dump())

@pytest.mark.parametrize(("severity","expected"), [("LOW",3),("MEDIUM",4.5),("HIGH",6)])
def test_severity(severity,expected):
    assert float(contribution(hazard(severity=severity),NOW)) == expected

@pytest.mark.parametrize(("days","factor"),[(7,1),(7.001,.7),(30,.7),(30.001,.4)])
def test_age_boundaries(days,factor):
    assert float(contribution(hazard(days=days),NOW)) == pytest.approx(6*factor)

def test_pending_multiplier():
    assert float(contribution(hazard(status="PENDING_VERIFICATION"),NOW)) == 1.8

def test_selection_caps_detour_then_high_then_points():
    a=route("a",1200,[hazard()])
    b=route("b",1380,[hazard(severity="LOW")])
    c=route("c",1920,[])
    result=compare([a,b,c])
    assert result.recommended_route_id == "b"
    assert result.recommendation_basis == "SAFETY_LOWEST_RISK"
    assert result.risk_assessment_status == "AVAILABLE"

def test_twenty_percent_boundary_and_high_priority():
    a=route("a",100,[hazard()])
    b=route("b",120,[hazard(str(i),severity="LOW",lat=10+i*.001) for i in range(3)])
    c=route("c",121,[])
    assert compare([a,b,c]).recommended_route_id == "b"
    assert b.risk.score == 9 > a.risk.score

@pytest.mark.parametrize("bad", [hazard(severity="UNKNOWN"),hazard(category="UNKNOWN"),hazard(days=-1)])
def test_unknown_never_zero_and_prevents_safety_ranking(bad):
    a,b=route("a",100,[bad]),route("b",120,[])
    result=compare([a,b])
    assert a.risk is None and a.unscored_hazards_count==1
    assert result.risk_assessment_status == "PARTIAL"
    assert result.recommendation_basis == "DURATION_ETA_ONLY"

def test_empty_vs_failed_lookup():
    a,b=route("a",100,[]),route("b",100,None)
    result=compare([a,b])
    assert a.risk.score==0 and b.risk is None
    assert result.risk_assessment_status=="PARTIAL"

def test_duplicates_conservative_verified_and_same_evidence_counts():
    a=route("a",100,[hazard("1",severity="LOW"),hazard("2"),
                     hazard("3",status="PENDING_VERIFICATION")])
    compare([a])
    assert len(a.nearby_hazards)==1
    assert a.nearby_hazards[0].source_report_ids==["1","2","3"]
    assert a.risk.score==6 and a.risk.verified_hazards_count==1
    assert a.severity_counts=={"LOW":0,"MEDIUM":0,"HIGH":1,"UNKNOWN":0}
    assert a.verified_high_count==1
    assert sum(h.score_contribution for h in a.nearby_hazards)==a.risk.score

def test_unknown_duplicate_not_hidden_and_nearby_distinct_not_merged():
    a=route("a",100,[hazard(),hazard("2",severity="UNKNOWN"),hazard("3",lat=10.00001)])
    compare([a])
    assert a.risk is None and len(a.nearby_hazards)==2

def test_zero_distance_no_infinite_density_and_stable_tie():
    a,b=route("b",0,[]),route("a",0,[])
    a.distance_meters=b.distance_meters=0
    result=compare([a,b])
    assert result.recommended_route_id=="a"
    assert a.risk_density_per_km is None

def test_repair_is_not_resolved():
    a=route("a",100,[hazard(category="Repair")])
    compare([a])
    assert a.risk.score==2

def test_single_route_no_fake_alternatives():
    result=compare([route("a",100,[])])
    assert len(result.routes)==1 and result.recommendation_basis=="SINGLE_ROUTE"


def test_contract_rejects_inconsistent_sum():
    result=compare([route("a",100,[hazard()])])
    payload=result.model_dump()
    payload["routes"][0]["risk"]["score"]=999
    with pytest.raises(ValueError, match="Contribution sum mismatch"):
        RouteCompareResponse.model_validate(payload)
