# TV1 — kiểm chứng local ngày 2026-11-07

Repo: `SafeRoute-AI`; nhánh `feature/tv1-map-routing-ui`; base `7a78359`.
Thay đổi local chưa commit/PR. Báo cáo này phân biệt kiểm chứng UI với API/Maps/GPS live.

| Kiểm tra | Kết quả |
|---|---|
| Gradle Wrapper 9.1.0 + SHA256 | PASS, tạo bằng task wrapper |
| assembleDebug + assembleDebugAndroidTest | PASS; APK ứng dụng cuối 7,611,784 bytes |
| JUnit RouteAndHazardTest | PASS, 16 tests, 0 failures/errors |
| Robolectric Tv1UiTest (SDK 35) | PASS, 17 tests, 0 failures/errors |
| lintDebug | PASS, 0 errors, 15 warnings |
| git diff --check; local.properties bị gitignore | PASS |
| Cài APK qua USB | PASS sau khi người dùng cho phép Install via USB trên Xiaomi |
| Tv1DeviceTest trên Xiaomi 2201117TG / Android 13 | PASS, 6 tests; runner báo OK (6 tests), 193,33 giây |
| Ảnh native portrait/light với key trống | Đã chụp và xem màn pending, 1 tuyến/no-hazard và chưa kết nối |
| Maps nền/marker/polyline live, GPS thật, TalkBack, dark/font lớn trên thiết bị | NOT_RUN |
| Backend/OpenAPI, reports/Routes thật, cloud/E2E | NOT_RUN, user xác nhận chưa có backend |

## Bằng chứng và lệnh

Từ `android/`:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug '-ProbolectricRuntimeDir=C:\Users\XUANLY~1\M2E4AB~1\REPOSI~1\org\ROBOLE~1\ANDROI~1\15-ROB~1' --console=plain
```

Đường dẫn 8.3 là cache runtime **riêng của máy kiểm tra**, không phải cấu hình APK.
Máy dùng Windows/JDK 24, Java source/target 17, AGP 9.0.1, Gradle 9.1.0, Android SDK 35.
Robolectric 4.17 ban đầu lỗi giải mã `%20` trong đường dẫn cache Windows; đường dẫn 8.3 khắc phục được.
Các test JVM chạy với Maps key trống.

Bộ test native chạy bằng ADB sau khi cài cả APK ứng dụng và APK test:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.comeforfood.saferoute.Tv1DeviceTest com.comeforfood.saferoute.test/androidx.test.runner.AndroidJUnitRunner
```

Trong phiên đã chọn serial của đúng điện thoại kết nối. Nếu nhiều máy, thêm `-s <serial>`.

Các ca native: 0 tuyến; 1 tuyến/no-hazard; đổi A/B và recreate; nhập sai/chưa kết nối; pending chuyển rejected sau refresh; report update xóa risk cũ. Dữ liệu là **MOCK_FOR_DEV** chỉ ở `src/test/` và `src/androidTest/`, không phải báo cáo/đường thật và không đóng gói vào APK ứng dụng.

Log/report máy sinh (bị gitignore):
- `android/build/reports/tv1/v2-local-validation.log` — build, JVM, lint cuối.
- `android/build/reports/tv1/v2-device-validation.log` — lượt Gradle connected đầu bị INSTALL_FAILED_USER_RESTRICTED; chưa test được ở lượt đó.
- `android/build/reports/tv1/v2-device-instrumentation.log` — chạy trực tiếp thành công 6/6.
- `android/app/build/reports/tests/testDebugUnitTest/index.html`
- `android/app/build/reports/lint-results-debug.html`
- `android/build/reports/tv1/device/` — ảnh native; ảnh mang tên MOCK_FOR_DEV dùng dữ liệu test.
- `android/app/build/outputs/apk/debug/app-debug.apk`

## Lỗi đã sửa và giới hạn

- Map cuộn được, GPS giữ điểm A ngay cả khi thiếu Maps key.
- Root tab TV2 phục hồi đúng sau recreate; report/review update làm mất hiệu lực risk và callback tuyến cũ.
- Lint PropertyEscape: đường dẫn SDK Windows trong local.properties đã escape dấu hai chấm.
- Ảnh thiết bị cho thấy thanh trạng thái tím mặc định với icon tối; đã đổi status/navigation bar sang màu background light/dark sẵn có. Bộ 6 test chạy trước sửa màu; build/JVM/lint đã chạy lại sau sửa.
- 15 cảnh báo lint còn lại: 12 nhắc phiên bản SDK/toolchain/dependency, 2 PluralsCandidate, 1 DataExtractionRules. Không tạo baseline giấu lỗi, không tự nâng stack.
- Các ca permission denied/coarse-only, landscape/dark/font 2.0 hiện chỉ có bằng chứng JVM. Không coi đó là kiểm chứng GPS/TalkBack/hình ảnh thực trên điện thoại.
- Nền Maps, tọa độ GPS, routing thật và P0 E2E còn cần key và backend đã khóa contract.

Mốc trước bổ sung V2 có 26 test JVM và lint 12 warnings. Mốc hiện tại có 33 test JVM, 6 test native và 15 warnings do thêm dependency instrumentation.

## Xác nhận bản cuối sau sửa thanh trạng thái

- Cài lại APK cuối thành công.
- Chạy lại `Tv1DeviceTest#disconnectedAppAndManualInputRemainUsable`: **PASS, OK (1 test)**. Log `android/build/reports/tv1/v2-device-final-smoke.log`.
- Khi runner chờ mở Activity trên Xiaomi, đã chủ động mở `com.comeforfood.saferoute/.MainActivity` bằng `adb shell am start`; đây là smoke test có hỗ trợ khởi động, không đo hiệu năng launch.
- Đã xem lại ảnh `01-map-no-backend.png` và `02-route-not-connected.png`: status bar theo nền sáng, icon tối đọc được; nội dung/điều hướng không bị system bars che. Hai ảnh MOCK_FOR_DEV là ảnh của lượt 6 test trước sửa màu.
- Đã mở lại ứng dụng bình thường sau test. Không thay đổi quyền vị trí/cấu hình hệ thống hoặc cài dữ liệu test vào nguồn mặc định.

## Maps key — cập nhật sau khi người dùng cấu hình

Ngày 2026-11-07: build assembleDebug PASS với key local; metadata manifest khớp cấu hình (chỉ so sánh, không ghi giá trị key). Cài APK qua USB và mở app thành công trên Xiaomi 2201117TG. Đã xem ảnh native `android/build/reports/tv1/device/maps-key-check.png`: nền Google Maps tải được đường và địa danh. **Maps nền live: PASS**. File local.properties vẫn bị Git bỏ qua. Log build: `android/build/reports/tv1/maps-key-build.log`.

Cập nhật này thay thế trạng thái thiếu Maps key/nền Maps NOT_RUN ở các mốc trước. Chưa kiểm chứng GPS live, marker/polyline từ API hoặc E2E; nguồn reports/routes vẫn chưa kết nối backend.

## Nhập A/B bằng địa điểm/địa chỉ — 2026-11-07

- Đã thay luồng yêu cầu tọa độ bằng tên/địa chỉ và xác nhận kết quả tìm được. Lưu tên gốc cùng địa chỉ provider, lưu tọa độ riêng; sửa tên xóa tọa độ cũ, swap/recreate giữ cặp tên/tọa độ.
- JVM: **38/38 PASS** (16 model + 22 UI), gồm 5 ca mới: chọn địa chỉ và swap/recreate; sửa địa chỉ hủy tọa độ; bỏ callback cũ; empty/network retry; hai tên cùng tọa độ bị từ chối.
- Build APK/app-test PASS; lint **0 errors, 15 warnings**. Log: `address-tests.log`, `address-build.log` trong `android/build/reports/tv1/`.
- Xiaomi 2201117TG / Android 13: live `AddressDeviceTest` **PASS, OK (1 test)**, 11,426 giây. Tìm thật “Chợ Bến Thành, TP Hồ Chí Minh” và “Dinh Độc Lập, TP Hồ Chí Minh”, chọn kết quả A/B, xác nhận tọa độ khác nhau và tên được giữ ở màn kết quả. Đây là kiểm chứng tìm địa chỉ, không phải kiểm chứng routing backend.
- Đã xem ảnh native `device/address-form.png`, `address-choice-a.png`, `address-choice-b.png`, `address-result.png`. Sau lượt đầu, bổ sung tên gốc vì provider trả địa chỉ phường/thành phố giống nhau cho hai địa điểm; lượt cuối xác nhận A/B phân biệt được, nội dung xuống dòng đầy đủ.
- Test live mặc định bỏ qua. Chạy riêng sau khi cài app/test:
  `adb shell am instrument -w -r -e liveGeocoder true -e class com.comeforfood.saferoute.AddressDeviceTest com.comeforfood.saferoute.test/androidx.test.runner.AndroidJUnitRunner`.
- Các lượt đầu gặp lỗi harness: ActivityScenario chờ mở Activity/Maps, Xiaomi hạn chế khởi động nền và chụp ảnh trước khi dialog nhận focus. Harness cuối mở Activity bằng shell, chờ lifecycle/focus có giới hạn; không thay quyền/cài đặt hệ thống. Log cuối: `android/build/reports/tv1/address-device-final.log`.
- Nguồn geocoding phụ thuộc thiết bị/kết nối; API async được chạy thật trên Android 13, worker dự phòng Android 26–32 chưa được kiểm chứng trên máy thật. Không thêm Places key, không sửa key local hoặc hợp đồng routes.
- App cuối đã cài và mở lại trên điện thoại. Backend vẫn chưa kết nối, nên chưa vẽ tuyến/đánh giá risk thật.

## Tích hợp TV2 — 2026-11-07

- JVM **47/47 PASS** (16 model + 22 TV1 UI + 2 AuthDto + 7 integration). APK/app-test build PASS; lint **0 errors, 83 warnings**.
- Xiaomi Android 13: Tv2DeviceTest **PASS, OK (1 test), 16.82s**, kiểm tra auth/history/community/reviewer guest, tab reselect và sáng/tối. Hai lượt trước bị màn hình khóa chặn Activity RESUMED; lượt cuối sau mở khóa thành công.
- Log trong android/build/reports/tv1/: tv2-merge-tests.log, tv2-merge-build.log, tv2-device-final.log. Ảnh cuối device-final/merged-*.png; đã xem xác nhận report sáng, profile tối, login, history, community.
- APK cuối đã cài và mở lại. Chưa kiểm thử backend E2E, gửi ảnh/AI/auth/routing thật. Thay đổi mới nằm local, chưa commit/push. Xem [bàn giao](../integration/tv1-tv2-merge.md).

## Luồng Full Mobile Prototype — 2026-11-07

- Phạm vi người dùng xác nhận: USER + REVIEWER + ADMIN, dựa trên SafeRoute_AI_Full_Mobile_Prototype.html.
- JVM: **53/53 PASS**, gồm 6 HtmlFlowTest kiểm tra welcome/onboarding/permissions/login/guest, tab và Back, bản nháp nhiều bước/recreate, không gửi thành công giả, role gate và night/large text.
- APK/app-test build PASS, lint **0 errors, 138 warnings** (107 unused resources trong bộ resource cũ được giữ). git diff --check PASS.
- Native Xiaomi 2201117TG / Android 13: HtmlFlowDeviceTest **PASS (1 test), 30.909s** trong lượt kiểm tra đầu thành công. Lượt trước đó không chụp được do bảng điều khiển nhanh chiếm focus; người dùng đóng bảng và giữ app trước khi chạy lại.
- Đã xem đủ 19 ảnh ở android/build/reports/tv1/html-flow-device/flow-*.png: welcome, onboarding/permissions/login, map, các bước report, history/account, reviewer/admin và admin night. Sau đó sửa một lượt nút Back, bo góc nút phụ, độ ưu tiên action và nhãn điều hướng.
- Ảnh có hậu tố fixture và các bước report dùng ảnh drawable/toạ độ thử nghiệm trong androidTest; không phải report thật, không chạy AI hoặc ghi server. Role được nạp chỉ trong test và khôi phục session cũ khi kết thúc. Không dùng ảnh cá nhân của điện thoại.
- Native test chưa thao tác chụp camera thật, chưa chọn ảnh thật từ thư viện hoặc xác minh GPS live. Kiểm thử luồng chọn/preview dùng fixture; không coi là E2E camera/upload/backend.
- Log JVM/build: html-flow-tests.log, html-flow-build.log trong android/build/reports/tv1/. Lượt native đầu: html-flow-device-final.log; lượt xác nhận sau chỉnh: html-flow-device-confirm.log.
- Bản đồ được chụp khi tiles đang tải; lượt này không dùng ảnh đó để tuyên bố Maps live đã tải hoàn tất. Cấu hình key local không thay đổi.
- Thay đổi đợt này ở working tree, chưa commit/push. [Ánh xạ và giới hạn từng nhóm màn](../integration/html-frontend-flow.md).

- Lượt xác nhận cuối sau chỉnh UI: **PASS, OK (1 test), 42.737s**, log html-flow-device-confirm.log. Ảnh cuối ở android/build/reports/tv1/html-flow-confirm/; đã xem lại map, report start, admin sáng/tối. APK cuối cài và app thường đã mở lại. Không thay cài đặt hệ thống; session test được khôi phục và draft fixture đã xóa.
