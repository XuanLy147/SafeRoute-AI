import json
from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, Query, status
from geoalchemy2 import Geometry
from sqlalchemy import cast, func
from sqlalchemy.orm import Session

from app.core.errors import error_responses
from app.core.security import get_current_user
from app.db.geo import make_point, point_to_coords
from app.db.session import get_db
from app.models.entities import AiDetection, AiPrediction, Report, ReportStatus, User, VoteType
from app.modules.images.router import find_image
from app.modules.images.service import image_url as private_image_url
from app.modules.reports.schemas import (
    ReportCreateRequest,
    ReportDetailResponse,
    ReportItemResponse,
    VotesSummary,
)

router = APIRouter(responses=error_responses(400, 401, 403, 404, 409, 503))

# Danh sách in-memory fallback cho test môi trường độc lập khi DB chưa kết nối


def _get_votes_summary(report, memory_item=None) -> VotesSummary:
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
    now = datetime.now(UTC)

    # 1. Thử lưu vào PostgreSQL/PostGIS nếu có kết nối
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
    db.flush()
    image_url = None
    if request.image_id:
        rep_img = find_image(db, request.image_id)
        if rep_img.owner_id != current_user.id:
            raise HTTPException(403, "Cannot attach another user's image")
        if rep_img.report_id is not None:
            raise HTTPException(409, "Image already attached")
        rep_img.report_id = report.id
        image_url = private_image_url(rep_img.object_key)
    if request.ai_detections is not None:
        prediction = AiPrediction(report_id=report.id, model_version=request.model_version or "USER_CONFIRMED")
        db.add(prediction)
        db.flush()
        for detection in request.ai_detections:
            db.add(
                AiDetection(
                    prediction_id=prediction.id,
                    class_code=detection.class_code,
                    confidence=detection.confidence,
                    bbox_xyxy=json.dumps(detection.bbox),
                )
            )
    db.commit()
    db.refresh(report)
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


@router.get(
    "",
    response_model=list[ReportItemResponse],
    summary="Lấy danh sách báo cáo công khai trên bản đồ",
    description="Mặc định LOẠI BỎ hoàn toàn các báo cáo bị REJECTED khỏi bản đồ chung. Có hỗ trợ lọc theo trạng thái và khu vực.",
)
def get_reports(
    status_filter: ReportStatus | None = Query(None, alias="status", description="Lọc theo trạng thái"),
    category: str | None = Query(None, description="Lọc theo mã hư hỏng"),
    west: float | None = Query(None, ge=-180, le=180),
    south: float | None = Query(None, ge=-90, le=90),
    east: float | None = Query(None, ge=-180, le=180),
    north: float | None = Query(None, ge=-90, le=90),
    limit: int = Query(200, ge=1, le=1000),
    offset: int = Query(0, ge=0),
    db: Session = Depends(get_db),
):
    # 1. Thử truy vấn DB
    query = db.query(Report).filter(Report.status != ReportStatus.REJECTED)
    if status_filter:
        query = query.filter(Report.status == status_filter)
    else:
        # QUY TẮC CỐT LÕI: Không trả về REJECTED trên bản đồ công khai
        query = query.filter(Report.status != ReportStatus.REJECTED)
    if category:
        query = query.filter(Report.category_user == category)
    bounds = (west, south, east, north)
    if any(v is not None for v in bounds):
        if any(v is None for v in bounds) or west >= east or south >= north:
            raise HTTPException(422, "Require west < east and south < north; all four bounds required")
        query = query.filter(
            func.ST_Intersects(
                cast(Report.location, Geometry("POINT", srid=4326)),
                func.ST_MakeEnvelope(west, south, east, north, 4326),
            )
        )
    reports = query.order_by(Report.observed_at.desc(), Report.id.desc()).offset(offset).limit(limit).all()
    results = []
    for r in reports:
        lat, lon = point_to_coords(r.location)
        img_url = private_image_url(r.images[0].object_key) if r.images else None
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
    reports = db.query(Report).filter(Report.user_id == current_user.id).order_by(Report.observed_at.desc()).all()
    results = []
    for r in reports:
        lat, lon = point_to_coords(r.location)
        img_url = private_image_url(r.images[0].object_key) if r.images else None
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
    r = db.query(Report).filter(Report.id == report_id).first()
    if not r:
        raise HTTPException(status_code=404, detail="Không tìm thấy báo cáo yêu cầu.")
    lat, lon = point_to_coords(r.location)
    img_url = private_image_url(r.images[0].object_key) if r.images else None
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
        images=[{"object_key": img.object_key, "url": private_image_url(img.object_key)} for img in r.images],
        ai_predictions=[
            {
                "model_version": p.model_version,
                "source": "USER_CONFIRMED",
                "detections": [
                    {"class_code": d.class_code, "confidence": d.confidence, "bbox": json.loads(d.bbox_xyxy)}
                    for d in p.detections
                ],
            }
            for p in r.predictions
        ],
        ai_detections=[
            {"class_code": d.class_code, "confidence": d.confidence, "bbox": json.loads(d.bbox_xyxy)}
            for p in r.predictions
            for d in p.detections
        ],
        model_version=r.predictions[-1].model_version if r.predictions else None,
        audit_logs=[
            {
                "reviewer_id": str(log.actor_id),
                "old_status": log.from_status.value,
                "new_status": log.to_status.value,
                "reason": log.reason,
                "timestamp": log.created_at.isoformat(),
            }
            for log in r.review_logs
        ],
        review_logs=review_logs,
    )
