# Đăng nhập và phân quyền — 08/11/2026

Phạm vi: repo SafeRoute-AI, nhánh local/main-latest-20261107. Thay đổi local; chưa commit/push.

## Luồng tài khoản

- Đăng ký email/mật khẩu luôn tạo USER; API từ chối tự đăng ký REVIEWER/ADMIN.
- Đăng nhập/đăng ký lấy token rồi gọi /auth/me. Chỉ lưu phiên khi profile và vai trò hợp lệ; quyền từ /me, không lấy lựa chọn của client hoặc tin vai trò cũ trong token.
- ViewModel giữ yêu cầu đang chạy khi xoay màn hình; chặn gửi lặp. Phản hồi cũ không ghi đè phiên mới hoặc khôi phục phiên đã đăng xuất.
- Cold start xác minh lại phiên. Mất mạng/503 giữ credentials và cho thử lại; 401 xóa phiên và chuyển về đăng nhập. Chỉ 403 có header X-SafeRoute-Role-Refresh: required mới yêu cầu xác minh lại quyền; 403 ảnh riêng giữ phiên và màn chi tiết, hiển thị ảnh không khả dụng.
- Khi app trở lại foreground, làm mới profile. Backend kiểm tra vai trò hiện tại trong DB trên mỗi yêu cầu được bảo vệ.
- Khách xem bản đồ/tìm đường; tạo báo cáo và báo cáo của tôi yêu cầu đăng nhập, sau thành công quay lại thao tác đã chọn.
- Đăng xuất xóa token/profile trên máy, draft báo cáo và back stack riêng tư. Token được mã hóa Android Keystore; nếu Keystore không dùng được thì chỉ giữ trong RAM.
- Google/Facebook được ẩn vì chưa tích hợp; quên mật khẩu giải thích rõ chưa có dịch vụ email. Không giả lập gửi email/thành công.
- Form kiểm tra email, mật khẩu ít nhất 6 ký tự / tối đa 72 byte UTF-8, xác nhận mật khẩu; không lưu mật khẩu trong saved view state.

## Quyền

| Tác vụ | Khách | USER | REVIEWER | ADMIN |
|---|---|---|---|---|
| Xem bản đồ, tuyến, báo cáo công khai | Có | Có | Có | Có |
| Tạo báo cáo, vote, báo cáo cá nhân | Không | Có | Có | Có |
| Hàng đợi và xét duyệt báo cáo | Không | Không | Có | Có |
| Tìm tài khoản, đổi quyền, đọc nhật ký quyền | Không | Không | Không | Có |

Ảnh riêng chỉ dành cho chủ ảnh hoặc REVIEWER/ADMIN theo kiểm tra backend hiện có.
Ẩn nút trên app không thay kiểm tra quyền phía server.

## ADMIN quản lý quyền trong app

Vào workspace ADMIN → Tài khoản / Quản lý quyền:
1. Tìm email/họ tên, danh sách phân trang 30 tài khoản.
2. Chọn tài khoản → USER / REVIEWER / ADMIN, nhập lý do 3–500 ký tự → Lưu thay đổi.
3. Xem Lịch sử thay đổi quyền: actor ID, target ID, quyền cũ/mới, lý do và thời điểm.
4. Không tự đổi quyền của chính mình. Các thay đổi được tuần tự hóa trong transaction, kiểm tra lại actor sau khi lấy lock; người bị hạ quyền không được hoàn thành thao tác ADMIN đã chờ.
5. expected_role phát hiện dữ liệu cũ; trả 409 thay vì ghi đè thay đổi vừa xảy ra. API không có thao tác sửa/xóa audit.

API mới (OpenAPI đã sinh lại):
- GET /api/v1/admin/users?q=&offset=0&limit=30
- PATCH /api/v1/admin/users/{id}/role — {role, expected_role, reason}
- GET /api/v1/admin/role-audit?user_id=&offset=0&limit=30

Migration 0003_role_audit chỉ thêm bảng/index nhật ký; không đổi quyền hoặc xóa tài khoản hiện có.
Bootstrap đầu tiên chỉ chạy rõ ràng bằng server operator, yêu cầu tài khoản đã đăng ký và chưa có ADMIN:
```powershell
docker compose --env-file backend/.env -p saferoute-local -f backend/docker-compose.yml exec backend python -m app.bootstrap_admin --email EMAIL_DA_DANG_KY
```
Lệnh yêu cầu gõ lại email xác nhận, ghi audit; không tự chạy lúc startup. ADMIN sau đó cấp các quyền tiếp theo trong app.

## Bằng chứng

- Android: 72 JVM tests PASS, assembleDebug / assembleDebugAndroidTest PASS, lintDebug 0 errors.
- Backend: 196 tests + 12 subtests PASS, Ruff PASS; database PostGIS test riêng đã xóa sau chạy.
- Migration: upgrade head → downgrade 0002 → upgrade head PASS trên DB test. Backend local đã được cập nhật và migration applied.
- OpenAPI: verify_contract.py PASS.
- Regression: JWT cũ mất quyền ngay sau thu hồi trong DB; USER/REVIEWER bị chặn admin endpoints; self-demotion/stale role bị chặn; candidate-token/late-response/logout/mất mạng/401 đều được kiểm thử.
- Log local: frontend/build/reports/tv1/auth-rbac-build.log, auth-backend-tests.log, auth-backend-deploy.log. Không commit secrets hoặc ảnh tài khoản thật.

## Giới hạn còn lại

- Chưa có khôi phục mật khẩu qua email, xác minh email, OAuth Google/Facebook, MFA, refresh token hoặc rate limiting chuyên biệt cho đăng nhập.
- Đăng xuất hiện thu hồi credentials trên thiết bị; chưa có endpoint thu hồi JWT từ xa. JWT còn hiệu lực đến hết hạn (cấu hình hiện tại 24 giờ); quyền luôn kiểm tra DB.
- Chưa triển khai khóa/mở khóa tài khoản. Cấp/thu hồi quyền là đổi USER/REVIEWER/ADMIN.
- Lịch sử duyệt, nghi trùng, toàn bộ báo cáo và overview ADMIN đã nối API trong lượt sửa tiếp theo. Cấu hình chỉ đọc; chưa chỉnh AI/risk policy trong app. Xem ../qa/DEVICE_ROLE_FIXES_2026-11-08.md.
- Đây là bản local hackathon, chưa chứng nhận production-ready. HTTPS, cấu hình mail và chống lạm dụng cần triển khai trước khi mở dịch vụ công khai.

### Kiểm tra Xiaomi và cấu hình local

- APK mới đã cài trên Xiaomi 2201117TG (Android 13); /health và /ready trả 200.
- USER được chọn bởi người dùng đã được bootstrap thành ADMIN và có audit tương ứng; không lưu email cá nhân vào tài liệu repo.
- Kiểm thử device AuthRbacDeviceTest chưa PASS: lượt đầu thất bại do so sánh nhãn màn hình sai chữ hoa/thường; đã sửa dùng string resource. Lượt sau instrumentation không hoàn tất và bị dừng thủ công để trả điện thoại. Không coi log “Process crashed” khi force-stop là bằng chứng app tự crash.
- Đã kiểm tra trực quan form đăng ký trên điện thoại, sửa divider mạng xã hội còn sót. Danh sách quản trị và hộp thoại đổi quyền trên máy thật vẫn cần kiểm tra tương tác; các kiểm thử HTTP/JVM tương ứng đã PASS.
- Test fixture không đổi tài khoản trong database thật. Sau khi ngắt instrumentation, người dùng có thể cần đăng nhập lại trên điện thoại.

## Cập nhật kiểm thử sau audit

Các ghi nhận kiểm thử phía trên là lịch sử của lượt auth ban đầu. Lượt audit tiếp theo đã thao tác cấp/thu hồi quyền và lịch sử trên điện thoại; lượt sửa sau audit đạt 77 JVM tests, 206 backend tests + 12 subtests, build/lint/migration/OpenAPI PASS. Xem [báo cáo sửa lỗi](../qa/DEVICE_ROLE_FIXES_2026-11-08.md) để biết API mới, kiểm thử thiết bị và giới hạn. Client/backend phải cập nhật cùng nhau để dùng header lỗi quyền và các endpoint mới.
