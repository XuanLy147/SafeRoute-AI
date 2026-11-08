# Hiển thị cảnh báo theo tuyến — 08/11/2026

## Nguyên nhân và thay đổi

Màn so sánh trước đây chỉ vẽ polyline và A/B. API chưa trả tọa độ các cảnh báo theo từng tuyến; số lượng ở thẻ tuyến chỉ hiển thị khi có điểm risk.

- Backend thêm `nearby_hazards` và `hazard_radius_meters` cho mỗi tuyến. Truy vấn PostGIS lấy toàn bộ báo cáo VERIFIED/PENDING_VERIFICATION trong phạm vi 30 m; không dùng giới hạn 200 báo cáo của danh sách công khai. REJECTED bị loại.
- Dữ liệu cảnh báo độc lập với chính sách chấm điểm: chưa có risk vẫn xem được cảnh báo. Không tạo hoặc thay trọng số/chính sách risk.
- Danh sách null biểu thị chưa tra cứu được; danh sách rỗng biểu thị truy vấn thành công nhưng không thấy cảnh báo. Lỗi truy vấn không được diễn giải thành 0 nguy cơ.
- Android hiện số đã/chưa xác minh và số lượng theo loại trên từng thẻ tuyến; chọn tuyến để xem ghim. Ghim phân biệt trạng thái; chạm cửa sổ thông tin để mở chi tiết báo cáo.
- Bán kính chỉ là gần đường vẽ, không chứng minh đúng đường/làn. UI nêu rõ có thể gồm đường bên cạnh.
- OpenAPI cập nhật tương ứng; phản hồi backend cũ thiếu trường vẫn dùng được, nhưng hiện chưa tải được điểm cảnh báo.

## Kiểm chứng

| Hạng mục | Kết quả |
|---|---|
| Android JVM | 92 bài đạt, 0 lỗi/thất bại |
| APK và lint Android | Đạt |
| Backend | 210 bài + 12 kiểm thử con đạt |
| Ruff | Ban đầu lỗi thứ tự import; đã sửa, kiểm tra toàn bộ đạt |
| OpenAPI và JSON mẫu | Đạt |
| PostGIS thật, dữ liệu tổng hợp | Tuyến gần/xa, trạng thái duyệt/từ chối, tọa độ, không có chính sách risk và lỗi truy vấn được kiểm tra |
| API Google thật qua backend local | HTTP 200, hai tuyến có danh sách cảnh báo và bán kính 30 m; tuyến mẫu có 0 cảnh báo |
| Điện thoại E2E | Đạt: USER/REVIEWER/ADMIN, Maps/Routes thật, trường cảnh báo theo tuyến và lưu dữ liệu sau khởi động lại |

Đã cập nhật riêng container backend local và cài APK; không đổi schema hay xóa cơ sở dữ liệu. Không commit/push.

Bằng chứng local: `android/build/reports/qa-roles/route-hazards-*.log`.

## Giới hạn

- Chưa có cấu hình chính sách risk nên API thật vẫn có thể trả risk=null. Phần mới là bằng chứng cảnh báo, không thay thế điểm rủi ro.
- Chưa đối soát làn/đường song song hoặc tầng cầu.
- Kiểm tra bổ sung dùng vị trí báo cáo có sẵn bị bộ duyệt tự động từ chối vì có thể gửi vị trí sang Google khi chưa có cho phép riêng. Không thực hiện lại; dùng PostGIS tổng hợp và tuyến Google mẫu thay thế.
