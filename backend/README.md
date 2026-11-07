# FastAPI & PostgreSQL/PostGIS — TV3 (TV4/TV5 tích hợp)

Một backend FastAPI. TV3 quản lý API, Auth, Report, Vote, Review, migrations và schema.
TV4 phụ trách service AI, TV5 phụ trách service routing và scoring trong cùng backend.

Các thư mục dự kiến khi triển khai:
- `app/core/`, `app/db/`, `app/api/v1/`, `app/models/`
- `app/modules/{auth,users,reports,images,votes,review,ai,routing}/`
- `migrations/versions/`, `tests/`

Endpoint dự kiến: `GET /health`, `POST /api/v1/ai/detect`, `POST /api/v1/routes/compare`. Dữ liệu request/response phải theo OpenAPI được xuất từ code thật.

Đừng tạo `main.py`, requirements hoặc migrations rỗng như thể API đã hoạt động.
