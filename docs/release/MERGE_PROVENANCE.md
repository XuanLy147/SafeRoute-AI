# Full release merge provenance — 2026-11-07

- Main source: `7a78359f2b3a1f57a417cf3bef35e80227b4f73f`.
- Frontend source: `e096f40be65e260b2934f23e1f8d24a8b9dbc703`.
- TV345 source: `0877ef1c7a05690d442839008a9f2028e3c6c825`.
- Fetch confirmed all three match the requested references.
- Worktree: `C:/dev/SafeRoute-AI-full-release`, branch `integration/saferoute-full-release`.
- Original `C:/dev/SafeRoute-AI` stays on TV4 with all pre-existing uncommitted changes preserved.

`d42c939` merges frontend history into main in the release worktree. Frontend
already includes TV345 in ancestry through d53d6f8, then e096f40 restores the
pre-backend tree. Therefore the subsequent TV345 merge says Already up to date
while implementation files are absent. A dedicated reconciliation commit restores
backend, ai, infra, scripts, backend CI, API/handoff, database and integration
evidence from `0877ef1c7a05690d442839008a9f2028e3c6c825`. Frontend-only integration documents are retained.
No history rewriting, source branch edits or force push.

Before Android implementation, `git diff origin/frontend -- android` was empty.
Before CI edits, restored backend/ai/infra/scripts/API/handoff trees matched the
TV345 source exactly. Both migrations 0001/0002 and all source tests are retained.
Android changes after reconciliation connect the existing presentation boundary;
there is still one MainActivity, one app, one Retrofit client and one DB schema.

All remote merge permission remains conditional on mandatory gates and review.
This document records integration, not authorization to bypass a failed gate.
