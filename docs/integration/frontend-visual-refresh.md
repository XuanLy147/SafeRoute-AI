# Làm mới giao diện theo ảnh tham chiếu — 2026-11-07

## Phạm vi và kết quả

Thực hiện trong repository `SafeRoute-AI`, nhánh `feature/tv1-map-routing-ui`. Yêu cầu: làm giao diện đẹp hơn theo ảnh welcome/login/register xanh–trắng và sửa logo mở đầu. Giữ Android Java/XML native và luồng USER/REVIEWER/ADMIN đã có.

- Welcome: logo mascot trắng trên ô xanh bo góc, tên SafeRoute, slogan và nút Bắt đầu dưới màn hình.
- Login/register: tiêu đề giữa, input nền sáng bo tròn, nút chính xanh, nút mạng xã hội dạng pill; có nhãn trường, autofill và vùng chạm tối thiểu 48dp. Form rộng tối đa 440dp, cuộn được khi màn hình ngắn.
- Map/routing: thanh tìm kiếm, khung bản đồ bo góc, phân cấp GPS/tìm đường và panel trạng thái.
- Report: hướng dẫn ba bước có icon, thanh tiến trình, ảnh chờ gọn hơn, lựa chọn loại/mức độ thành hàng đều nhau.
- Account/reviewer/admin: danh sách điều hướng có mũi tên, nhóm nội dung rõ ràng, màu sáng/tối đồng bộ. Thanh điều hướng có nền chọn nhẹ.

Logo nằm trong `android/app/src/main/res/drawable/ic_saferoute_logo.xml`, được dựng lại bằng vector từ ảnh nhỏ người dùng gửi. Đây không phải file logo gốc; không khẳng định trùng từng pixel. Manifest dùng cùng logo cho icon ứng dụng.

## File dùng chung

Styles: `values/styles_polish.xml`; màu: `values/colors_polish.xml` và `values-night/colors_polish.xml`; nhãn mới: `values/strings_polish.xml`. Các Fragment động dùng `item_navigation_action.xml`; model/API contract không đổi. TV1/TV2 cùng chịu ảnh hưởng ở app shell, auth/report/profile và màn role.

## Kiểm chứng

- JVM: **53/53 PASS**, 0 failures/errors.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: **PASS**.
- Lint: **0 errors, 159 warnings**; 122 cảnh báo resource không dùng. Còn 6 cảnh báo LabelFor do trường có cả nhãn liên kết và hint minh họa. Không vô hiệu hóa lint; chưa chạy TalkBack thủ công.
- Xiaomi 2201117TG / Android 13: `HtmlFlowDeviceTest` **PASS (1 test), 37.91 giây** ở lượt xác nhận cuối.
- Đã kiểm tra ảnh native: welcome/login/register, map/routing, report, account, reviewer/admin, admin/login tối, register ngang. Lượt đầu thấy hàng radio rộng không đều; đã sửa một đợt và kiểm tra lại.
- Lượt cuối thấy Google Maps tải tiles thật trong khung mới. Đây không phải bằng chứng routing/report backend hoạt động.
- APK cuối đã cài và app thường đã mở lại. Session fixture được khôi phục, draft thử nghiệm được xóa; orientation test trả về mặc định Activity.

Log local trong `android/build/reports/tv1/`: `visual-refresh-unit.log`, `visual-refresh-final-build.log`, `visual-refresh-device.log`, `visual-refresh-confirm.log`. Ảnh cuối trong `visual-refresh-confirm/` gồm `flow-01` đến `flow-19`, `visual-register.png`, `visual-route-search.png`, `visual-login-dark.png`, `visual-register-landscape.png`. Thư mục kéo về có cả ảnh từ các bài kiểm thử trước, chỉ các tên này thuộc lượt hiện tại. Build artifacts và ảnh giữ local, không commit.

## Giới hạn và bàn giao

Auth, AI, gửi report, review và so sánh tuyến vẫn cần backend/contract đã thống nhất. Không tạo đăng nhập, số liệu, tuyến hay báo cáo thành công giả. Các role/report trong bài test là fixture, không phải dữ liệu thật. Không thay Maps key hoặc push GitHub trong lượt chỉnh giao diện này.

Thay đổi hiện ở working tree, chưa commit/push. Các sửa có sẵn trong README, AI_TRACKER và .vscode được giữ; tracker chỉ bổ sung nhật ký của lượt này.
