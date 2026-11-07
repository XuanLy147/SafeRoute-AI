"""Read-only moderation views. Decisions remain in the audited review endpoint."""
from datetime import UTC, datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session, aliased, selectinload

from app.core.config import settings
from app.core.errors import error_responses
from app.core.security import require_role
from app.db.geo import point_to_coords
from app.db.session import get_db
from app.models.entities import Report, ReportStatus, ReviewLog, User, UserRole
from app.modules.images.service import image_url
from app.modules.reports.router import _get_votes_summary
from app.modules.reports.schemas import ReportItemResponse

router = APIRouter(responses=error_responses(401, 403, 422, 503))
reviewer = require_role([UserRole.REVIEWER, UserRole.ADMIN])
admin = require_role([UserRole.ADMIN])


class HistoryEntry(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    report_id: int
    actor_id: int
    from_status: ReportStatus
    to_status: ReportStatus
    reason: str
    created_at: datetime


class DuplicatePair(BaseModel):
    report_id: int
    candidate_id: int
    category: str
    distance_m: float


class Overview(BaseModel):
    report_counts: dict[str, int]
    user_counts: dict[str, int]
    review_count: int
    model_version: str
    ai_ready: bool
    risk_ready: bool
    risk_policy_revision: str | None
    max_image_size_mb: int
    duplicate_radius_m: int = 30
    duplicate_window_days: int = 7


def summary(report):
    lat, lon = point_to_coords(report.location)
    return ReportItemResponse(
        id=report.id, user_id=report.user_id, category_user=report.category_user,
        status=report.status, severity=report.severity, latitude=lat, longitude=lon,
        observed_at=report.observed_at, description=report.description,
        image_url=image_url(report.images[0].object_key) if report.images else None,
        votes=_get_votes_summary(report),
    )


@router.get("/review/history", response_model=list[HistoryEntry])
def history(
    offset: int = Query(0, ge=0), limit: int = Query(30, ge=1, le=100),
    actor: User = Depends(reviewer), db: Session = Depends(get_db),
):
    return db.scalars(select(ReviewLog).order_by(ReviewLog.id.desc()).offset(offset).limit(limit)).all()


@router.get("/admin/reports", response_model=list[ReportItemResponse])
def all_reports(
    status_filter: ReportStatus | None = Query(None, alias="status"),
    q: str = Query("", max_length=200),
    offset: int = Query(0, ge=0), limit: int = Query(30, ge=1, le=100),
    actor: User = Depends(admin), db: Session = Depends(get_db),
):
    stmt = select(Report).options(selectinload(Report.images), selectinload(Report.votes))
    if status_filter:
        stmt = stmt.where(Report.status == status_filter)
    if q.strip():
        value = q.strip()
        match = Report.description.contains(value, autoescape=True)
        if value.isdecimal() and len(value) < 10:
            match = or_(match, Report.id == int(value))
        stmt = stmt.where(match)
    return [
        summary(r) for r in db.scalars(
            stmt.order_by(Report.observed_at.desc(), Report.id.desc()).offset(offset).limit(limit)
        ).all()
    ]


@router.get("/review/duplicates", response_model=list[DuplicatePair])
def duplicates(
    q: str = Query("", max_length=200),
    offset: int = Query(0, ge=0), limit: int = Query(30, ge=1, le=100),
    actor: User = Depends(reviewer), db: Session = Depends(get_db),
):
    # Geography uses metres. Suggestions only: no status changes or automatic merging.
    other = aliased(Report)
    cutoff = datetime.now(UTC) - timedelta(days=7)
    distance = func.ST_Distance(Report.location, other.location)
    stmt = select(Report.id, other.id, Report.category_user, distance).join(
        other,
        (Report.id < other.id)
        & (Report.category_user == other.category_user)
        & func.ST_DWithin(Report.location, other.location, 30),
    ).where(
        Report.status != ReportStatus.REJECTED, other.status != ReportStatus.REJECTED,
        or_(Report.status == ReportStatus.PENDING_VERIFICATION, other.status == ReportStatus.PENDING_VERIFICATION),
        Report.observed_at >= cutoff, other.observed_at >= cutoff,
    )
    if q.strip():
        value = q.strip()
        match = or_(Report.description.contains(value, autoescape=True),
                    other.description.contains(value, autoescape=True))
        if value.isdecimal() and len(value) < 10:
            match = or_(match, Report.id == int(value), other.id == int(value))
        stmt = stmt.where(match)
    rows = db.execute(stmt.order_by(Report.id.desc(), other.id.desc()).offset(offset).limit(limit)).all()
    return [
        DuplicatePair(report_id=a, candidate_id=b, category=category, distance_m=round(metres, 1))
        for a, b, category, metres in rows
    ]


@router.get("/admin/overview", response_model=Overview)
def overview(actor: User = Depends(admin), db: Session = Depends(get_db)):
    from app.modules.ai.service import ai_service
    from app.modules.risk.policy import load_policy

    try:
        policy = load_policy()
    except ValueError as exc:
        raise HTTPException(503, "Risk configuration unavailable") from exc
    reports = {s.value: 0 for s in ReportStatus}
    reports.update({s.value: n for s, n in db.execute(select(Report.status, func.count()).group_by(Report.status))})
    users = {r.value: 0 for r in UserRole}
    users.update({r.value: n for r, n in db.execute(select(User.role, func.count()).group_by(User.role))})
    return Overview(
        report_counts=reports, user_counts=users,
        review_count=db.scalar(select(func.count()).select_from(ReviewLog)),
        model_version=ai_service.model_version, ai_ready=ai_service.is_model_ready(),
        risk_ready=bool(policy and policy.approved), risk_policy_revision=policy.revision if policy else None,
        max_image_size_mb=settings.MAX_IMAGE_SIZE_MB,
    )
