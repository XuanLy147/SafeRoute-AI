# Chính sách so sánh nguy cơ theo bằng chứng — 08/11/2026

## Phạm vi và trạng thái

Owner liên quan: TV1/TV2 (Android/contract), TV3/TV5 (PostGIS/routing).
Phiên bản: `recorded-hazards-v1-2026-11-08`, trạng thái `EXPERIMENTAL`.
Được triển khai trong development theo yêu cầu của chủ dự án. Production giữ scorer/cổng phê duyệt cũ; không tự bật chính sách thử nghiệm và không đổi readiness thành true.

## Công thức

Điểm mỗi nguy cơ = trọng số loại × mức độ × xác minh × độ mới.
- D00/D10/Nứt đường: 1; D20: 2; D40: 3.
- Vết sửa chữa (Repair): 1; vật cản/ngập nước: 3; đường gồ ghề: 2. Đây là các trọng số thử nghiệm bổ sung, không phải tiêu chuẩn đã hiệu chỉnh.
- LOW: 1; MEDIUM: 1,5; HIGH: 2.
- VERIFIED: 1; PENDING_VERIFICATION: 0,3; REJECTED không truy vấn.
- Dựa trên observed_at: <=7 ngày: 1; >7 đến <=30 ngày: 0,7; >30 ngày: 0,4. Cũ không đồng nghĩa đã sửa.
- Loại lạ, thiếu mức độ/thời điểm hoặc thời điểm tương lai: đóng góp null, điểm toàn tuyến null; không mặc định nhẹ/0.
- Tổng điểm = tổng đóng góp. Mật độ = tổng điểm / km; quãng đường 0 thì mật độ null.
- Không tự đặt ngưỡng LOW/MEDIUM/HIGH cho toàn tuyến; mức độ từng báo cáo vẫn giữ nguyên. AI confidence không tham gia.

## Dữ liệu thống nhất

Một SQL SELECT đọc tất cả cảnh báo và cờ thuộc từng tuyến trong cùng snapshot PostgreSQL. Bán kính 30 m tính bằng Geography/PostGIS từ polyline thật; không lấy danh sách phân trang và không gửi tọa độ báo cáo cho Google.

Tất cả điểm, số lượng, mức độ, ghim được tạo từ cùng evidence. Cùng loại chuẩn hóa + trùng chính xác tọa độ được gộp, lưu source_report_ids; không sửa/xóa hàng dữ liệu gốc. Trong nhóm ưu tiên nguồn đã xác minh, mức độ cao hơn, rồi điểm lớn hơn. Bản ghi thiếu dữ liệu không bị nguồn đầy đủ che mất. Không gộp gần nhau bằng bán kính tùy ý.

Đây là quy tắc gộp bảo thủ, chưa thay thế mã sự cố do reviewer xác nhận: hai sự cố độc lập có cùng loại và tọa độ có thể bị gộp; một sự cố có GPS lệch có thể chưa được gộp. Cần mô hình sự cố riêng để xử lý triệt để.

Response có assessed_at, revision, severity_counts, verified_high_count, unscored_hazards_count, contribution và source_report_ids. Validator kiểm tra tổng điểm, đếm trạng thái, mức độ, nguồn không trùng, route ID và khả dụng. Android kiểm tra lại số lượng, số xác minh và tổng đóng góp trước khi hiển thị.

Truy vấn lỗi => evidence null và không có điểm; truy vấn thành công không có cảnh báo => [] và điểm 0, kèm thông báo chưa ghi nhận nguy cơ. Mỗi lần so sánh là một snapshot, không phải subscription thời gian thực.

Schema hiện chỉ có PENDING_VERIFICATION/VERIFIED/REJECTED, chưa có trạng thái RESOLVED. Repair là loại vết sửa chữa, tuyệt đối không coi là đã xử lý xong.

## Chọn tuyến

Chỉ khi tất cả tuyến có điểm đầy đủ:
1. Giữ nhóm có duration <= 1,2 × thời gian nhanh nhất.
2. Ít nguy cơ HIGH đã xác minh hơn.
3. Tổng điểm thấp hơn.
4. Thời gian ngắn hơn, rồi khoảng cách, ID để kết quả ổn định.

Nếu thiếu điểm ở bất cứ tuyến nào: đề xuất ETA, nói rõ không đủ dữ liệu so nguy cơ. Chỉ có một tuyến thì không tạo tuyến giả. Lý do hiển thị đúng quy tắc, số HIGH, điểm và thời gian thêm. Một tuyến không có cảnh báo chưa chắc an toàn; nguy cơ trong 30 m có thể thuộc đường bên cạnh.

## Thay đổi UI

Bỏ “Chưa tính điểm rủi ro tổng hợp.”. Có điểm thì hiện điểm backend và số HIGH/MEDIUM/LOW, mật độ, nhãn thang điểm thử nghiệm. Thiếu dữ liệu thì hiện “Chưa đủ dữ liệu để tính điểm nguy cơ.”. Nhãn đề xuất: “Đề xuất theo nguy cơ và thời gian”. Giữ các luồng login/RBAC, danh sách và chọn ghim.

## Kiểm thử

- Android: 93 bài JVM PASS, assembleDebug/lintDebug PASS; log scoring-android.log. Không tuyên bố đã kiểm tra trực quan mọi trạng thái UI; E2E kiểm tra hành vi và dữ liệu trên máy thật.
- Backend toàn bộ: 230 bài + 12 subtests PASS; Ruff PASS. Log scoring-backend-final.log.
- Contract: runtime OpenAPI và JSON examples PASS; log scoring-contract.log.
- Kiểm thử bổ sung sau điều chỉnh production: 48 bài PASS, gồm cập nhật trạng thái báo cáo và giữ cổng production; scoring-final-targeted.log.
- Máy thật Xiaomi Android 13: E2E cuối PASS, Maps/Routes thật PASS, lưu dữ liệu sau khởi động lại PASS; scoring-device-final.log. Đã cài APK và cập nhật backend development local. /ready và compare tuyến mẫu đều HTTP 200; compare trả chính sách mới EXPERIMENTAL/AVAILABLE.
- Tất cả log nằm trong android/build/reports/qa-roles/ (Git bỏ qua). Backend dùng database kiểm thử tạm, không sửa quyền/báo cáo thật.

## Phê duyệt tự động

Auto-review từ chối thao tác đặt risk_ready=true vì có thể làm chính sách thử nghiệm bị hiểu là đã phê duyệt chính thức. Thao tác đó không thực hiện. Giữ nguyên readiness/admin gate, không có bước vượt chặn. Không push hoặc deploy cloud.
