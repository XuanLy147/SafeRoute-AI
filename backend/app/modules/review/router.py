from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.errors import error_responses
from app.core.security import require_role
from app.db.geo import point_to_coords
from app.db.session import get_db
from app.models.entities import Report, ReportStatus, ReviewLog, User, UserRole
from app.modules.images.service import image_url
from app.modules.reports.router import _get_votes_summary
from app.modules.reports.schemas import ReportItemResponse
from app.modules.review.schemas import ReviewResponse, ReviewStatusPatchRequest

router = APIRouter(responses=error_responses(400, 401, 403, 404, 409, 503))


@router.get(
    "/queue",
    response_model=list[ReportItemResponse],
    summary="Hàng đợi kiểm duyệt báo cáo",
    description="Chỉ dành cho vai trò REVIEWER hoặc ADMIN (USER gọi sẽ nhận mã lỗi 403 Forbidden). Trả về danh sách báo cáo đang ở trạng thái PENDING_VERIFICATION.",
)
def get_review_queue(
    current_user: User = Depends(require_role([UserRole.REVIEWER, UserRole.ADMIN])),
    db: Session = Depends(get_db),
):
    pending_reports = (
        db.query(Report)
        .filter(Report.status == ReportStatus.PENDING_VERIFICATION)
        .order_by(Report.observed_at.asc())
        .all()
    )
    results = []
    for r in pending_reports:
        lat, lon = point_to_coords(r.location)
        img_url = image_url(r.images[0].object_key) if r.images else None
        results.append(
            ReportItemResponse(
                id=r.id,
                user_id=r.user_id,
                category_user=r.category_user,
                status=r.status,
                severity=r.severity,
                latitude=lat,
                longitude=lon,
                observed_at=r.observed_at,
                description=r.description,
                image_url=img_url,
                votes=_get_votes_summary(r),
            )
        )
    return results


@router.patch(
    "/reports/{report_id}/status",
    response_model=ReviewResponse,
    summary="Xét duyệt báo cáo nguy cơ (Xác minh hoặc Từ chối)",
    description="Chỉ dành cho REVIEWER/ADMIN (USER gọi sẽ bị 403). BẮT BUỘC cung cấp lý do và tự động ghi vết Audit Log (review_logs).",
)
def review_report_status(
    report_id: int,
    request: ReviewStatusPatchRequest,
    current_user: User = Depends(require_role([UserRole.REVIEWER, UserRole.ADMIN])),
    db: Session = Depends(get_db),
):
    if request.status not in [ReportStatus.VERIFIED, ReportStatus.REJECTED]:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Trạng thái kiểm duyệt chỉ có thể chuyển thành 'VERIFIED' hoặc 'REJECTED'.",
        )

    if not request.reason or len(request.reason.strip()) < 5:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="Bắt buộc phải nhập lý do kiểm duyệt tối thiểu 5 ký tự để ghi vết audit log.",
        )

    now = datetime.now(UTC)

    # 1. Thử cập nhật trên DB
    report = db.query(Report).filter(Report.id == report_id).with_for_update().first()
    if not report:
        raise HTTPException(status_code=404, detail="Không tìm thấy báo cáo yêu cầu kiểm duyệt.")
    if report.status != ReportStatus.PENDING_VERIFICATION:
        raise HTTPException(409, "Report already reviewed")
    # Authentication may precede a long wait for the report lock. Re-read the
    # actor under a shared row lock, held through commit, so a role revocation
    # cannot race the final permission check. Lock order: report, then actor;
    # role management locks users only and never waits on reports.
    actor = (
        db.query(User).filter(User.id == current_user.id)
        .populate_existing().with_for_update(read=True).first()
    )
    if actor is None or actor.role not in (UserRole.REVIEWER, UserRole.ADMIN):
        raise HTTPException(403, "Review permission has changed",
                            headers={"X-SafeRoute-Role-Refresh": "required"})
    old_status = report.status
    report.status = request.status
    # Ghi log audit append-only
    log = ReviewLog(
        report_id=report.id,
        actor_id=current_user.id,
        from_status=old_status,
        to_status=request.status,
        reason=request.reason.strip(),
        created_at=now,
    )
    db.add(log)
    db.commit()
    db.refresh(log)
    return ReviewResponse(
        report_id=report.id,
        status=report.status,
        actor_id=current_user.id,
        reason=request.reason.strip(),
        created_at=now,
    )
