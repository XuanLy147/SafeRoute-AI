# Kiến trúc SafeRoute AI

```text
Android Java + XML (TV1/TV2)
   | HTTPS / REST
   v
FastAPI (TV3)
   |-- Auth/Reports/Votes/Review --> PostgreSQL/PostGIS
   |-- AI detect (TV4) -------> YOLOv12s v1.1
   |-- Routing (TV5) --------> Google Routes API + Risk scorer/PostGIS
```

## Chức năng P0

1. Ảnh + vị trí GPS → AI gợi ý → người dùng sửa/nhập thủ công → tạo report PENDING → marker trên map.
2. Reviewer xác minh/từ chối; vote cộng đồng không thay quyền reviewer.
3. Điểm A/B → Google Routes trả candidates → phân tích nguy cơ ghi nhận trên dữ liệu PostGIS → đề xuất tuyến và lý do, không bảo đảm tuyệt đối an toàn.

## Hợp đồng cần chốt

API JSON, enum status/role/vote, bbox coordinate system, DTO, geometry lat/lon, risk score, error format và auth.
