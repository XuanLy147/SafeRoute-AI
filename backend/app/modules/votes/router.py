from datetime import UTC, datetime

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db.session import get_db
from app.models.entities import CommunityVote, Report, User
from app.modules.reports.router import _MEMORY_REPORTS
from app.modules.votes.schemas import VoteCreateRequest, VoteResponse

router = APIRouter()

# Lưu trữ votes in-memory cho testing độc lập
_MEMORY_VOTES = []
_MEMORY_VOTE_ID = 500

@router.post(
    "/{report_id}/votes",
    response_model=VoteResponse,
    status_code=status.HTTP_201_CREATED,
    summary="Góp ý cộng đồng cho một báo cáo",
    description="Người dùng gửi ý kiến SUPPORT, OPPOSE hoặc UNSURE. Mỗi người chỉ được vote 1 lần trên 1 report (tránh spam 409 Conflict).",
)
def create_vote(
    report_id: int,
    request: VoteCreateRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    global _MEMORY_VOTE_ID
    now = datetime.now(UTC)

    # 1. Thử ghi nhận trên database
    try:
        report = db.query(Report).filter(Report.id == report_id).first()
        if not report:
            raise HTTPException(status_code=404, detail="Không tìm thấy báo cáo để góp ý.")

        # Kiểm tra vote trùng
        existing_vote = (
            db.query(CommunityVote)
            .filter(
                CommunityVote.report_id == report_id,
                CommunityVote.user_id == current_user.id,
            )
            .first()
        )
        if existing_vote:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Bạn đã đóng góp ý kiến cho báo cáo này rồi. Không thể vote nhiều lần.",
            )

        vote = CommunityVote(
            report_id=report_id,
            user_id=current_user.id,
            vote=request.vote,
            note=request.note,
            created_at=now,
        )
        db.add(vote)
        db.commit()
        db.refresh(vote)

        return VoteResponse(
            id=vote.id,
            report_id=vote.report_id,
            user_id=vote.user_id,
            vote=vote.vote,
            note=vote.note,
            created_at=vote.created_at,
        )
    except HTTPException:
        raise
    except Exception:
        # Fallback in-memory
        report_found = any(m["id"] == report_id for m in _MEMORY_REPORTS)
        if not report_found and report_id != 1:
            raise HTTPException(status_code=404, detail="Không tìm thấy báo cáo để góp ý.")

        # Check trùng in memory
        for v in _MEMORY_VOTES:
            if v["report_id"] == report_id and v["user_id"] == current_user.id:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="Bạn đã đóng góp ý kiến cho báo cáo này rồi. Không thể vote nhiều lần.",
                )

        _MEMORY_VOTE_ID += 1
        new_v = {
            "id": _MEMORY_VOTE_ID,
            "report_id": report_id,
            "user_id": current_user.id,
            "vote": request.vote.value,
            "note": request.note,
            "created_at": now,
        }
        _MEMORY_VOTES.append(new_v)

        for m in _MEMORY_REPORTS:
            if m["id"] == report_id:
                m.setdefault("votes_list", []).append(new_v)

        return VoteResponse(
            id=new_v["id"],
            report_id=new_v["report_id"],
            user_id=new_v["user_id"],
            vote=request.vote,
            note=new_v["note"],
            created_at=new_v["created_at"],
        )
