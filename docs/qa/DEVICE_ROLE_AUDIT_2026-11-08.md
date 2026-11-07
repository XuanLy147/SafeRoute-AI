# Kiểm thử nghiệp vụ trên điện thoại — 08/11/2026

## Cập nhật sau sửa — 08/11/2026

Đã triển khai sửa F01–F10 và kiểm thử lại trên điện thoại; xem [kết quả sửa và giới hạn](DEVICE_ROLE_FIXES_2026-11-08.md). Android 77 JVM tests, backend 206 tests + 12 subtests, build/lint/migration/OpenAPI đều PASS. APK và backend local đã cập nhật; chưa commit/push. F06 mới hỗ trợ cấu hình chỉ đọc; camera/photo picker end-to-end vẫn chưa xác minh.

**Phần bên dưới là kết quả trước khi sửa**, giữ nguyên để đối chiếu; không phải trạng thái hiện tại.

## Kết luận

**Chưa đạt để coi ba luồng USER / REVIEWER / ADMIN là hoàn thiện.**
Tái hiện 10 vấn đề bên dưới, trong đó hai lỗi P1 cản trở việc đọc dữ liệu và xem báo cáo cộng đồng.
Các thao tác chính tạo báo cáo, vote lần đầu, xác minh/từ chối và cấp/thu hồi quyền đã chạy được với backend thật.
27/27 kiểm tra HTTP bổ sung về phân quyền và trạng thái dữ liệu đạt. Kết quả này không thay thế các lỗi frontend đã quan sát.

Đợt này là kiểm thử và ghi nhận; **chưa sửa logic production, chưa commit/push**.
Giữ nguyên các thay đổi local có từ trước.

## Môi trường và phương pháp

- Repository SafeRoute-AI, branch local/main-latest-20261107, HEAD d3ad042 kèm thay đổi local chưa commit.
- Xiaomi 2201117TG, Android 13; app versionName 0.1-tv1, versionCode 1; APK cập nhật 08/11/2026 00:33:54 giờ máy.
- FastAPI + PostGIS + AI chạy Docker local; app truy cập qua adb reverse 8000. Google Maps và Routes thật.
- Kiểm thử trực tiếp View và điều hướng của app trên điện thoại bằng Android instrumentation; không mock HTTP.
- Xiaomi vẫn từ chối adb input với INJECT_EVENTS dù người dùng đã bật quyền. Vì vậy performClick/setText/list item click được thực hiện trong tiến trình test của app. Đây không phải kiểm thử thao tác chạm, bàn phím hoặc gesture hệ thống.
- Ba tài khoản gốc được kiểm thử nghiệp vụ bằng phiên QA ký cục bộ, thời hạn 30 phút, sử dụng vai trò hiện hành trong DB. Không đổi mật khẩu của chúng. Không tuyên bố đã kiểm thử đăng nhập mật khẩu đúng cho cả ba tài khoản gốc.
- Đăng ký và đăng nhập mật khẩu đúng được thực hiện qua UI với một tài khoản QA riêng; mật khẩu sai được thử qua UI với tài khoản USER.
- Ảnh xám 640×480 và tọa độ tổng hợp được đưa vào ReportDraft bằng test harness. Các bước AI, xác nhận, upload, tạo report, vote, review sau đó đều gọi API thật. Camera/photo picker end-to-end chưa được kiểm thử.
- Một số case nghiệp vụ được chạy sau probe GET để tránh lỗi F01 và kiểm tra độc lập logic phía sau. Không tính những case này là bằng chứng app không có lỗi kết nối.
- Timestamps backend là UTC ngày 07/11; thời gian địa phương của phiên QA là rạng sáng 08/11.

## Danh sách lỗi và tính năng chưa hoàn thiện

### F01 — P1 — GET đầu tiên sau thời gian chờ bị lỗi kết nối

**Tái hiện:** tải một danh sách thành công, chờ khoảng 8 giây rồi tải lại/mở danh sách khác.
**Thực tế:** app báo “Không nhận được phản hồi…”; thử lại ngay thì thành công. Gặp ở bản đồ, chi tiết báo cáo, hàng đợi và danh sách ADMIN.
Probe sử dụng chính Retrofit service của app cho kết quả:
200 → chờ 1 giây → 200 → chờ 8 giây → IOException → gọi lại → 200.
Lặp lại chờ 8 giây cho cùng kết quả lỗi/200.

Exception: java.io.IOException: unexpected end of stream on http://127.0.0.1:8000/...
Cause: java.io.EOFException: newline not found, limit=0.

**Kỳ vọng:** GET có thể phục hồi kết nối đóng mà không buộc người dùng bấm lại.
**Phân tích:** phù hợp với tái sử dụng kết nối keep-alive đã bị đóng trên đường adb reverse/backend; client đang tắt retryOnConnectionFailure cho mọi request. Chưa chứng minh lỗi tương tự trên mạng production HTTPS.
**Hướng sửa:** phục hồi giới hạn cho request đọc an toàn hoặc điều chỉnh quản lý pool; không bật retry POST/PATCH mù quáng.
**Owner:** TV2/TV5.
**Code:** android/.../core/network/ApiClient.java:25, ApiCalls.java.
**Evidence:** network-probe.json, admin-users-first.json, reviewer-initial.json, user-other-report.json.

### F02 — P1 — Xem ảnh không được phép làm người dùng bị đưa khỏi chi tiết báo cáo

**Tái hiện:** USER mở chi tiết báo cáo có ảnh do tài khoản khác tạo; nếu gặp F01 thì bấm Tải lại.
**Thực tế:** GET report trả 200; GET ảnh trả 403 đúng chính sách; tiếp theo /auth/me trả 200 nhưng app quay về bản đồ. Người dùng không giữ được màn chi tiết để đọc/bình chọn.
**Kỳ vọng:** giữ chi tiết công khai, hiển thị ảnh không khả dụng, vẫn cho thao tác được phép.
**Nguyên nhân xác nhận từ code:** ApiClient vô hiệu hóa quyền với mọi 403, kể cả từ tài nguyên ảnh; MainActivity phản ứng bằng xác minh lại phiên và điều hướng home.
**Hướng sửa:** phân biệt từ chối truy cập một tài nguyên với thay đổi vai trò; không nới quyền ảnh backend để chữa triệu chứng. Nếu refresh /me cho cùng vai trò thì không phá màn đang xem.
**Owner:** TV1/TV2.
**Code:** ApiClient.java:38, PrivateImage.java, ReportDetailFragment.java, MainActivity.java.
**Evidence:** user-other-report.json, user-private-image-403.json, relevant-api.log.

### F03 — P2 — Lịch sử xét duyệt / Nhật ký hệ thống chưa nối dịch vụ

**Tái hiện:** REVIEWER → Lịch sử xét duyệt; ADMIN → Nhật ký hệ thống.
**Thực tế:** thông báo chưa kết nối dịch vụ, Tải lại chỉ hiện snackbar. Sau một xác minh và một từ chối thật vẫn không có màn lịch sử tổng hợp hoạt động.
**Kỳ vọng:** xem được các quyết định đã thực hiện, người thực hiện, lý do và thời gian.
**Phân biệt:** audit trong chi tiết report và lịch sử đổi quyền ADMIN có hoạt động; lỗi này là hai màn lịch sử tổng hợp.
**Owner:** TV2/TV3.
**Code:** FlowPageFragment.java:73,115.
**Evidence:** reviewer-initial.json, review-queue-admin-all.json.
**Loại:** tính năng chưa triển khai, không phải backend đang mất mạng.

### F04 — P2 — Báo cáo nghi trùng có bộ lọc nhưng không thực hiện nghiệp vụ

**Tái hiện:** REVIEWER → Báo cáo nghi trùng → nhập tìm kiếm/chọn bộ lọc/Tải lại.
**Thực tế:** chỉ có UI khung, không truy vấn hay trả kết quả nghi trùng.
**Kỳ vọng:** có kết quả thật hoặc trạng thái tính năng chưa hỗ trợ rõ ràng, không trình bày các điều khiển như đã hoạt động.
**Owner:** TV2/TV3.
**Code:** FlowPageFragment.java, nhánh DUPLICATES.
**Evidence:** reviewer-initial.json.
**Loại:** thiếu tính năng.

### F05 — P2 — ADMIN “Toàn bộ báo cáo” chỉ hiển thị báo cáo chờ duyệt

**Tái hiện:** xác minh report QA #2; ADMIN mở Toàn bộ báo cáo.
**Thực tế:** chỉ hiện report gốc #1 còn pending; report QA đã xác minh không có trong danh sách. Body ghi “Báo cáo chờ duyệt: 1”.
**Kỳ vọng:** màn toàn bộ báo cáo phải có các trạng thái và bộ lọc phù hợp, hoặc đổi tên thành Hàng đợi nếu chỉ hỗ trợ pending.
**Nguyên nhân:** ADMIN_REPORTS dùng chung loadQueue()/review queue với REVIEWER.
**Owner:** TV2/TV3.
**Code:** FlowPageFragment.java:63,103.
**Evidence:** admin-all-after-verified.json.

### F06 — P2 — Chỉ số & mô hình / Cấu hình quản trị chưa có chức năng

**Tái hiện:** ADMIN mở hai mục này.
**Thực tế:** chỉ báo “Chức năng này chưa sẵn sàng…”, không có số liệu hay thao tác.
**Kỳ vọng:** triển khai theo phạm vi đã chốt hoặc ghi rõ chưa hỗ trợ trong tiêu chí bàn giao.
**Owner:** TV2/TV3/TV4/TV5.
**Code:** FlowPageFragment.java:93.
**Evidence:** admin-placeholders.json.
**Loại:** thiếu tính năng; không tính là lỗi RBAC.

### F07 — P2 — Sau khi vote thành công, UI vẫn mời vote lại và báo lỗi chung

**Tái hiện:** chủ report QA vote Đồng tình → số tăng lên 1 → bấm Đồng tình lần nữa.
**Thực tế:** server trả 409 đúng luật một vote, nhưng app xóa toàn bộ nội dung/actions, chỉ còn “Dữ liệu đã tồn tại hoặc đã thay đổi…” và nút tải lại. Không nói người dùng đã vote.
**Kỳ vọng:** giữ chi tiết, thể hiện lựa chọn đã gửi hoặc thông báo rõ đã bình chọn; nếu sản phẩm cho đổi vote thì cần contract riêng.
**Owner:** TV2/TV3.
**Code:** ReportDetailFragment.java:48,56,65, ApiCalls.message(409).
**Evidence:** qa-vote.json, http-role-matrix.json.
**Phân biệt:** DB không tăng vote lặp; lỗi nằm ở trải nghiệm và thông báo.

### F08 — P3 — Màn đăng nhập thông báo sai “Chưa kết nối đăng nhập”

**Tái hiện:** mở Login trong lúc auth backend vẫn hoạt động.
**Thực tế:** ghi chưa kết nối đăng nhập dù đăng ký/đăng nhập thật thành công.
**Kỳ vọng:** mô tả quyền khách, không khẳng định dịch vụ chưa được tích hợp.
**Owner:** TV2.
**Evidence:** login-wrong.json, qa-real-password-login.json, guest-gates.json.

### F09 — P2 — Giải thích tuyến khi risk unavailable tự mâu thuẫn

**Tái hiện:** tìm A “Cho Ben Thanh, Ho Chi Minh”, B “Dai hoc Sai Gon, 273 An Duong Vuong, Ho Chi Minh”; chọn kết quả rồi xem tuyến.
**Thực tế:** trả 3 tuyến thật, không có điểm risk. Cùng đoạn vừa nói đề xuất theo thời gian, risk chưa khả dụng, vừa khẳng định đề xuất dựa trên nguy cơ đã ghi nhận. Còn hiện enum UNAVAILABLE · DURATION_ETA_ONLY và dấu phân cách thừa.
**Kỳ vọng:** nêu rõ chỉ so sánh ETA trong trường hợp này, không ngụ ý đã đánh giá nguy cơ.
**Owner:** TV1/TV5.
**Code:** backend/app/modules/routing/service.py:28; schemas.py:115; Android RouteResultFragment.
**Evidence:** route-result.json, route-result.png.
**Phân biệt:** risk policy chưa khả dụng là cấu hình đã biết, không phải bản thân lỗi này. Lỗi là lời giải thích không nhất quán.

### F10 — P3 — Số phút trong giải thích tuyến khác thẻ tuyến

**Tái hiện:** cùng kết quả F09.
**Thực tế:** lời giải thích tuyến được đề xuất ghi 6 phút, thẻ cùng tuyến ghi 7 phút.
**Nguyên nhân:** backend dùng chia nguyên duration_seconds // 60, Android dùng Math.ceil(durationSeconds / 60.0).
**Kỳ vọng:** thống nhất quy tắc làm tròn/hiển thị.
**Owner:** TV1/TV5.
**Code:** backend/app/modules/routing/service.py:338; Android RouteResultFragment.java:94.
**Evidence:** route-result.json.

## Ma trận kiểm thử đã thực hiện

PASS dưới đây là kết quả nghiệp vụ trong điều kiện nêu ở phần phương pháp; các lỗi F01/F02 vẫn mở.

| Nhóm | Case | Kết quả / evidence |
|---|---|---|
| Auth | Sai mật khẩu không tạo phiên | PASS — login-wrong.json |
| Auth | Xác nhận mật khẩu không khớp | PASS — register-result.json |
| Auth | Đăng ký hợp lệ, gửi lặp không sinh hai tài khoản | PASS — register-result.json; DB xác nhận chỉ một email QA |
| Auth | Đăng nhập mật khẩu đúng tài khoản QA | PASS — qa-real-password-login.json |
| Auth | Đăng ký email đã tồn tại | PASS — register-duplicate.json, thông báo cụ thể |
| Guest | Bản đồ truy cập được; Report/Mine yêu cầu login | PASS — guest-gates.json |
| Auth | Login xong trở về Mine đang yêu cầu | PASS — login-resume-mine.json |
| Auth | Logout rồi Back không khôi phục phiên riêng tư | PASS — logout-guest.json; Activity kết thúc, mở lại vẫn chưa đăng nhập |
| Auth | Token QA hết hạn | PASS — expired-session.json, về Login |
| RBAC | Phiên QA mang role REVIEWER cũ, DB đã là USER | PASS — stale-role-session.json, trả về UI USER |
| Network | Ngắt reverse 8000 rồi khôi phục, không mất phiên | PASS — offline-recovery.json |
| Network | GET sau idle | FAIL — F01 |
| Report | AI ảnh xám trả no detection, có nhập thủ công | PASS với input tổng hợp — qa-ai-result.json |
| Report | Chưa chọn loại không đi tiếp; chưa xác nhận không gửi | PASS — kiểm tra UI và DB trước submit |
| Report | Upload + create hai report thật ở PENDING | PASS — qa-submit.json, qa-second-submit.json |
| Report | Mine chỉ hiện hai report QA của tài khoản QA | PASS — login-resume-mine.json, HTTP ownership |
| Report | USER không thấy nút Xác minh/Từ chối | PASS — qa-own-report.json |
| Community | Xem report của người khác có ảnh riêng | FAIL — F02 |
| Community | Vote lần đầu tăng đúng một phiếu, không đổi status | PASS — qa-vote.json; DB trước cleanup |
| Community | Vote lặp không tăng DB, nhưng UI lỗi chung | Backend PASS / UX FAIL — F07 |
| Review | Thiếu lý do bị chặn inline | PASS — review-first-write.json |
| Review | VERIFY report QA #2, audit đúng | PASS — review-first-write.json, cleanup snapshot |
| Review | REJECT report QA #3, audit đúng | PASS — qa-reject-map.json, cleanup snapshot |
| Map | Verified có nhãn đúng, pending chưa xác minh, rejected không xuất hiện | PASS — qa-reject-map.json |
| Review | Không duyệt lại report terminal | PASS HTTP 409 — http-role-matrix.json |
| Review | Lịch sử và nghi trùng | FAIL — F03/F04 |
| Admin | Chặn tự đổi quyền | PASS — admin-validation.json |
| Admin | Lý do trống và chọn nguyên role bị chặn | PASS — admin-grant.json |
| Admin | Cấp QA USER → REVIEWER qua UI | PASS — admin-grant.json |
| Admin | Thu hồi QA REVIEWER → USER qua UI | PASS — admin-revoke-history.json |
| Admin | Lịch sử có đúng actor, target, old/new role và reason | PASS — admin-revoke-history.json |
| Admin | Tìm tài khoản / không có kết quả | PASS — admin-search.json |
| Admin | Toàn bộ báo cáo / nhật ký / metrics / settings | FAIL — F03/F05/F06 |
| Routing | Geocode tên địa điểm, chọn A/B, 3 tuyến Google, polyline | PASS — route-address.json, route-result.json/png |
| Routing | Giải thích risk và ETA | FAIL — F09/F10 |
| Auth copy | Nội dung mô tả trạng thái dịch vụ | FAIL — F08 |

### Kiểm tra HTTP bổ sung — 27/27 PASS

Ma trận GET admin/users, admin/role-audit, review/queue cho khách/USER/REVIEWER/ADMIN;
PATCH đổi role bị từ chối với khách/USER/REVIEWER; review bị chặn với khách/USER;
review terminal 409; vote lặp 409; guest vote 401;
ảnh riêng: guest 401, USER không sở hữu 403, owner/REVIEWER/ADMIN 200;
public list loại rejected và giữ verified; mine đúng owner.

Không coi đây là kiểm thử nút UI. Chi tiết HTTP codes lưu trong http-role-matrix.json.

## Chưa kiểm thử / giới hạn

- Camera và photo picker thực sự chọn ảnh, runtime permission dialogs, thao tác chạm/gesture: BLOCKED bởi INJECT_EVENTS của thiết bị.
- Xoay màn hình, process death, background lâu, mất mạng đúng lúc POST đã được server commit: NOT_RUN trong đợt này.
- Phân trang trên 30 tài khoản, tải lớn, nhiều reviewer đồng thời, conflict đổi quyền đồng thời: NOT_RUN trên máy thật.
- Upload quá dung lượng/MIME sai, GPS bị từ chối/độ chính xác thấp, TalkBack/cỡ chữ lớn và nhiều thiết bị: NOT_RUN.
- Password login đúng của ba tài khoản gốc: NOT_RUN; dùng QA sessions cho nghiệp vụ của chúng như đã nêu.
- Risk scoring có policy thật, HTTPS/cloud và release APK: không được xác nhận bởi phiên local này.
- Các regression JVM/backend của đợt auth trước có tài liệu riêng; không chạy lại và không lấy số cũ làm số test mới.

## Bằng chứng, thay đổi và dọn dữ liệu

- Evidence local (Git ignored): android/build/reports/qa-roles/. JSON UI che trường mật khẩu; không đưa token/key vào tài liệu này.
- Test harness mới: android/app/src/androidTest/java/com/comeforfood/saferoute/DeviceAuditBridgeTest.java.
  Chỉ bật bằng deviceAudit=true và secret mỗi phiên trong app-private config; bind loopback, giới hạn 45 phút.
  Có session/input tổng hợp chỉ dành cho QA; không được tính là đăng nhập UI hoặc camera UI.
- assembleDebugAndroidTest PASS. Lượt bridge cuối kết thúc OK (1 test).
  Đây là một test vận hành bridge, **không có nghĩa mọi business case PASS**.
- Không sửa logic app/backend production trong đợt audit này.
- Đã xóa đúng report QA #2/#3 cùng hai ảnh tổng hợp và dữ liệu phụ thuộc sau khi lưu snapshot bằng chứng.
- Report gốc #1 vẫn PENDING, một vote, không có review mới; vai trò bốn tài khoản gốc không đổi.
- Giữ tài khoản QA #5 ở USER và hai audit cấp/thu hồi quyền để không xóa lịch sử quản trị.
- Phiên QA trên điện thoại đã logout, bridge/cổng forward 37123 đã đóng, reverse 8000 được khôi phục.
- Thiết lập giữ màn hình sáng khi cắm sạc do test bật đã tắt lại. Không push Git.

## Thứ tự xử lý đề xuất

1. F01 và F02; retest toàn bộ list/detail/community trên điện thoại sau idle.
2. F03/F05 và F07 để reviewer/admin tìm lại dữ liệu và người dùng hiểu trạng thái vote.
3. F09/F10, F08; chốt rõ phạm vi F04/F06 trước khi công bố hoàn thiện.
4. Chạy bổ sung camera/picker, lifecycle và ghi dữ liệu khi đứt kết nối; chỉ đóng bug khi có evidence retest.
