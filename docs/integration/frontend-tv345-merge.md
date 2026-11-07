# Ghép frontend và TV345 — 2026-11-07

## Nguồn và phạm vi

- Frontend trước ghép: `678e0a8` (UI/logo mới), đã push `origin/frontend` theo yêu cầu.
- Backend: `0877ef1`, nhánh `integration/tv345-backend-handoff`, gồm lịch sử TV03/TV4/TV5.
- Merge Git không có xung đột source. Giữ Android Java/XML, backend FastAPI/PostGIS, weights và OpenAPI từ nguồn. Không force-push, không sửa main hoặc nhánh nguồn TV345.
- README/tracker cập nhật để không quay về trạng thái scaffold. Thay đổi tài liệu local trước ghép có bản lưu trong stash; các mốc cũ được giữ dưới dạng lịch sử. .vscode và local.properties không được đưa vào commit.
- Mở rộng workflow backend hiện có sang frontend để kiểm tra chính bản merge.

## Kiểm chứng

- Android sau ghép: **53/53 JVM PASS**, assembleDebug và lintDebug PASS; **0 lỗi, 159 cảnh báo**. Log local: `android/build/reports/tv1/tv345-merge-android-tests.log`, `tv345-merge-android-build.log`.
- Android không đổi logic trong merge; bài native gần nhất trước ghép PASS 37.91s. Không coi đây là kiểm thử Android–backend E2E.
- Backend source trước ghép có 179 tests và các runtime gates được nhánh nguồn ghi nhận; chưa dùng số đó làm kết quả mới của bản merge. Kết quả xác nhận trên bản merge được ghi bổ sung bên dưới.

## Điểm nối còn phải triển khai

Hợp đồng API đã có tại `docs/api/openapi.json` và `docs/handoff/`. App hiện vẫn dùng Tv1DataSource chưa kết nối; auth/report/review chưa gọi server. Merge source không tự tạo HTTP client hoặc bật các luồng thành công.

Các việc tiếp theo: shared HTTP/image client; auth 401/403/session; upload/AI/manual/report; danh sách/detail/vote/review; adapter reports/routes và consumer tests; cuối cùng kiểm thử hai luồng P0 trên điện thoại. Khi map routes phải mang theo nullable risk, assessment status, recommendation basis và nhãn DEMO_UNAPPROVED; không diễn giải unknown risk thành 0. Ảnh private cần Bearer header.

Chưa có bằng chứng Google Routes live, Cloud Run, hay policy risk production được duyệt. Không tự thêm khóa, deploy cloud hoặc tạo dữ liệu demo như dữ liệu thật.
