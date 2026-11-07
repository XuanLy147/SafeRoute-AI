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

## Kết quả chốt trên commit merge

- Merge commit: `d53d6f82aa806ba60896ec9959a9a3dec780fcac`, hai parent `678e0a8` và `0877ef1`; đã push `frontend`.
- **GitHub Actions PASS toàn bộ**: [run 42366695080](https://github.com/XuanLy147/SafeRoute-AI/actions/runs/42366695080), chạy chính SHA merge, hoàn tất 18:10 ngày 2026-11-07 (UTC+7).
- Gates PASS: Ruff; PostGIS/migrations/regression/HTTP E2E; checkpoint AI CPU; runtime OpenAPI + ví dụ; Gitleaks; Docker build; HTTP container + restart/persistence.
- [Bản ghi kết quả CI](evidence/frontend-tv345-ci.json) lấy từ GitHub API. Các bài HTTP này kiểm tra backend với fixture được đánh dấu; không gọi Google Routes live và không chứng minh Android gọi API.
- Docker local khởi động được nhưng tải base image chậm; dừng lượt build local trùng lặp sau khi chuyển xác nhận sang CI. Không ghi local Docker build là PASS; không sửa runtime hoặc hạ test gate để vượt kiểm tra.
- Source backend/scripts so với nguồn TV345 chỉ chuẩn hóa whitespace ở docstring migration và EOF script container. Bằng chứng log lịch sử được giữ nguyên, kể cả khoảng trắng của log.
- Commit bàn giao sau merge chỉ thay tài liệu/evidence; source Android/backend giữ nguyên bản đã kiểm thử. CI backend lọc theo source/contract/workflow, không chạy lại do bản cập nhật tài liệu integration này.
