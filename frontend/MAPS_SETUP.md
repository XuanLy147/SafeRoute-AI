# Google Maps SDK + điện thoại USB (TV1)

Dùng project chính `SafeRoute-AI`. Android chỉ cần Maps SDK key; Google Routes API chạy ở backend.

## Tạo key

1. Mở [Google Cloud Console](https://console.cloud.google.com/), chọn hoặc tạo project của nhóm.
2. Vào Billing và liên kết tài khoản thanh toán. Google yêu cầu project bật billing; người dùng tự thực hiện bước tài khoản/thanh toán.
3. APIs & Services > Library > tìm **Maps SDK for Android** > Enable.
4. Google Maps Platform > Keys & Credentials (hoặc APIs & Services > Credentials) > Create credentials > API key. Trong API restrictions chỉ chọn **Maps SDK for Android**.
5. Mở key vừa tạo, phần Application restrictions chọn **Android apps**, thêm:
   - Package: `com.comeforfood.saferoute`
   - SHA-1 debug của máy hiện tại: `2A:00:2D:8F:BE:18:AB:34:D4:5C:65:4A:7F:74:5D:F1:AF:0B:96:17`
6. Kiểm tra API restrictions chỉ cho **Maps SDK for Android**, rồi Save.

## Đặt key trong máy

Mở `frontend/local.properties` đã được Git bỏ qua; giữ dòng sdk.dir, điền key thật sau dấu bằng:

```properties
sdk.dir=D\:/Android/Sdk
MAPS_API_KEY=YOUR_ANDROID_MAPS_KEY
```

Không dùng nguyên chuỗi ví dụ làm key; không gửi key vào chat hoặc commit file này.
Biến môi trường/Gradle property `MAPS_API_KEY`, nếu được đặt, có độ ưu tiên cao hơn file local.
Sau khi lưu, build/cài lại APK; thay file local không tự thay key trong app đang cài.

## Chạy qua USB

1. Bật Developer options > USB debugging; cắm USB và chấp nhận hộp thoại RSA.
2. Trên Xiaomi, nếu báo `INSTALL_FAILED_USER_RESTRICTED`, kiểm tra riêng **Install via USB / Cài đặt qua USB**, mở khóa màn hình và chấp nhận hộp thoại cài SafeRoute. Không cần tắt Play Protect.
3. `adb devices -l` cần trạng thái `device`, không phải `unauthorized`.
4. Từ thư mục `frontend/` chạy `.\gradlew.bat :app:installDebug`, hoặc Run trong Android Studio; sau đó mở SafeRoute AI.

Nếu nền bản đồ chưa tải: kiểm tra package/SHA-1, SDK đã bật, restriction, project billing, mạng và Google Play services. Việc bật SDK có thể cần vài phút để có hiệu lực.
Maps key chỉ mở bản đồ; routes/reports thật vẫn cần adapter và backend do nhóm bàn giao.

SHA-1 của máy/người ký khác sẽ khác; lấy lại bằng `.\gradlew.bat :app:signingReport`.
Key giới hạn theo debug certificate chỉ dành cho bản debug này. Bản release cần chứng thư và restriction tương ứng.

Nguồn: [Google Maps SDK setup](https://developers.google.com/maps/documentation/android-sdk/get-api-key), [Android USB device](https://developer.android.com/studio/run/device).
