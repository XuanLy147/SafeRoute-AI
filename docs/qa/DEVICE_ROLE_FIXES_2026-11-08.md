# Sửa lỗi sau kiểm thử điện thoại — 08/11/2026

Xem thêm [kiểm thử hồi quy tiếp theo](COMPREHENSIVE_REGRESSION_2026-11-08.md).

## Kết quả và phạm vi

Đã sửa các lỗi F01–F10 của [báo cáo gốc](DEVICE_ROLE_AUDIT_2026-11-08.md), bổ sung các màn đọc dữ liệu còn thiếu và kiểm thử lại trên Xiaomi 2201117TG / Android 13 với backend local thật. Riêng cấu hình quản trị hiện chỉ đọc; các giới hạn bên dưới vẫn áp dụng.

Repository: SafeRoute-AI; branch local/main-latest-20261107, nền d3ad042 và các thay đổi local được giữ nguyên. Chưa commit/push. APK cuối đã cài lên điện thoại; backend Docker local đã cập nhật, không triển khai cloud.

## Đối chiếu lỗi

| ID | Thay đổi | Bằng chứng kiểm thử lại |
|---|---|---|
| F01 | GET/HEAD phục hồi một lần khi socket/EOF bị đóng; loại bỏ socket nhàn rỗi trước thử lại. Không tự gửi lại POST/PATCH. | Tải đồng thời chi tiết/ảnh/vote rồi chờ 8 giây và tải lại hai lượt thành công; màn nghi trùng tải ngay lần đầu. JVM kiểm tra retry đọc và không lặp ghi. |
| F02 | Chỉ xác minh lại quyền với 403 có header X-SafeRoute-Role-Refresh: required. 403 ảnh riêng giữ màn chi tiết, hiện thông báo gọn. | USER xem báo cáo có ảnh không thuộc mình vẫn ở chi tiết, giữ phiên và nút bình chọn. Phiên mang vai trò cũ vẫn được đồng bộ về USER. Quyền ảnh server không nới lỏng. |
| F03 | Lịch sử xét duyệt lấy dữ liệu thật, phân trang, hiện người xử lý/lý do/thời gian. Nhật ký ADMIN có lịch sử duyệt và đường dẫn trực tiếp đến lịch sử quyền. | Xác minh và từ chối báo cáo QA xuất hiện trong lịch sử; đường dẫn lịch sử quyền mở đúng màn trên điện thoại. |
| F04 | Nghi trùng truy vấn PostGIS: cùng loại, cách tối đa 30 m, trong 7 ngày, không rejected và có ít nhất một pending. | Cặp QA cách 8,8 m xuất hiện; backend loại các cặp xa/cũ/khác loại/rejected. Đây là gợi ý, không tự gộp báo cáo. |
| F05 | API ADMIN liệt kê mọi trạng thái, tìm ID/nội dung, lọc và phân trang. API bản đồ công khai giữ quy tắc loại rejected. | Điện thoại thấy pending/verified/rejected; lọc rejected trả đúng một báo cáo QA. |
| F06 | Chỉ số lấy số đếm thực; hiển thị phiên bản/trạng thái AI, risk policy và giới hạn ảnh. Cấu hình ghi rõ chỉ đọc. | Đối chiếu số đếm với DB và mở màn cấu hình trên máy thật. Chưa có chỉnh cấu hình AI/risk trong app; không đưa số benchmark giả. |
| F07 | Đọc vote của chính tài khoản trước khi cho bình chọn; đã vote thì hiện lựa chọn. 409 có thông báo rõ; kết quả ghi chưa chắc chắn được đối chiếu lại. Lỗi review giữ lý do và đọc trạng thái mới. | Vote QA chỉ tăng một lần, tải lại vẫn hiện lựa chọn và không mời vote lại. Backend kiểm tra 409 và dữ liệu vote riêng từng người. |
| F08 | Bỏ tiền tố “Chưa kết nối đăng nhập” khỏi thông báo đăng nhập. | Màn đăng nhập cuối không còn câu sai trạng thái. |
| F09 | Giải thích routing bằng tiếng Việt, phân biệt chưa đánh giá nguy cơ với đề xuất theo thời gian; cảnh báo chung không mâu thuẫn. | Google trả ba tuyến thật; màn kết quả nói rõ chưa có đánh giá nguy cơ và đề xuất theo ETA. |
| F10 | Backend và Android cùng làm tròn ETA lên phút. | Regression 361 giây → 7 phút; màn thật có lý do và thẻ tuyến cùng 7 phút. |

## Kiểm tra tự động và bản cài

- Android: 77 JVM tests PASS; assembleDebug, assembleDebugAndroidTest và lintDebug PASS, 0 lỗi lint.
- Backend: 206 tests + 12 subtests PASS trên PostGIS riêng; Ruff PASS. Một cảnh báo deprecation từ dependency.
- Migration: upgrade head → downgrade 0002 → upgrade head PASS trên database test, đã xóa database này.
- OpenAPI sinh lại từ implementation; verify_contract.py PASS cho schema và JSON mẫu.
- Đã kiểm tra giao diện cuối: ảnh không khả dụng không chiếm khối trống lớn; lịch sử ngắn ẩn phân trang thừa; liên kết nhật ký quyền mở trực tiếp.
- Client và backend cần phát hành cùng nhau vì có các API đọc mới và header phân biệt lỗi quyền.

## API bổ sung

- GET /api/v1/reports/{id}/votes/mine — tài khoản đã đăng nhập, chỉ vote của mình.
- GET /api/v1/review/history — REVIEWER/ADMIN.
- GET /api/v1/review/duplicates — REVIEWER/ADMIN.
- GET /api/v1/admin/reports — ADMIN.
- GET /api/v1/admin/overview — ADMIN.

Các danh sách hỗ trợ offset/limit; reports hỗ trợ status và q, duplicates hỗ trợ q. Nhật ký hệ thống ở đây gồm quyết định duyệt và nhật ký đổi quyền, chưa phải kho log mọi sự kiện hệ thống.

## Bằng chứng local

Trong android/build/reports/qa-roles/ (Git bỏ qua):
- fix-android.log, fix-backend.log, fix-contract.log, fix-deploy.log.
- fix-multi-read-idle.json, fix-final-detail.json/png, fix-vote.json.
- fix-history.json, fix-duplicates.json/png, fix-admin-all.json, fix-admin-filter.json/png.
- fix-admin-metrics.json/png, fix-admin-settings.json, fix-admin-audit.json.
- fix-stale-role.json, fix-route.json, fix-final-ui.json, fix-final-history.png.
- fix-cleanup.json xác nhận xóa đúng ba báo cáo tổng hợp của lượt sửa; dữ liệu gốc và vai trò không đổi.

Không đưa token, mật khẩu, ảnh riêng hoặc thông tin tài khoản thật vào tài liệu Git.

## Giới hạn và bàn giao

- Kiểm thử trên điện thoại bằng Android instrumentation thao tác View với API thật; không tương đương thao tác chạm/gesture hệ thống. Camera/photo picker end-to-end chưa xác minh do hạn chế INJECT_EVENTS của Xiaomi.
- Dùng phiên QA ngắn hạn theo vai trò DB để kiểm tra nghiệp vụ; không tuyên bố đã đăng nhập bằng mật khẩu của cả ba tài khoản thật.
- F01 đã xác minh qua USB/adb reverse và Docker local; chưa kiểm thử production HTTPS.
- Màn cấu hình chỉ đọc; chưa chỉnh model/risk policy trong app, chưa có số đo accuracy/latency/cost mới.
- Các giới hạn tài khoản trong docs/integration/auth-rbac-flow.md (khôi phục mật khẩu, OAuth, thu hồi JWT từ xa, v.v.) vẫn tồn tại.
- Đã đăng xuất phiên QA, dừng bridge, gỡ chuyển tiếp cổng QA và khôi phục thiết lập giữ sáng về giá trị cũ. Giữ adb reverse 8000 để app tiếp tục dùng backend local.
- Báo cáo gốc được giữ làm lịch sử phát hiện. Kết quả này cập nhật trạng thái các lỗi đó, không chứng nhận toàn bộ app production-ready.
