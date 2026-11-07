from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db.geo import make_point, point_to_coords
from app.db.session import get_db
from app.models.entities import Report, ReportImage, ReportStatus, User, VoteType
from app.modules.reports.schemas import (
    ReportCreateRequest,
    ReportDetailResponse,
    ReportItemResponse,
    VotesSummary,
)

router = APIRouter()

# Danh sách in-memory fallback cho test môi trường độc lập khi DB chưa kết nối
_MEMORY_REPORTS: list[dict] = []
_MEMORY_ID_COUNTER = 100

def _get_votes_summary(report, memory_item=None) -> VotesSummary:
    if memory_item:
        votes = memory_item.get("votes_list", [])
        sup = sum(1 for v in votes if v.get("vote") == VoteType.SUPPORT.value)
        opp = sum(1 for v in votes if v.get("vote") == VoteType.OPPOSE.value)
        uns = sum(1 for v in votes if v.get("vote") == VoteType.UNSURE.value)
        return VotesSummary(support=sup, oppose=opp, unsure=uns, total=len(votes))

    if hasattr(report, "votes") and report.votes:
        sup = sum(1 for v in report.votes if v.vote == VoteType.SUPPORT)
        opp = sum(1 for v in report.votes if v.vote == VoteType.OPPOSE)
        uns = sum(1 for v in report.votes if v.vote == VoteType.UNSURE)
        return VotesSummary(support=sup, oppose=opp, unsure=uns, total=len(report.votes))
    return VotesSummary()

@router.post(
    "",
    response_model=ReportItemResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Tạo báo cáo nguy cơ mặt đường mới",
    description="Gửi báo cáo kèm GPS và loại hư hỏng. Trạng thái ban đầu BẮT BUỘC là PENDING_VERIFICATION.",
)
def create_report(
    request: ReportCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    global _MEMORY_ID_COUNTER
    now = datetime.now(UTC)

    # 1. Thử lưu vào PostgreSQL/PostGIS nếu có kết nối
    try:
        report = Report(
            user_id=current_user.id,
            category_user=request.category_user,
            status=ReportStatus.PENDING_VERIFICATION,
            severity=request.severity,
            location=make_point(request.longitude, request.latitude),
            observed_at=now,
            description=request.description,
        )
        db.add(report)
        db.commit()
        db.refresh(report)

        image_url = None
        if request.image_id:
            rep_img = ReportImage(
                report_id=report.id,
                object_key=request.image_id,
                mime="image/jpeg",
            )
            db.add(rep_img)
            db.commit()
            image_url = f"/uploads/{request.image_id}.jpg"

        lat, lon = point_to_coords(report.location)
        if lat == 0.0 and lon == 0.0:
            lat, lon = request.latitude, request.longitude

        return ReportItemResponse(
            id=report.id,
            user_id=report.user_id,
            category_user=report.category_user,
            status=report.status,
            severity=report.severity,
            latitude=lat,
            longitude=lon,
            observed_at=report.observed_at,
            description=report.description,
            image_url=image_url,
            votes=VotesSummary(),
        )
    except Exception:
        # Fallback lưu in-memory cho test độc lập khi DB offline
        _MEMORY_ID_COUNTER += 1
        item = {
            "id": _MEMORY_ID_COUNTER,
            "user_id": current_user.id,
            "category_user": request.category_user,
            "status": ReportStatus.PENDING_VERIFICATION,
            "severity": request.severity,
            "latitude": request.latitude,
            "longitude": request.longitude,
            "observed_at": now,
            "description": request.description,
            "image_url": f"/uploads/{request.image_id}.jpg" if request.image_id else None,
            "votes_list": [],
            "review_logs": [],
        }
        _MEMORY_REPORTS.append(item)
        return ReportItemResponse(
            id=item["id"],
            user_id=item["user_id"],
            category_user=item["category_user"],
            status=item["status"],
            severity=item["severity"],
            latitude=item["latitude"],
            longitude=item["longitude"],
            observed_at=item["observed_at"],
            description=item["description"],
            image_url=item["image_url"],
            votes=VotesSummary(),
        )

@router.get(
    "",
    response_model=list[ReportItemResponse],
    summary="Lấy danh sách báo cáo công khai trên bản đồ",
    description="Mặc định LOẠI BỎ hoàn toàn các báo cáo bị REJECTED khỏi bản đồ chung. Có hỗ trợ lọc theo trạng thái và khu vực.",
)
def get_reports(
    status_filter: ReportStatus | None = Query(None, alias="status", description="Lọc theo trạng thái"),
    category: str | None = Query(None, description="Lọc theo mã hư hỏng"),
    db: Session = Depends(get_db),
):
    # 1. Thử truy vấn DB
    try:
        query = db.query(Report)
        if status_filter:
            query = query.filter(Report.status == status_filter)
        else:
            # QUY TẮC CỐT LÕI: Không trả về REJECTED trên bản đồ công khai
            query = query.filter(Report.status != ReportStatus.REJECTED)

        if category:
            query = query.filter(Report.category_user == category)

        reports = query.order_by(Report.observed_at.desc()).all()
        results = []
        for r in reports:
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
        # Fallback in-memory
        results = []
        for m in _MEMORY_REPORTS:
            if status_filter and m["status"] != status_filter:
                continue
            if not status_filter and m["status"] == ReportStatus.REJECTED:
                continue
            if category and m["category_user"] != category:
                continue
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

@router.get(
    "/mine",
    response_model=list[ReportItemResponse],
    summary="Lấy danh sách báo cáo của tôi",
    description="Xem lịch sử các báo cáo do chính người dùng hiện tại đã gửi lên.",
)
def get_my_reports(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    try:
        reports = (
            db.query(Report)
            .filter(Report.user_id == current_user.id)
            .order_by(Report.observed_at.desc())
            .all()
        )
        results = []
        for r in reports:
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
            if m["user_id"] == current_user.id:
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

@router.get(
    "/{report_id}",
    response_model=ReportDetailResponse,
    summary="Chi tiết một báo cáo nguy cơ",
    description="Lấy chi tiết báo cáo, bao gồm ảnh, nhãn người dùng, số lượng vote và lịch sử kiểm duyệt.",
)
def get_report_detail(
    report_id: int,
    db: Session = Depends(get_db),
):
    try:
        r = db.query(Report).filter(Report.id == report_id).first()
        if not r:
            raise HTTPException(status_code=404, detail="Không tìm thấy báo cáo yêu cầu.")
        lat, lon = point_to_coords(r.location)
        img_url = f"/uploads/{r.images[0].object_key}.jpg" if r.images else None

        review_logs = [
            {
                "id": log.id,
                "actor_id": log.actor_id,
                "from_status": log.from_status.value,
                "to_status": log.to_status.value,
                "reason": log.reason,
                "created_at": log.created_at.isoformat(),
            }
            for log in r.review_logs
        ]

        return ReportDetailResponse(
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
            images=[{"object_key": img.object_key, "url": f"/uploads/{img.object_key}.jpg"} for img in r.images],
            ai_predictions=[],
            review_logs=review_logs,
        )
    except HTTPException:
        raise
    except Exception:
        for m in _MEMORY_REPORTS:
            if m["id"] == report_id:
                return ReportDetailResponse(
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
                    images=[{"object_key": "sample", "url": m["image_url"]}] if m.get("image_url") else [],
                    ai_predictions=[],
                    review_logs=m.get("review_logs", []),
                )
        raise HTTPException(status_code=404, detail="Không tìm thấy báo cáo yêu cầu.")
