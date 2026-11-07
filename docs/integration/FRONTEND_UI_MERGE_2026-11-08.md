# Frontend UI integration - 2026-11-08

## Provenance and recovery

- Local integration branch: `integration/frontend-ui-preserve-fixes-20261108`.
- Incoming `origin/frontend`: `9f49601` (fetched for this integration).
- Recovery checkpoint: `5830b26`, preserving all previous auth, role, concurrency and report-draft fixes on `local/main-latest-20261107`.
- Local merge only. No push or cloud deployment. `.vscode/` settings excluded.

## Preservation and conflict resolution

Incoming map overlays, navigation, route controls, onboarding images, icons, colors, themes and report layouts are retained. Compared with incoming resources, only login/register layouts, the local session-gate layout and `strings_flow.xml` differ to retain existing authentication behavior/messages; incoming login/register typography changes are included. The new map was visually checked using the physical-device screenshot.

Three merge conflicts were resolved: NetworkDataSource keeps the localized overall risk availability/recommendation/disclaimer while accepting incoming per-route summaries and generic crack mapping; ReportWizardFragment keeps prior draft-reset guards with the incoming summary/location flow; Tv2IntegrationTest retains the authenticated fixture and follows the incoming navigation.

Login/session/token handling and backend authentication, authorization, review-lock and role-audit fixes are preserved. MainActivity still gates report creation behind login, then opens the incoming direct report flow. No account passwords or real user roles were changed.

Compatibility fixes added:

- Saved draft categories migrate from seven old indices to four incoming categories. Old Repair requires a new explicit selection; confirmation resets while uncertain-write state and draft content remain intact.
- Discard cancels pending address lookups and clears the manual address field, preventing old callbacks/addresses from repopulating a new draft.
- Navigation test explicitly logs in before testing the authenticated report flow. Device fixture selects the new pothole index while preserving its D40 assertions.

Incoming backend dotenv/API-key changes are included and tested. The local backend image was rebuilt for isolated QA; the existing local backend service was not redeployed.

## Verification

| Check | Result |
|---|---|
| Android JVM tests | 88 passed, 0 failures |
| Debug app and instrumentation APK builds | PASS |
| Android lint | PASS |
| Backend suite | 208 tests + 12 subtests passed |
| Ruff and migration upgrade/downgrade/upgrade | PASS |
| Runtime OpenAPI and supplied examples | PASS |
| Xiaomi Android 13 physical-device E2E | PASS |
| Live Google Maps and Google Routes | PASS |
| Backend restart persistence | PASS |

The first Android run identified a navigation-test fixture that attempted the new report flow as a guest; the auth gate correctly blocked it. The corrected authenticated fixture and full suite passed.

Physical E2E used disposable backend/database/accounts: USER registration, synthetic photo with real AI/manual/upload/create/vote, REVIEWER password login and verification/map, ADMIN password login/read screens/activity recreation/logout, then backend restart persistence. Disposable resources were removed and the normal USB mapping restored. The merged APK is installed on the phone.

Local ignored evidence: `android/build/reports/qa-roles/frontend-merge-{android,backend,contract,device,image}.log` and `frontend-merge-live-map.png`.

## Limits

This verifies the exercised scenarios, not absence of every possible bug. Camera/picker system gestures and EXIF coverage remain unverified; the image fixture is synthetic. Notification UI is inherited and is not a claim of a new notification service. No production acceptance or deployment is implied.

Unresolved conflict list is empty. Git whitespace check reports one pre-existing incoming trailing space in `item_primary_button.xml` line 2; the upstream visual resource was retained unchanged.
