# Bàn giao giao diện TV1 — 2026-11-07

## Phạm vi và nguồn

Làm việc trong `SafeRoute-AI`, nhánh `feature/tv1-map-routing-ui`, xuất phát từ `main` tại `7a78359`; repo chưa có nhánh frontend tại lúc bắt đầu.
Người dùng xác nhận TV3/TV5 chưa có backend/OpenAPI và yêu cầu tiếp tục phần giao diện.
Đã đối chiếu AGENTS, README, tracker, prompt chung, phân công trong tài liệu đính kèm. UI spec cũ chỉ dùng màu sắc/phong cách; Java/XML và routing P0 theo tài liệu mới hơn.

Project nằm duy nhất ở `android/`. Không sửa backend, AI, hạ tầng, workflow hoặc phần tính năng TV2.
Không chép skeleton Java/XML rỗng từ repo demo. Gradle Wrapper được tạo bằng Gradle 9.1.0 trên máy.

## API-LOCK còn thiếu

| Owner | Cần bàn giao |
|---|---|
| TV3 + TV2 | Reports DTO thật: tọa độ, loại, trạng thái, ID, mô tả, lỗi; phạm vi vùng/pagination |
| TV5 + TV4 + TV3 | Routes DTO: request A/B/mode; polyline hay points và độ chính xác; distance/duration units; risk score/scale; recommended_id; reason/explanation |
| TV2 | Một adapter từ shared Retrofit/repository vào `Tv1DataSource`; session, token và ánh xạ lỗi |
| TV1 + TV2 | Review package, MainActivity, Gradle và cách ghép tab Báo cáo/Cá nhân trước merge |
| TV1 | Android Maps key giới hạn package/SHA-1; test trên thiết bị |

Không xuất OpenAPI giả; không tự nhận JSON đã khóa. Không có HTTP call trong phần UI hiện tại.

## Hợp đồng nội bộ UI (không phải DTO API)

- `Point`: WGS84 latitude/longitude, kiểm tra hữu hạn và giới hạn ±90/±180.
- `Hazard`: presentation model, loại và trạng thái độc lập. `REPAIR` tương ứng nhãn Repair; không là RESOLVED.
- `Route`: durationSeconds, distanceMeters, riskScore nullable, verifiedCount, pendingCount, points và reason. Không tính risk trong Android.
- `RouteComparison`: chỉ chứa tuyến nguồn thực trả; recommendedId nullable, scoreDescription mô tả thang điểm. Không tự chọn tuyến có điểm thấp nhất làm khuyến nghị.
- `Tv1DataSource.loadReports(callback)` và `compare(origin, destination, callback)` trả `Request.cancel()`.
- Callback chạy trên thread bất kỳ; ViewModel chuyển về main thread, hủy request cũ, bỏ callback lỗi thời và timeout 30 giây.
- Nguồn mặc định trả NOT_CONNECTED. Danh sách rỗng thành công mang ý nghĩa khác với mất kết nối.
- Callback success(null), ID tuyến trùng, khuyến nghị không thuộc danh sách, tọa độ/geometry/risk không hợp lệ đều bị từ chối.
- `RouteGeometry.decodePolyline` chỉ dùng cho Google encoded polyline precision 5 khi owner xác nhận đúng encoding.
- Không nối A/B thành đường thẳng dự phòng. Không tạo tuyến thay thế khi chỉ có một tuyến.
- Khi ghép API, cần khóa giới hạn số report/phạm vi/pagination; UI hiện render danh sách nhỏ, chưa có paging cho dữ liệu lớn.
- Kết quả tuyến giữ trong ViewModel qua configuration change; sau process death giữ A/B và yêu cầu tải lại kết quả, không lưu polyline lớn vào Bundle.
- Refresh map gọi lại nguồn reports. Tìm tuyến lại lấy risk mới; không sửa risk từ dữ liệu cache phía client.

## Bằng chứng chạy local

Build APK và APK instrumentation PASS; 33/33 tests JVM PASS; lintDebug PASS (0 lỗi, 15 cảnh báo). Xem [kiểm chứng local](../qa/tv1-validation.md) để biết lệnh, môi trường và giới hạn.
Báo cáo máy sinh ở:
- `android/app/build/reports/tests/testDebugUnitTest/index.html`
- `android/app/build/reports/lint-results-debug.html`
- `android/app/build/outputs/apk/debug/app-debug.apk`

Chưa có commit/PR. Trạng thái cài/test USB và ảnh chụp được ghi trong báo cáo kiểm chứng; chưa xác minh Maps nền, GPS thật, tile/key errors hoặc API/cloud/E2E.
Code review riêng đã xác nhận sửa hai lỗi: nội dung map cuộn được khi thiếu chiều cao, và GPS giữ lại điểm A khi chưa có Maps.

## Checklist chạy trên điện thoại

1. Cài APK: mở không key không crash; chuyển tab; Back; xoay màn hình; font scale 1.3–2.0 và light/dark.
2. Maps key hợp lệ: nền map/tile, marker/pending label, chạm marker, filter và preview.
3. GPS: cấp coarse-only/fine, từ chối, từ chối vĩnh viễn, tắt location, timeout; không dùng tọa độ mặc định làm vị trí người dùng.
4. A/B: nhập sai, ngoài giới hạn, trùng, swap, chọn trên map, bàn phím không che nút; back/recreate giữ dữ liệu.
5. Adapter thật: 0/1/nhiều tuyến; missing risk/reason; 401/403/429/5xx/timeout; marker bị reject biến mất sau refresh.
6. So polyline với response thật, ETA/km/score giữ đúng unit; recommendation không đồng nghĩa an toàn tuyệt đối.

## Rubric liên quan

| Tiêu chí | Minh chứng hiện có / còn thiếu |
|---|---|
| IV.1 Kiến trúc | Một project Java/XML, chia feature và UI data boundary; chờ review/merge |
| IV.2 UI/UX | APK + test UI JVM; cần kiểm thử hình ảnh/tương tác trên thiết bị |
| IV.5 Độ tin cậy | Validation, pending/rejected, timeout/stale response tests; chưa test mạng/GPS thật |
| VIII Teamwork/demo | Handoff owner/API-LOCK và checklist; chưa có demo E2E/video |

Tham khảo kỹ thuật đã tra: [Google Location](https://developers.google.com/android/reference/com/google/android/gms/location/FusedLocationProviderClient), [Maps polylines](https://developers.google.com/maps/documentation/android-sdk/polygon-tutorial), [Robolectric setup](https://robolectric.org/getting-started/).

## Bổ sung TV1 theo Master Prompt V2

Phân công áp dụng: TV1 app shell/map/routing UI; TV2 shared Retrofit/DTO và report/auth/review; TV3 API/OpenAPI/AI integration; TV4 DB/risk/cloud; TV5 Google Routes adapter. Các mô tả cũ trong governance root còn xung đột. TV3/Integration Lead cập nhật R0; TV1 không tự sửa phân công của các thành viên.

### Ghép màn hình TV2

Trong khởi động Application, TV2 gọi `setTv2Screens(...)` với implementation `Tv2Screens.report()` và `profile()` trả Fragment mới. Mặc định trả null, shell hiển thị chưa kết nối. Fragment TV2 phải phục hồi được bằng FragmentManager (constructor rỗng hoặc FragmentFactory đã đăng ký). Tab được giữ đúng khi recreate/back; không thêm Activity/project thứ hai.

Sau khi server xác nhận tạo/sửa/verify/reject báo cáo thành công, TV2 gửi sự kiện trên **activity FragmentManager**:

```java
requireActivity().getSupportFragmentManager()
    .setFragmentResult(MainActivity.REPORTS_CHANGED, new Bundle());
```

Đây là sự kiện nội bộ UI, không phải HTTP contract. TV1 tải lại reports, hủy yêu cầu so sánh cũ, bỏ callback lỗi thời, xóa kết quả/điểm rủi ro và yêu cầu người dùng làm mới tuyến. Không gửi sự kiện trước khi server xác nhận thành công.

### Kiểm thử bổ sung

- JVM: ghép Fragment TV2 và phục hồi tab; coarse-only; từ chối vị trí và fallback nhập tay; kết quả permission sau recreate; landscape/dark/font 2.0; pending/rejected sau refresh; thay đổi báo cáo vô hiệu hóa risk và callback cũ.
- Device suite `Tv1DeviceTest`: 6 ca, fixture ghi rõ MOCK_FOR_DEV. Lệnh sau khi cài app và APK test:
  `adb shell am instrument -w -r -e class com.comeforfood.saferoute.Tv1DeviceTest com.comeforfood.saferoute.test/androidx.test.runner.AndroidJUnitRunner`.
- Ảnh app do suite tạo tại external files `tv1-device-tests/`; không chụp sau khi Activity đã đóng. Kết quả thực tế ở báo cáo kiểm chứng.

### Trạng thái đề xuất để TV3/Lead cập nhật tracker

| Hạng mục TV1 | Trạng thái và bằng chứng |
|---|---|
| Shell/data boundary, trạng thái UI, invalidation sau report update | DONE_VERIFIED ở mức JVM: 33 tests; build/lint PASS |
| Android Maps key và kiểm chứng nền map/GPS thật | BLOCKED: chưa có key; chưa kiểm chứng live |
| Reports/routes adapter + hình học/điểm thật từ server | BLOCKED: TV2/TV3/TV4/TV5 cần bàn giao contract/runtime |
| Demo P0-A/P0-B E2E | BLOCKED bởi hai mục trên |

Không coi test fixture hoặc build APK là hoàn tất toàn bộ DoD TV1 V2.

## Tìm A/B bằng tên địa điểm hoặc địa chỉ

- Nhập tên/địa chỉ, nên kèm tỉnh/thành phố; bấm **So sánh tuyến**, chọn kết quả đúng lần lượt cho A và B. GPS và chọn điểm trên bản đồ vẫn dùng được.
- Dùng Android Geocoder của thiết bị, không cần thêm Places key. Chất lượng/khả dụng phụ thuộc provider và kết nối của máy; không phải Google Places Autocomplete.
- Tìm tối đa 5 kết quả, timeout 15 giây, lỗi/không có kết quả có hướng dẫn thử lại hoặc chọn trên bản đồ. Không tự chọn kết quả đầu tiên.
- Tên hiển thị được lưu riêng với tọa độ đã xác nhận. Sửa nội dung xóa tọa độ cũ; đổi A/B đổi cả tên và tọa độ. Màn kết quả dùng tên địa chỉ; adapter vẫn nhận Point như trước.
- `AddressSearch` là boundary nội bộ TV1, tách khỏi Retrofit/DTO reports/routes. `AndroidAddressSearch` dùng API async trên Android 13+, worker giới hạn trên Android cũ, bỏ kết quả sau hủy/timeout. Callback được trả trên main thread.
- Test JVM dùng `-PMAPS_API_KEY=` để không khởi tạo Maps live; không thay key local. Khi build/cài app thật, bỏ tham số này.
- Live geocoder test chỉ chạy khi truyền `-e liveGeocoder true`; mặc định bỏ qua để test offline không phụ thuộc dịch vụ bên ngoài.

Nguồn kỹ thuật: [Android Geocoder](https://developer.android.com/reference/android/location/Geocoder).
