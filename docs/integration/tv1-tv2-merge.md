# Tích hợp giao diện TV1 + TV2

Ngày 2026-11-07. Bản ghép đã được kiểm thử tại local trước khi chuẩn bị commit và đưa lên frontend theo yêu cầu người dùng. Lịch sử frontend/TV2 được giữ bằng merge commit; nội dung Android dùng bản đã tích hợp thủ công và kiểm thử, không nhập lại bộ Gradle Groovy/nav_graph cũ.

## Nguồn và phạm vi

- TV2: origin/feature/tv2-report-community, commit 384e1bf5c24691ea1f5aadafc527bee6cbdd1827; PR #2 đã có trên origin/frontend (11a14ac).
- Giữ MainActivity, Gradle Kotlin DSL, manifest và cấu hình Maps của TV1. Nhập các màn auth, report, reports, profile, community, review cùng DTO/session của TV2. Không tạo app hoặc network client thứ hai.
- AppNavigation chuyển các action của TV2 sang FragmentManager chung. Tab Báo cáo/Cá nhân mở màn TV2; bấm lại tab từ màn con trở về màn gốc.
- Màu TV2 dùng alias tới theme TV1, hỗ trợ sáng/tối; tiêu đề 24sp, lề 20dp, bo góc 12dp, vùng chạm tối thiểu 48dp. Chuỗi XML đưa vào resources, icon camera/vị trí/lịch sử thống nhất.
- Giữ luồng tìm A/B bằng tên/địa chỉ, Maps và boundary datasource của TV1.

## Giới hạn chức năng

Backend chưa nối: auth, gửi/lấy báo cáo, AI, vote/review và routes chưa chạy E2E. Không đăng nhập giả, tự cấp reviewer, đặt GPS cố định hoặc trình bày detection/báo cáo mẫu như dữ liệu thật. Báo cáo lấy GPS qua foreground location; chọn ảnh từ thư viện, chưa bổ sung chụp camera. Reviewer cần quyền hợp lệ; kiểm tra phía Android không thay thế RBAC server.

DTO TV2 được giữ từ nguồn, chưa được xác nhận bằng OpenAPI backend. Cần chốt contracts với TV3/TV4/TV5 trước khi viết adapter.

## Bằng chứng

- 47/47 JVM tests PASS: 16 model, 22 TV1 UI, 2 AuthDto, 7 integration.
- assembleDebug, assembleDebugAndroidTest, lintDebug PASS. Lint: 0 errors, 83 warnings (chưa xử lý hết cảnh báo của code nhập vào).
- Xiaomi 2201117TG / Android 13: AccountReportDeviceTest PASS, 1 test, 16.82 giây. Kiểm tra điều hướng auth/history/community, guest bị chặn reviewer, reselect tab và theme tối.
- Log: frontend/build/reports/tv1/tv2-merge-tests.log, tv2-merge-build.log, tv2-device-final.log.
- Ảnh: frontend/build/reports/tv1/device-final/merged-*.png. Đã kiểm tra trực quan các màn báo cáo, profile tối, login, lịch sử và cộng đồng sau sửa cuối.
- Hai lượt native trước không vào RESUMED vì màn hình khóa; chạy lại thành công sau khi người dùng mở khóa. Không tính các lượt lỗi là PASS.
- APK cuối đã cài trên máy; git diff --check PASS. local.properties vẫn bị Git bỏ qua.

## Khôi phục và bàn giao

Snapshot trước tích hợp: frontend/build/integration/tv1-before-tv2.zip. Source TV2: frontend/build/integration/tv2-source/. Manifest nhập: frontend/build/integration/tv2-import.json. Các file này nằm trong build bị Git bỏ qua, không phải artifact được version control. Không chứa local.properties.

Không thay các thay đổi sẵn có ở root README.md, AI_TRACKER.md hoặc .vscode trong đợt tích hợp này.
