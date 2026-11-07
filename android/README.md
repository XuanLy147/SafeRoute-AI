# SafeRoute Android — phần giao diện TV1

Project Java 17 + XML Views + ViewBinding trong repository chính `SafeRoute-AI`.
Một `MainActivity`, dùng chung cho TV1/TV2. Các thay đổi hiện ở nhánh `feature/tv1-map-routing-ui`, chưa commit/push.

## Chạy project

Mở **thư mục `android/` này** bằng Android Studio, Gradle Sync, chọn thiết bị rồi Run.
Toolchain được giữ theo phiên bản đã có trên máy: AGP 9.0.1, Gradle Wrapper 9.1.0 (có SHA256), compile/target SDK 35, min SDK 26, Java source/target 17.
Máy kiểm tra chạy Gradle bằng JDK 24; chưa kiểm thử bằng JDK khác.

CLI (PowerShell, đứng trong `android/`):

```powershell
$env:ANDROID_HOME = 'D:\Android\Sdk' # thay theo máy
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Android Studio có thể tự tạo `local.properties` chứa `sdk.dir`; file này đã được bỏ qua bởi Git.

## Google Maps

Đọc key theo thứ tự: biến môi trường `MAPS_API_KEY` → Gradle property → `local.properties`.
Chỉ đặt **Android Maps SDK key** có giới hạn package `com.comeforfood.saferoute` và SHA-1 của chứng thư ký phù hợp.
Không đặt Google Routes server key hay token backend vào Android.
Không commit key hoặc `local.properties`. Android Maps key sẽ nằm trong APK theo cơ chế SDK, cần hạn chế key trên Google Cloud.

Không có key: app mở bình thường, hiển thị trạng thái chưa có bản đồ; vẫn nhập A/B và lấy GPS làm điểm A.
Hướng dẫn tạo key và cấu hình đúng package/SHA-1: [MAPS_SETUP.md](MAPS_SETUP.md).

Có key chưa đảm bảo Maps hoạt động: cần SDK được bật, restriction đúng, Google Play services và kết nối mạng. Chưa kiểm thử live Maps trong phiên này.

## Phạm vi đã triển khai

- App shell với tab Bản đồ/Tìm đường; TV2 đăng ký Fragment Báo cáo/Cá nhân qua `Tv2Screens` dùng chung host. Khi chưa đăng ký, hiển thị thông báo chưa kết nối.
- Bản đồ Google khi được cấu hình; marker theo trạng thái, lọc ổ gà/vết nứt và chỉ đã xác minh.
- Loại bỏ REJECTED khỏi danh sách và marker. Pending luôn có nhãn chưa xác minh.
- Preview cảnh báo và dùng vị trí cảnh báo làm điểm đến.
- Quyền vị trí chính xác/gần đúng; GPS foreground có timeout, xử lý từ chối và mở cài đặt quyền.
- Nhập tên địa điểm/địa chỉ A/B, chọn kết quả tìm được; GPS và chọn điểm trên bản đồ; đổi A/B và giữ cả tên/tọa độ khi xoay màn hình.
- Màn kết quả: loading/lỗi/retry/không có tuyến/một tuyến/nhiều tuyến; chọn tuyến, vẽ đúng polyline, ETA/km, điểm rủi ro do nguồn dữ liệu cung cấp, lý do và giới hạn dữ liệu.
- Giao diện tiếng Việt, light/dark, nội dung cuộn, xử lý system bars/keyboard insets.

**Chưa có backend/OpenAPI/Maps key.** Đã nhận điện thoại USB; kết quả cài/chạy được ghi riêng trong [báo cáo kiểm chứng](../docs/qa/tv1-validation.md). Bản APK mặc định không chứa marker/tuyến giả; thao tác tìm đường sẽ báo chưa kết nối.
Dữ liệu tổng hợp chỉ nằm trong `src/test/` và `src/androidTest/`, không đóng gói vào APK ứng dụng.

## Điểm nối dành cho TV2/TV3/TV5

`feature/shared/Tv1DataSource` là giao diện cung cấp dữ liệu cho UI, **không phải JSON contract**.
TV2 cài một adapter dùng repository/Retrofit chung bằng `SafeRouteApp.setDataSource(...)` trong lúc khởi động ứng dụng.
TV3/TV5 phải chốt DTO thực tế trước khi viết adapter; xem [bàn giao TV1](../docs/integration/tv1-android-ui.md).
Không thêm HTTP client khác, không gọi Google Routes trực tiếp từ Android.

## Kiểm thử

```powershell
.\gradlew.bat :app:testDebugUnitTest '-PMAPS_API_KEY='
```

16 JUnit tests kiểm tra tọa độ/polyline, số lượng tuyến, khuyến nghị và lọc trạng thái.
22 Robolectric tests (SDK 35) kiểm tra navigation, validation, swap, recreation/back, empty/single/multiple routes, timeout và stale callbacks.
Các test UI chạy với Maps key để trống.

6 test instrumentation đã PASS trên Xiaomi 2201117TG (Android 13), dùng fixture MOCK_FOR_DEV. Xem [báo cáo QA](../docs/qa/tv1-validation.md) để biết log, ảnh chụp và giới hạn kiểm thử live.

Robolectric 4.17 native runtime có lỗi đọc đường dẫn Windows chứa khoảng trắng trên máy kiểm tra.
Khi gặp `NoSuchFileException` chứa `%20`, dùng đường dẫn Windows 8.3 tới thư mục chứa JAR runtime đã tải:

```powershell
.\gradlew.bat :app:testDebugUnitTest '-ProbolectricRuntimeDir=<thu-muc-runtime-khong-co-khoang-trang>'
```

Tùy chọn này chỉ ảnh hưởng test, không ảnh hưởng APK. Xem lệnh và kết quả đã chạy trong [bàn giao TV1](../docs/integration/tv1-android-ui.md).
Không thay test JVM bằng bằng chứng chạy trên điện thoại: cần cài APK để kiểm tra Maps/GPS/render thực tế.

## Tìm A/B bằng tên địa điểm hoặc địa chỉ

- Nhập tên/địa chỉ, nên kèm tỉnh/thành phố; bấm **So sánh tuyến**, chọn kết quả đúng lần lượt cho A và B. GPS và chọn điểm trên bản đồ vẫn dùng được.
- Dùng Android Geocoder của thiết bị, không cần thêm Places key. Chất lượng/khả dụng phụ thuộc provider và kết nối của máy; không phải Google Places Autocomplete.
- Tìm tối đa 5 kết quả, timeout 15 giây, lỗi/không có kết quả có hướng dẫn thử lại hoặc chọn trên bản đồ. Không tự chọn kết quả đầu tiên.
- Tên hiển thị được lưu riêng với tọa độ đã xác nhận. Sửa nội dung xóa tọa độ cũ; đổi A/B đổi cả tên và tọa độ. Màn kết quả dùng tên địa chỉ; adapter vẫn nhận Point như trước.
- `AddressSearch` là boundary nội bộ TV1, tách khỏi Retrofit/DTO reports/routes. `AndroidAddressSearch` dùng API async trên Android 13+, worker giới hạn trên Android cũ, bỏ kết quả sau hủy/timeout. Callback được trả trên main thread.
- Test JVM dùng `-PMAPS_API_KEY=` để không khởi tạo Maps live; không thay key local. Khi build/cài app thật, bỏ tham số này.
- Live geocoder test chỉ chạy khi truyền `-e liveGeocoder true`; mặc định bỏ qua để test offline không phụ thuộc dịch vụ bên ngoài.

Nguồn kỹ thuật: [Android Geocoder](https://developer.android.com/reference/android/location/Geocoder).

## Giao diện TV1 + TV2 đã ghép tại local

Tab Báo cáo và Cá nhân hiện mở các màn TV2, dùng chung navigation và theme sáng/tối của TV1. Auth, báo cáo, AI, góp ý và reviewer chưa nối backend; UI thông báo đúng trạng thái chưa kết nối. Chi tiết nguồn, giới hạn và kiểm thử: [bàn giao tích hợp](../docs/integration/tv1-tv2-merge.md).

## Luồng theo HTML — cập nhật 2026-11-07

Luồng hiện tại thay thế mô tả tab/điểm vào cũ: Welcome → giới thiệu → quyền → Login; USER có Bản đồ / Báo cáo / Của tôi / Tài khoản. Tìm đường mở từ Bản đồ. Báo cáo đi theo từng bước ảnh → GPS → AI/manual → kiểm tra → gửi/thử lại, có giữ draft. Reviewer/admin có workspace và menu riêng theo quyền của phiên.

Backend auth chưa kết nối nên có nút **Xem bản đồ với tư cách khách**. App không có nút tự chọn vai trò; reviewer/admin chỉ xuất hiện khi có phiên hợp lệ. Tests dùng fixture riêng, không lưu quyền thử nghiệm vào app sau khi chạy.

Xem [đối chiếu đầy đủ với HTML](../docs/integration/html-frontend-flow.md), gồm cả các màn mở rộng chưa có API. Không đổi Google Routes sang OSRM của bản mẫu HTML.

## Backend đã ghép vào frontend

Source TV345 và OpenAPI hiện nằm cùng repository. Xem [handoff TV1](../docs/handoff/TV1_MAP_ROUTING_HANDOFF.md), [handoff TV2](../docs/handoff/TV2_REPORT_COMMUNITY_HANDOFF.md) và [bản ghép](../docs/integration/frontend-tv345-merge.md). App vẫn cần triển khai shared HTTP/image client theo contract; chưa có Android–backend E2E. Các mô tả thiếu OpenAPI ở mốc QA cũ là lịch sử.
