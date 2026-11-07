"""Fail on stale generated OpenAPI or absent handoff artifacts."""
import json
import sys
from pathlib import Path
root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / "backend"))
from app.main import app
actual = app.openapi()
assert json.loads((root / "docs/api/openapi.json").read_text(encoding="utf-8")) == actual
for name in ("TV1_MAP_ROUTING_HANDOFF", "TV2_REPORT_COMMUNITY_HANDOFF", "API_CONTRACT_LOCK", "RELEASE_READINESS", "LOCAL_DEVELOPMENT"):
    path = root / "docs/handoff" / (name + ".md")
    assert path.exists() and path.stat().st_size > 100, name
assert "/api/v1/routes/compare" in actual["paths"]
assert "normalized" in actual["components"]["schemas"]["DetectionItem"]["properties"]["bbox"]["description"].lower()
print("PASS: runtime OpenAPI matches committed artifact; five handoff documents present")
