# Kiểm thử hồi quy và rà soát câu chữ UI/UX — 08/11/2026

## Phạm vi và kết luận hiện tại

Kiểm thử frontend Android sau khi ghép giao diện, Việt hóa nhãn nguy cơ, thêm khung cảnh báo thu gọn và sắp xếp lại bản đồ. Giữ các thay đổi hiện có; chỉ sửa local, không push.

**Các kiểm thử tự động và lượt E2E chạy lại trên điện thoại đều PASS. Không đồng nghĩa đã loại trừ mọi lỗi hoặc đủ điều kiện phát hành production.**

## Kết quả kiểm thử

| Check | Current result | Evidence |
|---|---|---|
| Android JVM | 90 tests, 0 failures/errors | ux-regression-android.log; XML results |
| App + instrumentation APK | PASS | ux-regression-android.log |
| Android lint | PASS | lint-results-debug.xml |
| Backend | 208 tests + 12 subtests PASS | ux-regression-backend.log |
| Ruff | PASS | ux-regression-backend.log |
| Migration upgrade / downgrade to 0002 / upgrade | PASS, disposable DB | ux-regression-backend.log |
| OpenAPI and supplied JSON examples | PASS | ux-regression-contract.log |
| E2E Xiaomi Android 13 | PASS ở lượt chạy lại; lần đầu bị chặn foreground | ux-regression-device-retry.log |

Log nằm trong thư mục local được Git bỏ qua `frontend/build/reports/qa-roles/`. Lần đầu thất bại ở điều kiện foreground được giữ nguyên trong `ux-regression-device.log`; lượt chạy lại sau khi xác nhận app foreground nằm ở `ux-regression-device-retry.log`. Không dùng kết quả lịch sử để thay cho lần này.

## Độ bao phủ và giới hạn

- Existing Android suite covers navigation/auth gates, session handling, routing/hazard mapping, report drafts and role/lifecycle regressions. Panel tests cover tap, both swipe directions, refresh retaining collapse and activity recreation.
- Backend suite covers auth/RBAC, reports/votes/review, role audit and concurrency; all runs use a temporary database. Migration round trip does not touch live data.
- E2E lượt chạy lại PASS: USER đăng ký → ảnh tổng hợp → AI/tải ảnh/tạo báo cáo/bình chọn thật → REVIEWER đăng nhập/duyệt/bản đồ → Routes thật → ADMIN xem báo cáo/nhật ký quyền/số liệu/cấu hình/tài khoản → tạo lại Activity/đăng xuất → kiểm tra dữ liệu sau khi khởi động lại backend. Đây là một kịch bản xuyên suốt, không phải kiểm thử mọi thao tác của từng vai trò.
- Disposable phone QA resources were cleaned up and USB mapping restored. No real user passwords/roles were changed.
- Native camera/picker gestures, EXIF, tablet layouts, font scaling, TalkBack and dark theme are not comprehensively verified in this run. Do not claim zero bugs.

## Các thay đổi câu chữ trong đợt này

Toàn bộ thay đổi bên dưới chỉ tác động phần hiển thị: giữ nguyên mã API, xác thực, phân quyền và hành vi gửi/thử lại. Chuyển LOW/MEDIUM/HIGH sang tiếng Việt không đổi dữ liệu.

| Mã chuỗi / vị trí | Trước khi sửa | Sau khi sửa |
|---|---|---|
| reports_disconnected | Chưa kết nối dữ liệu cảnh báo. Không có marker không có nghĩa là đường an toàn. | Chưa tải được dữ liệu cảnh báo. Đường không có cảnh báo vẫn có thể có nguy cơ. |
| filter_verified | Chỉ đã xác minh | Chỉ hiện cảnh báo đã xác minh |
| route_auth | Phiên đăng nhập chưa hợp lệ. Vui lòng đăng nhập lại khi tính năng tài khoản sẵn sàng. | Phiên đăng nhập không còn hợp lệ. Vui lòng đăng nhập lại. |
| pending_note | Thông tin này chưa được reviewer xác minh. | Thông tin này chưa được người kiểm duyệt xác minh. |
| report_address_choose | Chọn địa chỉ vị trí báo cáo | Chọn địa chỉ nơi xảy ra nguy cơ |
| report_manual | Nhận diện thủ công | Tự chọn loại nguy cơ |
| role_review_home | Tổng quan reviewer | Tổng quan kiểm duyệt |
| flow_privacy_body | Ứng dụng chỉ lấy vị trí khi bạn yêu cầu và chỉ đọc ảnh bạn chọn. Quyền của hệ thống được quản lý trong Cài đặt Android.\n\nChưa kết nối dịch vụ tải ảnh; chưa xác nhận chính sách lưu trữ hoặc xử lý EXIF của máy chủ. | Ứng dụng lấy vị trí khi bạn yêu cầu và chỉ đọc ảnh bạn chọn. Khi dùng phân tích AI hoặc gửi báo cáo, ảnh được tải lên máy chủ để xử lý. Bạn có thể quản lý quyền truy cập trong Cài đặt Android. |
| report_failure_body | Dịch vụ tạo báo cáo chưa được kết nối. Nội dung đang được giữ trong phiên này để bạn kiểm tra hoặc thử lại. | Chưa xác nhận được báo cáo đã gửi thành công. Bản nháp vẫn được giữ; hãy kiểm tra Báo cáo của tôi trước khi gửi lại để tránh trùng lặp. |
| zero hazard route summary | Tuyến đường thông thoáng, chưa ghi nhận nguy cơ mặt đường. | Chưa ghi nhận nguy cơ gần tuyến. Dữ liệu có thể chưa đầy đủ. |
| severity label | LOW / MEDIUM / HIGH | Thấp / Trung bình / Cao |

### Lý do sửa

- Bỏ từ kỹ thuật như marker, reviewer và mã mức độ, thay bằng tiếng Việt dễ hiểu.
- Bỏ nội dung cũ nói dịch vụ tài khoản/ảnh chưa kết nối. Nội dung quyền riêng tư mô tả việc tải ảnh khi phân tích/gửi báo cáo, không tự cam kết chính sách EXIF hay thời hạn lưu ảnh.
- Không suy diễn tuyến đường thông thoáng chỉ vì chưa có cảnh báo. Nêu rõ dữ liệu có thể chưa đầy đủ.
- Làm rõ tên hành động/bộ lọc. Khi chưa xác nhận gửi thành công, hướng dẫn kiểm tra Báo cáo của tôi trước để tránh gửi trùng; không thêm tự động gửi lại.

## Cải thiện chất lượng kiểm thử

Kiểm thử ADMIN cũ có thể qua chỉ nhờ tiêu đề danh sách dù chưa có đúng báo cáo, đồng thời vẫn dùng mã D40. Đã sửa để bắt buộc tìm đúng hàng gồm ID, tên loại tiếng Việt, trạng thái, thời gian và mô tả của báo cáo. Kiểm thử mới đã PASS trên điện thoại; không thay logic sản phẩm.

## Các điểm còn cần cải thiện — chưa sửa

- Hazard cards can repeat the type in both title and subtitle (e.g. N?t ngang twice); cosmetic redundancy remains.
- Dòng bộ lọc xác minh dài và có chiều cao cố định cần kiểm tra thêm trên máy bật cỡ chữ lớn; chưa kết luận không bị cắt chữ.
- Giao diện thông báo/địa điểm đã lưu không đồng nghĩa dịch vụ đã đầy đủ; các phần chờ tích hợp không nằm trong đợt sửa này.

## Giới hạn để tránh phát sinh lỗi

Không đổi schema, nâng dependency, bỏ kiểm tra đăng nhập, tự động gửi lại thao tác ghi, thay quyền tài khoản thật, commit hoặc push. Giữ các chốt bảo vệ đã sửa ở đợt trước.

### Android test suites

| Suite | Tests | Failures |
|---|---:|---:|
| ApiContractTest | 18 | 0 |
| AuthDtoTest | 2 | 0 |
| AuthFlowTest | 5 | 0 |
| DraftCategoryMigrationTest | 2 | 0 |
| HtmlFlowTest | 9 | 0 |
| RegressionSafetyTest | 3 | 0 |
| RouteAndHazardTest | 17 | 0 |
| MapRoutingUiTest | 22 | 0 |
| AccountReportIntegrationTest | 12 | 0 |

Lint: 0 Error/Fatal issues. Final strengthened device-test APK build and lint PASS: `ux-regression-harness.log`.

Physical screenshot inspected: ux-regression-live-map.png. Updated filter label fits on the tested standard-font phone. Duplicate title/subtitle remains documented above. Google Maps and Routes live checks PASS. Temporary QA services removed; normal USB mapping restored.
