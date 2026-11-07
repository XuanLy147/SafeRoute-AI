"""Transaction races on a disposable, explicitly opted-in PostGIS database only."""
import os
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi import HTTPException
from sqlalchemy import create_engine, delete, event, select
from sqlalchemy.engine import make_url
from sqlalchemy.orm import Session

from app.db.geo import make_point
from app.models.entities import Report, ReportStatus, ReviewLog, RoleAudit, User, UserRole
from app.modules.admin.router import change_role
from app.modules.admin.schemas import RoleChangeRequest
from app.modules.review.router import review_report_status
from app.modules.review.schemas import ReviewStatusPatchRequest


@pytest.fixture
def committed_fixture():
    url = os.environ.get("SAFEROUTE_TEST_DATABASE_URL", "")
    assert url and (make_url(url).database or "").startswith("saferoute_test_")
    engine = create_engine(url)
    tag = uuid.uuid4().hex
    with Session(engine) as db:
        actor = User(email=f"review-{tag}@example.invalid", password_hash="not-login", role=UserRole.REVIEWER)
        admin = User(email=f"admin-{tag}@example.invalid", password_hash="not-login", role=UserRole.ADMIN)
        db.add_all([actor, admin])
        db.flush()
        report = Report(user_id=actor.id, category_user="D40", severity="LOW",
                        status=ReportStatus.PENDING_VERIFICATION, location=make_point(106.678, 10.755))
        db.add(report)
        db.commit()
        ids = actor.id, admin.id, report.id
    try:
        yield engine, ids
    finally:
        with Session(engine) as db:
            db.execute(delete(ReviewLog).where(ReviewLog.report_id == ids[2]))
            db.execute(delete(RoleAudit).where(RoleAudit.user_id == ids[0]))
            db.execute(delete(Report).where(Report.id == ids[2]))
            db.execute(delete(User).where(User.id.in_(ids[:2])))
            db.commit()
        engine.dispose()


def decide(engine, actor_id, report_id, state=ReportStatus.VERIFIED, barrier=None):
    with Session(engine) as db:
        actor = db.get(User, actor_id)  # Authentication took place before waiting for the report.
        if barrier:
            barrier.wait(timeout=5)
        try:
            review_report_status(report_id, ReviewStatusPatchRequest(status=state, reason="QA concurrency evidence"),
                                 current_user=actor, db=db)
            return 200
        except HTTPException as error:
            db.rollback()
            return error.status_code


def test_demoted_reviewer_cannot_commit_a_decision_waiting_for_report_lock(committed_fixture):
    engine, (actor_id, admin_id, report_id) = committed_fixture
    waiting = threading.Event()
    blocker = Session(engine)
    blocker.scalar(select(Report).where(Report.id == report_id).with_for_update())

    def observe(conn, cursor, statement, parameters, context, executemany):
        if "FROM reports" in statement and "FOR UPDATE" in statement:
            waiting.set()

    event.listen(engine, "before_cursor_execute", observe)
    try:
        with ThreadPoolExecutor(max_workers=1) as workers:
            future = workers.submit(decide, engine, actor_id, report_id)
            try:
                assert waiting.wait(5), "Review did not reach the held report lock"
                with Session(engine) as db:
                    change_role(actor_id, RoleChangeRequest(role=UserRole.USER, expected_role=UserRole.REVIEWER,
                                                          reason="QA revoke while review waits"),
                                actor=db.get(User, admin_id), db=db)
            finally:
                blocker.rollback()
            assert future.result(timeout=10) == 403
        with Session(engine) as db:
            assert db.get(Report, report_id).status == ReportStatus.PENDING_VERIFICATION
            assert db.scalars(select(ReviewLog).where(ReviewLog.report_id == report_id)).all() == []
    finally:
        blocker.close()
        event.remove(engine, "before_cursor_execute", observe)


def test_two_concurrent_decisions_create_exactly_one_audit(committed_fixture):
    engine, (actor_id, admin_id, report_id) = committed_fixture
    barrier = threading.Barrier(2)
    with ThreadPoolExecutor(max_workers=2) as workers:
        a = workers.submit(decide, engine, actor_id, report_id, ReportStatus.VERIFIED, barrier)
        b = workers.submit(decide, engine, admin_id, report_id, ReportStatus.REJECTED, barrier)
        assert sorted([a.result(timeout=10), b.result(timeout=10)]) == [200, 409]
    with Session(engine) as db:
        logs = db.scalars(select(ReviewLog).where(ReviewLog.report_id == report_id)).all()
        assert len(logs) == 1
        assert logs[0].to_status == db.get(Report, report_id).status
