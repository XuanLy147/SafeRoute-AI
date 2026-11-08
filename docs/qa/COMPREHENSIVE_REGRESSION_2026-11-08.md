# Kiểm thử hồi quy toàn diện và sửa lỗi — 08/11/2026

Cập nhật tiếp theo: [4 lỗi bản nháp/ảnh đã sửa; 84 JVM tests và E2E điện thoại đạt](REPORT_DRAFT_REGRESSION_2026-11-08.md). Phần bên dưới giữ kết quả đợt kiểm thử trước.

## Kết luận

**Đã sửa 4 vấn đề mới, kiểm thử hồi quy đạt và cài bản sửa trên Xiaomi 2201117TG / Android 13.** Luồng xuyên suốt USER → REVIEWER → ADMIN chạy với HTTP, PostGIS, AI và Google thật trong môi trường thử nghiệm riêng. Dữ liệu vẫn đúng sau khi khởi động lại backend thử nghiệm.

Đây là kết quả trong phạm vi ma trận bên dưới, không phải cam kết “không còn bất kỳ lỗi nào” hay chứng nhận production-ready. Không commit/push; giữ nguyên các thay đổi local có trước. Repository SafeRoute-AI, nhánh local/main-latest-20261107, nền d3ad042.

Các phát hiện F01–F10 của đợt trước xem [báo cáo trước](DEVICE_ROLE_FIXES_2026-11-08.md). Báo cáo này chỉ liệt kê thay đổi và bằng chứng mới.

## Giới hạn bắt buộc khi sửa

1. Chỉ sửa lỗi có bằng chứng tái hiện; giữ sửa đổi production nhỏ, không đổi framework, dependency, model hay schema.
2. Giữ kiểm tra quyền server. Quyết định duyệt phải kiểm tra quyền hiện hành trong cùng transaction; không dựa vào JWT hoặc UI cũ.
3. Không tự gửi lại POST/PATCH khi chưa rõ kết quả. Mỗi báo cáo chỉ có một quyết định cuối và một audit cho quyết định đó.
4. Màn hình đã hủy không khởi chạy yêu cầu mới; phản hồi cũ không được cập nhật màn hình mới. Khi view được tạo lại phải có thể tải lại.
5. Database kiểm thử phải được đặt tên riêng, kiểm tra tên trước ghi/xóa; các thử nghiệm đồng thời không chạy trên dữ liệu thật.
6. Không đổi mật khẩu/quyền tài khoản thật, không đưa khóa/token vào Git hoặc báo cáo, không reset các thay đổi local.
7. Chỉ bàn giao khi regression, build/lint, contract và kịch bản thiết bị thực sự chạy đạt; case chưa chạy phải ghi rõ.
8. Dừng mở rộng sửa giao diện/tính năng khi không có bằng chứng lỗi mới. Chạy lại kiểm thử khi có thay đổi liên quan, không dùng build thành công thay cho kiểm thử nghiệp vụ.

Các giới hạn 2–4 được bảo vệ bằng kiểm thử tự động, không chỉ ghi trong tài liệu.

## Các lỗi mới và bằng chứng trước/sau

| ID | Mức độ | Tái hiện và nguyên nhân | Sửa và kiểm thử hồi quy |
|---|---|---|---|
| R01 | P2 | Mở danh sách tài khoản ADMIN, rời màn khi HTTP đang tải rồi tạo lại view. Cờ loading vẫn true dù request của view cũ đã hủy; load mới bị bỏ qua. | Reset trạng thái tải/tham chiếu view trong onDestroyView. RegressionSafetyTest.adminReloadsAfterLeavingDuringRequest: FAIL trước → PASS sau; yêu cầu mới trả danh sách được. |
| R02 | P3 | Chuyển sang lịch sử quyền, hủy/tạo lại view trên cùng Fragment. onViewCreated khởi tạo lại history từ arguments, làm quay về danh sách tài khoản. | Chỉ khởi tạo mode trong onCreate, vẫn khôi phục saved state. adminHistoryModeSurvivesViewRecreation: FAIL trước → PASS sau. |
| R03 | P2 | Gọi ApiCalls.enqueue với LifecycleOwner đã DESTROYED vẫn khởi chạy HTTP. Observer đăng ký quá muộn không nhận lại ON_DESTROY. Đây là lỗ hổng hợp đồng vòng đời được tái hiện bằng test, không phải một crash đã quan sát trên máy. | Hủy và trả về trước enqueue nếu owner đã hủy; đóng error body kể cả callback bị bỏ qua. destroyedOwnerNeverStartsAWrite: FAIL trước → PASS sau, call chưa thực thi. |
| R04 | P1 | Reviewer đã được xác thực chờ khóa report; ADMIN thu hồi quyền và commit; sau khi mở khóa, reviewer cũ vẫn ghi quyết định thành công (200). | Sau khi lấy khóa report, đọc lại actor với populate_existing và khóa chia sẻ hàng user đến commit. Reviewer đã bị hạ quyền nhận 403 + header refresh; report vẫn pending, không sinh ReviewLog. Test PostGIS đa transaction: FAIL trước → PASS sau. |

Bổ sung test hai quyết định duyệt đồng thời: đúng một thành công, một 409, đúng một audit và trạng thái khớp audit. Case này đã đạt trước sửa và vẫn đạt sau sửa.

Khóa theo thứ tự report → actor. Thao tác quản lý quyền chỉ khóa users, không chờ report; kiểm tra lại quyền và ghi quyết định được bảo vệ trong cùng transaction.

## Ma trận kiểm thử cuối

| Phạm vi | Cách kiểm tra | Kết quả |
|---|---|---|
| Auth, phiên, quyền cũ, logout, phản hồi muộn | JVM + HTTP/PostGIS | PASS |
| Hủy/tạo lại view ADMIN, giữ mode, chặn yêu cầu từ owner đã hủy | 3 regression JVM mới | PASS |
| Thu hồi quyền khi review đang chờ; hai quyết định đồng thời | 2 test với transaction/connection PostGIS độc lập | PASS |
| Reports, ảnh riêng, vote trùng, review/audit, danh sách và phân trang | Bộ test backend + Android | PASS |
| Routing, geometry, risk status, dữ liệu sai, AI API và runtime config | Các suite hiện có được chạy lại | PASS |
| Android toàn bộ JVM | 80 tests, 0 failures, 0 errors | PASS |
| Backend toàn bộ | 208 tests + 12 subtests; 1 deprecation warning từ dependency | PASS |
| Build app APK/test APK, Android lint | Bản production sửa + test harness cuối | PASS; 0 lỗi lint |
| Backend Ruff, migration upgrade/downgrade 0002/upgrade | Database dùng một lần | PASS |
| OpenAPI và JSON ví dụ | verify_contract.py với source cuối | PASS |
| Luồng thiết bị với API thật và ba vai trò | Một kịch bản E2E tổng hợp trên Xiaomi | PASS |
| Dữ liệu sau restart backend thử nghiệm | Đọc lại report verified, vote và audit | PASS |
| Google Maps SDK, Google Routes | Provider thật trên điện thoại | PASS |
| Camera/photo picker qua thao tác hệ thống | Không chạy trọn luồng trong đợt này | NOT_VERIFIED |
| Production HTTPS, nhiều thiết bị/OEM, tải lớn, pentest độc lập | Ngoài môi trường kiểm thử này | NOT_RUN |

80 JVM tests gồm: ApiContractTest 18, AuthDtoTest 2, AuthFlowTest 5, HtmlFlowTest 6, RegressionSafetyTest 3, RouteAndHazardTest 16, MapRoutingUiTest 22, AccountReportIntegrationTest 8.

Không dùng các lượt đầu thất bại để đếm PASS. Lượt Android toàn bộ đã chạy sau sửa production; lượt kiểm tra cuối sau bổ sung harness thiết bị dùng kết quả Gradle up-to-date cho phần không đổi và kiểm tra lại phần test thiết bị/lint thay đổi.


Xác minh AI bổ sung: dùng ảnh xám tổng hợp từ kịch bản thiết bị gọi trực tiếp backend local sau cleanup, nhận HTTP 200, model_version SafeRoute-YOLOv12s-v1.1, detections = []; /health và /ready đều 200. Bằng chứng: audit2-ai-live.json. Kịch bản UI cho phép nhập nhãn thủ công khi AI lỗi, nên kết quả AI này được ghi riêng, không suy từ việc UI tiếp tục được.

## Kịch bản trên điện thoại

Môi trường Docker riêng gồm PostGIS, FastAPI/YOLO và volume ảnh; không nối DB đang chứa tài khoản thật của người dùng. Reviewer và ADMIN fixture được đăng ký qua HTTP, gán quyền chỉ trong DB thử nghiệm. USER được đăng ký qua giao diện; REVIEWER và ADMIN đăng nhập mật khẩu qua giao diện. Không dùng token giả để bỏ qua đăng nhập trong kịch bản này.

1. Đăng ký USER; xác nhận /auth/me trả USER.
2. Đưa ảnh xám/tọa độ tổng hợp vào bản nháp bằng test harness; gọi AI thật, chọn nhãn thủ công, xác nhận và gửi ảnh/báo cáo qua API thật.
3. Kiểm tra report pending, có ảnh; vote và thấy số đếm 1; tạo lại Activity, báo cáo vẫn còn.
4. Đăng xuất, đăng nhập REVIEWER; thấy report trong queue; nhập lý do và xác minh.
5. Kiểm tra verified, một audit, không còn trong queue; gửi lại quyết định nhận 409.
6. Bản đồ nhận hazard verified và Google Maps render; Google Routes trả tuyến thật, có geometry, không giả score khi policy chưa được duyệt.
7. Đăng xuất, đăng nhập ADMIN; mở toàn bộ reports, lịch sử duyệt, đường dẫn lịch sử quyền, metrics, cấu hình chỉ đọc, danh sách tài khoản.
8. Tạo lại Activity ở danh sách tài khoản; dữ liệu tải lại được. Đăng xuất.
9. Restart backend thử nghiệm; verified/vote/audit vẫn được giữ.

Không coi việc đưa ảnh vào draft là kiểm thử camera/photo picker. Các điều khiển app được thao tác qua instrumentation, không phải kiểm thử mọi gesture/bàn phím hệ thống.

Lượt đầu dùng ActivityScenario bị kẹt trước khi gửi request nghiệp vụ trên Xiaomi. Đã chủ động force-stop để dọn; thông báo “Process crashed” của lượt bị dừng không được coi là app tự crash. Harness mới có cờ physicalDevice, dùng foreground launch và quan sát lifecycle; đường emulator chuẩn vẫn giữ nguyên. Lượt chạy lại đạt OK (1 test) và xác minh restart thành công. Sửa thêm assertion E2E cũ còn đòi enum UNAVAILABLE trên UI, nay kiểm tra giải thích không rỗng/không lộ enum và riskScore vẫn null.

## Bằng chứng và lệnh

Log local trong frontend/build/reports/qa-roles/ (Git bỏ qua):
- audit2-before-android.log: 3 regression mới thất bại trước sửa.
- audit2-before-backend.log: race thu hồi quyền thất bại trước sửa; competing decisions đã đạt.
- audit2-android.log, audit2-final-android.log: 80 JVM tests và build/lint.
- audit2-backend.log: 208 + 12, Ruff, migration và cleanup.
- audit2-contract.log, audit2-deploy.log.
- audit2-device.log: lượt ActivityScenario bị dừng, không PASS.
- audit2-device-retry.log: E2E thật, Google Maps/Routes và restart PASS.
- audit2_backend_runner.py: chạy bộ backend trên DB saferoute_test_audit2_* rồi xóa đúng DB được tạo.
- audit2_device_runner.py: adapter local của scripts/qa/verify_android_e2e.py, dùng image mới, adb reverse và cờ physicalDevice/adminE2E.

Bộ chuẩn: frontend/gradlew.bat -p frontend :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug; scripts/qa/verify_postgis.py --backend backend --runxfail; scripts/qa/verify_contract.py. Các lệnh cần môi trường SDK/JDK/PostGIS theo runbook; runner local trên đã dùng Docker image hiện có và không cần cài thêm dependency vào dự án.

## Phần còn giới hạn

Cấu hình ADMIN vẫn chỉ đọc. Nghi trùng là gợi ý khoảng cách/thời gian; không tự gộp. OAuth, email reset/verification, MFA, thu hồi JWT từ xa và tải lớn chưa được bổ sung trong đợt sửa này. Không suy diễn rằng bộ test hiện có bao phủ mọi tương tác có thể xảy ra.

Backend local đã cập nhật bản sửa; APK đã cài và chạy test. Các container/network/volume/DB thử nghiệm được dọn, adb reverse được trả về backend local cổng 8000, phiên QA đã đăng xuất. Không thay đổi mật khẩu/quyền hay gửi báo cáo thử vào DB thật. Chưa commit/push.
