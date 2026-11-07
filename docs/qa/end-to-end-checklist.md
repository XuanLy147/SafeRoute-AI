# E2E checklist (chỉ tick khi PASS thực tế)

- [ ] APK mở Map trên điện thoại thật
- [ ] Image pick/camera → GPS → AI detections hoặc [] → manual correction → submit
- [ ] Report PENDING xuất hiện trên bản đồ với badge chưa xác minh
- [ ] Reviewer/Admin duyệt hoặc từ chối kèm lý do; USER bị chặn quyền duyệt
- [ ] Nhập A/B → Google Routes response thật → risk score từ PostGIS → so sánh đúng số tuyến trả về
- [ ] Backend /health và HTTPS cloud response hoạt động
- [ ] Chạy tests, lưu log và đo latency/cost có đơn vị
- [ ] Không commit API key, token và model weights không được phép
