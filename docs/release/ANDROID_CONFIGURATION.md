# Android configuration and verification

Clone https://github.com/XuanLy147/SafeRoute-AI and checkout
`integration/saferoute-full-release`. Android Studio uses `android/`.
Keep existing AGP 9.0.1 / Gradle 9.1.0. Java source/target remains 17; the Gradle
runtime needs a compatible modern JDK (CI uses 21; local Studio JBR is 25).
Install SDK 35, build tools and platform-tools. Do not commit local.properties.

Put `sdk.dir` and `MAPS_API_KEY` in android/local.properties or use MAPS_API_KEY
environment variable. Restrict the Android Maps key by package/signing certificate
and Maps SDK. Configure the separate server Routes key in backend/.env; never
embed it in the APK. Owner must enable authorized APIs/billing.

Default debug origin is http://10.0.2.2:8000/ (emulator). For a physical device use
`-PAPI_BASE_URL=http://YOUR_BACKEND_LAN_HOST:8000/` with an intentionally accessible
backend. Base origin must have no /api/v1 suffix. Debug-only manifest permits local
HTTP. Release rejects non-HTTPS and requires a verified deployment origin via
`-PAPI_BASE_URL=https://YOUR_VERIFIED_HOST/`; localhost default is not a deployment.

From android/:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
.\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.comeforfood.saferoute.Tv1DeviceTest,com.comeforfood.saferoute.Tv2DeviceTest,com.comeforfood.saferoute.HtmlFlowDeviceTest --console=plain
```

APK: android/app/build/outputs/apk/debug/app-debug.apk. XML reports live under
android/app/build/test-results and outputs/androidTest-results. Device regression
uses explicit fixtures and is never evidence of Google live traffic.

For actual Android/backend E2E, boot a Google APIs emulator, set ANDROID_HOME and
JAVA_HOME, build Docker (`docker build -f backend/Dockerfile -t
saferoute-tv345:integration .`), then run `python scripts/qa/verify_android_e2e.py`.
It starts isolated PostGIS/backend, registers dedicated accounts, grants reviewer
in that disposable DB, builds for the allocated emulator endpoint, installs APKs,
and performs real UI/HTTP/persistence. Synthetic gray image is not accuracy data.
The special E2E APK points to an ephemeral port; rebuild normal debug afterward.
No cloud resources are created. Normal registered accounts always start USER.
