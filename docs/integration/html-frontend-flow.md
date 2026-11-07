# Luồng Android theo Full Mobile Prototype

Ngày 2026-11-07. Tham chiếu: SafeRoute_AI_Full_Mobile_Prototype.html trong thư mục Công việc do người dùng cung cấp. Phạm vi đã xác nhận: USER, REVIEWER và ADMIN. HTML là bản mẫu điều hướng; dữ liệu minh họa không được chuyển thành kết quả thật.

## Điểm vào và điều hướng

- Lần đầu: Welcome → 3 màn giới thiệu → giải thích quyền → Login ↔ Register.
- Các lần sau: Login nếu chưa có phiên; tài khoản có phiên mở trang theo quyền.
- Khi auth chưa nối: Login có lựa chọn rõ ràng “Xem bản đồ với tư cách khách”; không tạo token, không nâng quyền.
- USER: Bản đồ / Báo cáo / Của tôi / Tài khoản. Tìm đường chuyển thành thao tác từ Bản đồ.
- REVIEWER: Tổng quan / Chờ duyệt / Lịch sử / Bản đồ.
- ADMIN: Quản trị / Báo cáo / Nhật ký / Tài khoản (quản lý người dùng). Hồ sơ của chính mình truy cập từ trang tổng quan.
- Ẩn tab khi onboarding, auth, báo cáo, xác nhận xét duyệt và phân quyền. Giữ system/IME insets.
- Back dùng FragmentManager; wizard báo cáo lùi từng bước, về bản đồ ở bước đầu. Tab mới về màn gốc, bấm lại tab thoát màn con.

## Ánh xạ các nhóm màn HTML

| Nhóm HTML | Triển khai native |
|---|---|
| splash, onboard1–3, permission, login, register | WelcomeFragment, OnboardingFragment, LoginFragment, RegisterFragment |
| map, search, searchResults, filters, nearby | MapFragment và MapFlowFragment; tìm địa danh bằng Android Geocoder, chọn kết quả đưa camera bản đồ tới địa điểm |
| pinPreview, alertDetail, gallery | Preview của marker → chi tiết lấy Hazard thật từ datasource → màn ảnh báo rõ khi nguồn chưa có ảnh/bbox |
| reportStart, imagePicker, photoPreview | ReportWizardFragment: giới thiệu → camera/bộ chọn ảnh hệ thống → xem lại ảnh |
| gps | Lấy GPS có consent hoặc chạm/kéo ghim Google Maps; không gán tọa độ mẫu vào báo cáo |
| aiLoading, aiDetected, aiUncertain | Khi chưa có dịch vụ AI: bỏ qua loading giả, hiển thị chưa kết nối và đường nhập nhãn thủ công; chưa có kết quả detection thật |
| reportType, reportReview, reportFailure | Chọn nhãn/severity, mô tả → tóm tắt ảnh/GPS/nhãn và checkbox xác nhận → lỗi kết nối, giữ draft và cho thử lại |
| reportSuccess, feedbackDone | Chưa cho đi tới khi chưa có response thành công từ dịch vụ; không hiển thị thành công giả |
| myReports, myReportDetail | Tab Của tôi; danh sách chưa kết nối, không hiển thị mẫu làm báo cáo thật. Chi tiết chỉ có ý nghĩa khi backend cung cấp bản ghi |
| profile, settings, privacy, saved, notifications | ProfileFragment + FlowPageFragment; Settings mở quyền Android hoặc xem lại giới thiệu; Saved/Notifications ghi rõ là mở rộng chưa có API |
| reportFeedback | CommunityFeedbackFragment nhận tên/id từ báo cáo đang xem; chưa gửi góp ý khi API chưa tích hợp |
| empty, offline, gpsDenied | Trạng thái tại đúng màn liên quan; không tạo màn giả có dữ liệu. GPS bị từ chối vẫn chọn ghim được nếu Maps sẵn sàng |
| reviewHome, reviewQueue, reviewDetail, reviewApprove, reviewReject, reviewHistory, possibleDuplicate | FlowPageFragment có màn riêng và Back stack; kiểm tra REVIEWER/ADMIN, hàng chờ/history chưa có dữ liệu; chưa thể chọn bản ghi giả để duyệt |
| adminHome, adminReports, adminReview, adminAudit | Workspace ADMIN và kiểm tra quyền mỗi lần mở/resume |
| adminUsers, adminRoles, adminMetrics, adminSettings | Trang mở rộng theo HTML, báo rõ API chưa có; không có chức năng tự cấp quyền hoặc thay cấu hình thật |
| routing, routeEmpty | Giữ Google Routes boundary theo stack hiện hành; không đổi sang OSRM của HTML cũ. Nhập tên/địa chỉ A/B, hiển thị đúng tuyến/no-alternative từ datasource |

## Trạng thái và quyền

ReportDraft dùng SavedStateHandle cho bước, URI ảnh, GPS, nhãn, severity, mô tả và xác nhận. Không phải cơ chế lưu nháp lâu dài trên server. Ảnh chọn bằng OpenDocument có URI grant; camera dùng FileProvider giới hạn cache/report-photos. Chỉ mở máy ảnh theo thao tác người dùng. Đăng xuất xóa draft khỏi phiên.

Các trang reviewer/admin kiểm tra SessionManager, không có selector tự chọn vai trò trong bản app. Đây là chặn UI; backend vẫn phải xác thực token/RBAC/audit. Auth, upload, inference, vote, review, user management, metrics và routing backend chưa được nối. Không tuyên bố đã chạy E2E.

## Kiểm thử

- 53/53 JVM tests PASS, gồm 6 HtmlFlowTest mới: onboarding/guest, phiên quay lại, bản đồ/account/back, wizard/draft/recreate/failure, role gates/menu, giao diện lớn/night.
- assembleDebug và assembleDebugAndroidTest PASS. Lint 0 errors, 138 warnings; phần lớn là resources cũ chưa dùng sau khi thay wizard/reviewer.
- Log: android/build/reports/tv1/html-flow-tests.log và html-flow-build.log.
- Kiểm thử thiết bị và ảnh cuối được bổ sung trong docs/qa/tv1-validation.md.
