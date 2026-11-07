"""DRAFT TV3/TV4/TV5 bridge. Not wired into public API.

Requires the TV3 entities/session and TV5 schemas in an integrated checkout.
No schema/session creation; caller owns transaction and approved policy.
"""

import json
from math import isfinite

from geoalchemy2 import Geography
from sqlalchemy import cast, func, literal, select, union_all

from app.models.entities import Report
from app.modules.routing.schemas import RouteRiskScore
from app.modules.risk.scoring import NearbyReport, RiskPolicy, RouteReports, score_routes


class RiskIntegrationError(ValueError):
    """Incomplete or inconsistent input/output; never interpreted as zero risk."""


def route_geojson(candidate):
    coordinates = candidate.coordinates
    if len(coordinates) < 2:
        raise RiskIntegrationError("Route needs at least two decoded coordinates")
    for lat, lon in coordinates:
        if not isfinite(lat) or not isfinite(lon) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
            raise RiskIntegrationError("Invalid route coordinate")
    if len(set(coordinates)) < 2:
        raise RiskIntegrationError("Route geometry has no distinct points")
    return {"type": "LineString", "coordinates": [[lon, lat] for lat, lon in coordinates]}


def nearby_statement(candidates, radius_m):
    """One statement gives all candidates one PostgreSQL statement snapshot.

    Geography operands make ST_DWithin's radius meters. The location column is
    unwrapped so TV3's idx_reports_location GiST index remains applicable.
    """
    if isinstance(radius_m, bool) or not isfinite(radius_m) or radius_m <= 0:
        raise RiskIntegrationError("Radius must be finite and positive, in meters")
    ids = [candidate.id for candidate in candidates]
    if any(not isinstance(id_, str) or not id_.strip() for id_ in ids) or len(ids) != len(set(ids)):
        raise RiskIntegrationError("Candidate IDs must be nonempty and unique")
    statements = []
    for candidate in candidates:
        geometry = func.ST_SetSRID(func.ST_GeomFromGeoJSON(json.dumps(route_geojson(candidate))), 4326)
        geography = cast(geometry, Geography(geometry_type="LINESTRING", srid=4326))
        statements.append(select(
            literal(candidate.id).label("route_id"), Report.id.label("report_id"),
            Report.status, Report.severity,
        ).where(func.ST_DWithin(Report.location, geography, radius_m)))
    return union_all(*statements) if statements else None


class PostgisRiskAdapter:
    def __init__(self, session, policy: RiskPolicy, *, radius_m: float,
                 medium_min: float, high_min: float):
        # Thresholds and severity weights must come from an explicitly selected
        # policy. Do not silently translate TV3 category-based seed weights.
        if any(isinstance(v, bool) or not isfinite(v) for v in (medium_min, high_min)):
            raise RiskIntegrationError("Risk level thresholds must be finite")
        if not 0 < medium_min < high_min:
            raise RiskIntegrationError("Require 0 < medium_min < high_min")
        nearby_statement([], radius_m)
        self.session, self.policy = session, policy
        self.radius_m, self.medium_min, self.high_min = radius_m, medium_min, high_min

    def evaluate_by_id(self, candidates):
        statement = nearby_statement(candidates, self.radius_m)
        if statement is None:
            return {}
        grouped = {candidate.id: [] for candidate in candidates}
        # DB failures propagate to the caller; never return fabricated zero scores.
        for row in self.session.execute(statement).mappings():
            if row["route_id"] not in grouped:
                raise RiskIntegrationError("Query returned an unknown route ID")
            if type(row["report_id"]) is not int:
                raise RiskIntegrationError("Expected TV3 integer report ID")
            status = getattr(row["status"], "value", row["status"])
            grouped[row["route_id"]].append(NearbyReport(str(row["report_id"]), status, row["severity"]))
        risks = score_routes(tuple(RouteReports(id_, tuple(rows)) for id_, rows in grouped.items()), self.policy)
        results = {}
        for risk in risks:
            verified = sum(c.status == "VERIFIED" for c in risk.contributions)
            pending = sum(c.status == "PENDING_VERIFICATION" for c in risk.contributions) + risk.excluded_pending_count
            level = "HIGH" if risk.score >= self.high_min else "MEDIUM" if risk.score >= self.medium_min else "LOW"
            results[risk.route_id] = RouteRiskScore(
                score=risk.score, level=level, hazards_count=verified + pending,
                verified_hazards_count=verified, pending_hazards_count=pending,
                reason=(f"Policy {risk.policy_revision}; {verified} verified, {pending} unverified nearby reports; "
                        f"pending multiplier={self.policy.pending_multiplier:g}; "
                        f"{risk.excluded_rejected_count} rejected excluded. "
                        "Score reflects recorded hazards only; zero does not guarantee safety."),
            )
        if set(results) != set(grouped):
            raise RiskIntegrationError("Scorer returned missing or extra route IDs")
        return results

    def evaluate_routes(self, candidates):
        """Compatibility facade for current TV5 positional protocol.

        The internal join uses route IDs, not database order. TV5 should adopt
        evaluate_by_id plus exact-key validation for arbitrary scorer providers.
        """
        results = self.evaluate_by_id(candidates)
        if set(results) != {candidate.id for candidate in candidates}:
            raise RiskIntegrationError("Scorer returned missing or extra route IDs")
        return [results[candidate.id] for candidate in candidates]
