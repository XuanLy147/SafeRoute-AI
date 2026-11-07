from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import or_, select, text
from sqlalchemy.orm import Session

from app.core.errors import error_responses
from app.core.security import require_role
from app.db.session import get_db
from app.models.entities import RoleAudit, User, UserRole
from app.modules.admin.schemas import RoleAuditResponse, RoleChangeRequest, RoleChangeResponse, UserSummary

router = APIRouter(responses=error_responses(401, 403, 404, 409, 503))
admin_only = require_role([UserRole.ADMIN])


@router.get("/users", response_model=list[UserSummary])
def list_users(
    q: str = Query("", max_length=200),
    offset: int = Query(0, ge=0),
    limit: int = Query(30, ge=1, le=100),
    actor: User = Depends(admin_only),
    db: Session = Depends(get_db),
):
    stmt = select(User)
    if q.strip():
        # Treat search input as literal text, not SQL wildcard patterns.
        value = q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        stmt = stmt.where(
            or_(User.email.ilike(f"%{value}%", escape="\\"), User.full_name.ilike(f"%{value}%", escape="\\"))
        )
    return db.scalars(stmt.order_by(User.id).offset(offset).limit(limit)).all()


@router.patch("/users/{user_id}/role", response_model=RoleChangeResponse)
def change_role(
    user_id: int, request: RoleChangeRequest, actor: User = Depends(admin_only), db: Session = Depends(get_db)
):
    # Serialize role mutations so concurrent demotions cannot remove the final admin.
    db.execute(text("SELECT pg_advisory_xact_lock(73920261108)"))
    db.refresh(actor)
    if actor.role != UserRole.ADMIN:
        raise HTTPException(403, "Administrator permission has changed", headers={"X-SafeRoute-Role-Refresh": "required"})
    target = db.scalar(select(User).where(User.id == user_id).with_for_update())
    if target is None:
        raise HTTPException(404, "User not found")
    if target.role != request.expected_role:
        raise HTTPException(409, "Role changed; reload before trying again")
    if target.role == request.role:
        raise HTTPException(409, "This role is already assigned")
    if target.id == actor.id:
        raise HTTPException(409, "Administrators cannot change their own role")
    log = RoleAudit(
        actor_id=actor.id, user_id=target.id, from_role=target.role, to_role=request.role, reason=request.reason
    )
    target.role = request.role
    db.add(log)
    db.commit()
    db.refresh(target)
    db.refresh(log)
    return RoleChangeResponse(user=UserSummary.model_validate(target), audit_id=log.id)


@router.get("/role-audit", response_model=list[RoleAuditResponse])
def role_audit(
    user_id: int | None = None,
    offset: int = Query(0, ge=0),
    limit: int = Query(30, ge=1, le=100),
    actor: User = Depends(admin_only),
    db: Session = Depends(get_db),
):
    stmt = select(RoleAudit)
    if user_id is not None:
        stmt = stmt.where(RoleAudit.user_id == user_id)
    return db.scalars(stmt.order_by(RoleAudit.id.desc()).offset(offset).limit(limit)).all()
