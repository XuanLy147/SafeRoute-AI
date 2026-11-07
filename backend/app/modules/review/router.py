from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.security import require_role
from app.db.geo import point_to_coords
from app.db.session import get_db
from app.models.entities import Report, ReportStatus, ReviewLog, User, UserRole
from app.modules.reports.router import _MEMORY_REPORTS, _get_votes_summary
from app.modules.reports.schemas import ReportItemResponse
from app.modules.review.schemas import ReviewResponse, ReviewStatusPatchRequest

router = APIRouter()

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
    try:
        pending_reports = (
            db.query(Report)
            .filter(Report.status == ReportStatus.PENDING_VERIFICATION)
            .order_by(Report.observed_at.asc())
            .all()
        )
        results = []
        for r in pending_reports:
            lat, lon = point_to_coords(r.location)
            img_url = f"/uploads/{r.images[0].object_key}.jpg" if r.images else None
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
    except Exception:
        results = []
        for m in _MEMORY_REPORTS:
            if m["status"] == ReportStatus.PENDING_VERIFICATION:
                results.append(
                    ReportItemResponse(
                        id=m["id"],
                        user_id=m["user_id"],
                        category_user=m["category_user"],
                        status=m["status"],
                        severity=m["severity"],
                        latitude=m["latitude"],
                        longitude=m["longitude"],
                        observed_at=m["observed_at"],
                        description=m["description"],
                        image_url=m["image_url"],
                        votes=_get_votes_summary(None, m),
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
    try:
        report = db.query(Report).filter(Report.id == report_id).first()
        if not report:
            raise HTTPException(status_code=404, detail="Không tìm thấy báo cáo yêu cầu kiểm duyệt.")

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
    except HTTPException:
        raise
    except Exception:
        # Fallback in-memory
        for m in _MEMORY_REPORTS:
            if m["id"] == report_id:
                old_status = m["status"]
                m["status"] = request.status
                audit_entry = {
                    "id": len(m.get("review_logs", [])) + 1,
                    "actor_id": current_user.id,
                    "from_status": old_status.value if isinstance(old_status, ReportStatus) else str(old_status),
                    "to_status": request.status.value,
                    "reason": request.reason.strip(),
                    "created_at": now.isoformat(),
                }
                m.setdefault("review_logs", []).append(audit_entry)
                return ReviewResponse(
                    report_id=report_id,
                    status=request.status,
                    actor_id=current_user.id,
                    reason=request.reason.strip(),
                    created_at=now,
                )
        raise HTTPException(status_code=404, detail="Không tìm thấy báo cáo yêu cầu kiểm duyệt.")
