# Tiếp tục sửa lỗi bản nháp và ảnh — 08/11/2026

## Kết quả

Đã sửa thêm 4 lỗi trong ReportWizardFragment, có kiểm thử tái hiện thất bại trước sửa và đạt sau sửa. 84 JVM tests PASS, build app/test APK PASS, lint 0 lỗi. Bản sửa đã cài và kiểm thử E2E trên Xiaomi 2201117TG / Android 13 với môi trường Docker riêng.

Đợt này tiếp nối [kiểm thử toàn diện](COMPREHENSIVE_REGRESSION_2026-11-08.md). Repository SafeRoute-AI, nhánh local/main-latest-20261107; giữ các thay đổi local có trước, chưa commit/push.

## Phát hiện và cách sửa

| ID | Mức độ | Tái hiện trước sửa | Hành vi sau sửa |
|---|---|---|---|
| R05 | P2 | Bản nháp có mô tả, loại nguy cơ và mức độ; chọn “Bỏ bản nháp để tạo báo cáo mới”. Draft đã trống nhưng form vẫn hiện mô tả/lựa chọn cũ. Mức độ hiển thị có thể khác giá trị sẽ gửi. | Khi render, đồng bộ mô tả, loại và mức độ từ draft; sau bỏ nháp, mô tả trống, chưa chọn loại, mức độ về mặc định. Tóm tắt cũ cũng được xóa. |
| R06 | P2 | Bỏ nháp rồi chọn lại cùng URI ảnh. Biến đánh dấu preview còn URI cũ nên bỏ qua tải lại; màn vẫn hiện placeholder thay vì ảnh đã chọn. | Xóa dấu preview khi draft không có ảnh; chọn lại cùng URI sẽ tải preview và đồng bộ ảnh ở bước AI/xác nhận. |
| R07 | P2 | Đã có kết quả AI, thử phân tích lại nhưng URI/MIME không hợp lệ. Kết quả trong draft được xóa sau khi render, còn ô sửa nhãn/khung cũ và model version từ lần trước. | Xóa detections, model version và xác nhận trước khi hiển thị lần phân tích mới; cập nhật overlay theo draft kể cả khi không tải lại bitmap. |
| R08 | P2 | Đang xem ảnh hợp lệ, thay bằng URI không đọc được. Callback xóa URI trong draft nhưng không cập nhật màn hình; ảnh cũ vẫn hiện. | Render lại trạng thái không có ảnh, đồng bộ placeholder ở các bước và yêu cầu xác nhận lại. |

## Giới hạn bảo vệ

- Chỉ một file production thay đổi: frontend/app/src/main/java/com/comeforfood/saferoute/feature/report/ReportWizardFragment.java.
- Đồng bộ form chỉ khi giá trị hiển thị khác draft, tránh làm mất xác nhận khi chỉ vẽ lại cùng dữ liệu.
- Giữ cơ chế uncertain: lần gửi chưa rõ kết quả không tự được phép gửi lại. Không thay đổi quy tắc retry, phân quyền, API, database hoặc dependency.
- Không gọi API ghi từ các regression JVM. E2E dùng tài khoản, ảnh và database thử nghiệm riêng; không đổi tài khoản thật.
- Không mở rộng thành thay đổi giao diện hoặc tính năng chưa có yêu cầu.

## Bằng chứng

Bốn regression trong AccountReportIntegrationTest:
- discardingDraftResetsVisibleInputsAndDefaultSeverity
- selectingSamePhotoAfterDiscardLoadsPreviewAgain
- failedReanalysisClearsOldAiEditorsModelAndConsent
- unreadableReplacementPhotoRemovesPreviousPreview

Cả bốn đều FAIL trước sửa và PASS sau sửa. Các bài cũ về khôi phục draft/xác nhận, auth, quyền, network, routing và lifecycle vẫn đạt: tổng cộng 84 tests, 0 failures, 0 errors.

Log trong frontend/build/reports/qa-roles/ (Git bỏ qua):
- audit3-before.log: ba lỗi đầu.
- audit3-image-before.log: ảnh thay thế không đọc được.
- audit3-android.log: toàn bộ JVM/build/lint.
- audit3-summary.json: kết quả từng regression và SHA-256 source đã kiểm thử.
- audit3-device.log: E2E trên điện thoại, Google Maps/Routes và persistence sau restart.

Lệnh Android: frontend/gradlew.bat -p frontend :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug, với SDK/JDK và thư viện Robolectric local như runbook.

E2E dùng lại audit2_device_runner.py đã xác minh ở đợt trước. Kịch bản qua đăng ký USER → ảnh tổng hợp/AI/manual → upload/report/vote → đăng nhập REVIEWER/duyệt/map → tuyến Google thật → đăng nhập ADMIN/các màn đọc/tạo lại Activity/logout. Sau restart, report verified và audit/vote còn đúng. OK (1 test) là một kịch bản tổng hợp, không phải một thao tác đơn lẻ.

Backend không sửa trong đợt này nên không chạy lại bộ 208 tests + 12 subtests; kết quả đó thuộc đợt trước, không tính là bằng chứng mới. HTTP/backend thật vẫn được kiểm tra xuyên suốt qua E2E trên môi trường riêng.

## Bàn giao và giới hạn còn lại

APK mới đã cài. Container, network, volume thử nghiệm đã được dọn; kết nối USB trả về backend local cổng 8000; phiên thử nghiệm đã đăng xuất. Chưa commit/push.

Bốn lỗi mới được tái hiện bằng JVM UI test; lượt điện thoại xác minh hồi quy luồng tổng thể. Không tuyên bố đã thao tác cả bốn tình huống lỗi mới bằng chạm hệ thống trên điện thoại. Camera/photo picker end-to-end, ảnh EXIF nhiều hướng, nhiều loại thiết bị và production HTTPS chưa được xác minh trong đợt này. Không cam kết toàn bộ app không còn lỗi.
