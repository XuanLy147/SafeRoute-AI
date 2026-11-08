"""Read all candidate evidence in one SQL snapshot, never one snapshot per route."""
import json

from geoalchemy2 import Geography, Geometry
from sqlalchemy import cast, func, or_, select

from app.models.entities import Report
from app.modules.routing.polyline import decode_polyline
from app.modules.routing.schemas import RouteHazard

HAZARD_RADIUS_METERS = 30.0

def attach_route_hazards(routes, db):
    if not routes:
        return
    predicates = []
    for route in routes:
        line = {"type": "LineString", "coordinates": [[lon, lat] for lat, lon in decode_polyline(route.polyline)]}
        geography = cast(func.ST_SetSRID(func.ST_GeomFromGeoJSON(json.dumps(line)), 4326), Geography)
        predicates.append(func.ST_DWithin(Report.location, geography, HAZARD_RADIUS_METERS))
    location = cast(Report.location, Geometry)
    statement = select(
        Report.id, Report.category_user, Report.status, Report.severity, Report.observed_at,
        func.ST_Y(location).label("latitude"), func.ST_X(location).label("longitude"),
        *[p.label(f"route_{i}") for i, p in enumerate(predicates)],
    ).where(Report.status.in_(["VERIFIED", "PENDING_VERIFICATION"]), or_(*predicates)).order_by(Report.id)
    results = [[] for _ in routes]
    for row in db.execute(statement):
        hazard = RouteHazard(
            id=str(row.id), category=row.category_user, severity=row.severity,
            observed_at=row.observed_at, status=getattr(row.status, "value", row.status),
            latitude=row.latitude, longitude=row.longitude,
        )
        for i in range(len(routes)):
            if row._mapping[f"route_{i}"]:
                results[i].append(hazard.model_copy(deep=True))
    for route, hazards in zip(routes, results):
        route.nearby_hazards = hazards
        route.hazard_radius_meters = HAZARD_RADIUS_METERS
