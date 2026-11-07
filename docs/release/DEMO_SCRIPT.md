# Integrated demo script

1. Start migrated local PostGIS/backend per ../handoff/LOCAL_DEVELOPMENT.md;
   check /health and /ready. Install configured debug APK.
2. Register USER in app; choose/capture a non-private road image and choose location.
3. Run AI; explain model confidence separately from severity. Edit labels or remove
   AI suggestions and use manual input; confirm before submitting.
4. Open My Reports/detail and confirm the real ID and PENDING_VERIFICATION status.
5. Login to a real authorized reviewer account, open queue, inspect private image,
   enter reason, verify/reject. Confirm audit and refreshed public map. Votes do not verify.
6. Choose actual A/B; backend calls Google Routes. Show only returned candidates,
   their ETA, geometry and nullable risk. Explain policy status and recorded-hazard disclaimer.
7. Demonstrate permission denial/offline/manual fallback without claiming success.

Use dedicated local test accounts. Explicit test database reviewer provisioning is
allowed only in the disposable test harness; never change a shared user's role.
Never present test fixtures or unavailable live gates as live provider evidence.
