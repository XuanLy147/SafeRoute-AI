from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from app.core.security import create_access_token
from app.db.geo import make_point
from app.db.session import get_db
from app.main import app
from app.models.entities import Report, ReportStatus

client = TestClient(app)


def bearer(id_):
    return {"Authorization": "Bearer " + create_access_token({"sub": str(id_)})}


def report(status="PENDING_VERIFICATION", lat=10.755, category="D40", days=0, description="QA"):
    db = next(app.dependency_overrides[get_db]())
    row = Report(user_id=1, category_user=category, status=ReportStatus(status), severity="LOW",
                 location=make_point(106.678, lat), description=description,
                 observed_at=datetime.now(UTC) - timedelta(days=days))
    db.add(row)
    db.commit()
    return row.id


@pytest.mark.parametrize("path,reviewer_allowed", [
    ("/review/history", True), ("/review/duplicates", True),
    ("/admin/reports", False), ("/admin/overview", False),
])
def test_new_views_enforce_roles(path, reviewer_allowed):
    path = "/api/v1" + path
    assert client.get(path).status_code == 401
    denied = client.get(path, headers=bearer(1))
    assert denied.status_code == 403
    assert denied.headers["X-SafeRoute-Role-Refresh"] == "required"
    assert client.get(path, headers=bearer(2)).status_code == (200 if reviewer_allowed else 403)
    assert client.get(path, headers=bearer(3)).status_code == 200


def test_all_reports_filters_and_pagination_do_not_leak_rejected_publicly():
    ids = [report(s) for s in ["PENDING_VERIFICATION", "VERIFIED", "REJECTED"]]
    all_rows = client.get("/api/v1/admin/reports", headers=bearer(3)).json()
    assert {r["id"] for r in all_rows} == set(ids)
    rows = client.get("/api/v1/admin/reports?status=REJECTED", headers=bearer(3)).json()
    assert [r["id"] for r in rows] == [ids[2]]
    first = client.get("/api/v1/admin/reports?limit=1", headers=bearer(3)).json()
    second = client.get("/api/v1/admin/reports?limit=1&offset=1", headers=bearer(3)).json()
    assert first[0]["id"] != second[0]["id"]
    assert ids[2] not in [r["id"] for r in client.get("/api/v1/reports").json()]
    assert client.get("/api/v1/admin/reports?q=%25", headers=bearer(3)).json() == []
    assert client.get("/api/v1/admin/reports?offset=-1", headers=bearer(3)).status_code == 422


def test_history_reads_real_decisions_and_paginates():
    a, b = report(), report()
    for id_, state in [(a, "VERIFIED"), (b, "REJECTED")]:
        response = client.patch(f"/api/v1/reports/{id_}/status", headers=bearer(2),
                                json={"status": state, "reason": "QA decision evidence"})
        assert response.status_code == 200
    rows = client.get("/api/v1/review/history", headers=bearer(2)).json()
    assert [(r["report_id"], r["to_status"], r["actor_id"]) for r in rows] == [(b, "REJECTED", 2), (a, "VERIFIED", 2)]
    assert rows[0]["reason"] == "QA decision evidence"
    assert client.get("/api/v1/review/history?limit=1&offset=1", headers=bearer(3)).json()[0]["report_id"] == a


def test_duplicate_suggestions_obey_distance_age_category_and_status_without_mutation():
    a = report()
    b = report("VERIFIED", lat=10.75505)
    report("REJECTED")
    report(lat=10.765)
    report(days=8)
    report(category="D00")
    rows = client.get("/api/v1/review/duplicates", headers=bearer(2)).json()
    assert len(rows) == 1
    assert {rows[0]["report_id"], rows[0]["candidate_id"]} == {a, b}
    assert 0 < rows[0]["distance_m"] < 30
    assert client.get("/api/v1/review/duplicates?q=absent", headers=bearer(2)).json() == []
    assert client.get(f"/api/v1/reports/{a}").json()["status"] == "PENDING_VERIFICATION"


def test_my_vote_is_private_and_survives_new_client():
    id_ = report()
    url = f"/api/v1/reports/{id_}/votes"
    assert client.get(url + "/mine").status_code == 401
    assert client.get(url + "/mine", headers=bearer(1)).json() == {"vote": None}
    assert client.post(url, headers=bearer(1), json={"vote": "SUPPORT"}).status_code == 201
    assert TestClient(app).get(url + "/mine", headers=bearer(1)).json() == {"vote": "SUPPORT"}
    assert client.get(url + "/mine", headers=bearer(2)).json() == {"vote": None}
    assert client.post(url, headers=bearer(1), json={"vote": "SUPPORT"}).status_code == 409
    assert client.get(f"/api/v1/reports/{id_}").json()["support_votes"] == 1


def test_overview_uses_real_counts_and_no_secrets():
    report("VERIFIED")
    report("REJECTED")
    body = client.get("/api/v1/admin/overview", headers=bearer(3)).json()
    assert body["report_counts"]["VERIFIED"] == 1
    assert body["report_counts"]["REJECTED"] == 1
    assert body["user_counts"]["ADMIN"] == 1
    assert body["duplicate_radius_m"] == 30
    assert isinstance(body["ai_ready"], bool)
    assert not any(key in str(body).lower() for key in ["password", "api_key", "secret_key", "database_url"])
