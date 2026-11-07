"""Draft internal scoring contract; not an HTTP DTO or a geospatial query.

Callers supply only reports selected near each real route by PostGIS. Policy
weights are explicit: this module supplies no production severity scale.
"""

from dataclasses import dataclass
from math import fsum, isfinite
from types import MappingProxyType
from typing import Mapping


STATUSES = frozenset({"PENDING_VERIFICATION", "VERIFIED", "REJECTED"})


def _identifier(value: str) -> None:
    if not isinstance(value, str) or not value.strip():
        raise ValueError("Identifiers and severity labels must be nonempty strings")


def _nonnegative(value: float) -> None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ValueError("Weights must be finite nonnegative numbers")
    if not isfinite(value) or value < 0:
        raise ValueError("Weights must be finite nonnegative numbers")


@dataclass(frozen=True)
class RiskPolicy:
    revision: str
    severity_weights: Mapping[str, float]
    pending_multiplier: float

    def __post_init__(self) -> None:
        _identifier(self.revision)
        weights = dict(self.severity_weights)
        if not weights:
            raise ValueError("An explicit severity policy is required")
        for label, weight in weights.items():
            _identifier(label)
            _nonnegative(weight)
        _nonnegative(self.pending_multiplier)
        if self.pending_multiplier > 1:
            raise ValueError("Pending multiplier must be between zero and one")
        object.__setattr__(self, "severity_weights", MappingProxyType(weights))


@dataclass(frozen=True)
class NearbyReport:
    report_id: str
    status: str
    severity: str

    def __post_init__(self) -> None:
        _identifier(self.report_id)
        _identifier(self.severity)
        if self.status not in STATUSES:
            raise ValueError("Unknown report status")


@dataclass(frozen=True)
class RouteReports:
    route_id: str
    reports: tuple[NearbyReport, ...]


@dataclass(frozen=True)
class Contribution:
    report_id: str
    status: str
    severity: str
    weight: float
    multiplier: float
    score: float


@dataclass(frozen=True)
class RouteRisk:
    route_id: str
    policy_revision: str
    score: float
    contributions: tuple[Contribution, ...]
    excluded_pending_count: int
    excluded_rejected_count: int


def score_routes(
    routes: tuple[RouteReports, ...], policy: RiskPolicy
) -> tuple[RouteRisk, ...]:
    """Preserve candidate order/count; sum weights once per report per route.

    Pending multiplier zero means excluded. Identical duplicates are collapsed;
    conflicting snapshots fail rather than choosing a status arbitrarily. A
    report near several routes contributes separately to each route. Scores
    describe recorded reports only, not safety or probabilities.
    """
    route_ids: set[str] = set()
    snapshots: dict[str, NearbyReport] = {}
    results = []
    for route in routes:
        _identifier(route.route_id)
        if route.route_id in route_ids:
            raise ValueError("Duplicate route ID")
        route_ids.add(route.route_id)
        unique: dict[str, NearbyReport] = {}
        for report in route.reports:
            previous = snapshots.setdefault(report.report_id, report)
            if previous != report:
                raise ValueError("Conflicting report snapshots; reload consistently")
            unique[report.report_id] = report
        contributions = []
        pending = rejected = 0
        for report_id in sorted(unique):
            report = unique[report_id]
            if report.status == "REJECTED":
                rejected += 1
                continue
            if report.status == "PENDING_VERIFICATION" and policy.pending_multiplier == 0:
                pending += 1
                continue
            if report.severity not in policy.severity_weights:
                raise ValueError("Severity is absent from the selected policy")
            multiplier = policy.pending_multiplier if report.status == "PENDING_VERIFICATION" else 1.0
            weight = policy.severity_weights[report.severity]
            contributions.append(Contribution(
                report.report_id, report.status, report.severity,
                weight, multiplier, weight * multiplier,
            ))
        try:
            score = fsum(item.score for item in contributions)
        except OverflowError as exc:
            raise ValueError("Risk score exceeds finite numeric range") from exc
        if not isfinite(score):
            raise ValueError("Risk score exceeds finite numeric range")
        results.append(RouteRisk(
            route.route_id, policy.revision, score, tuple(contributions), pending, rejected,
        ))
    return tuple(results)
