# SafeRoute AI — Backend Service (FastAPI & PostgreSQL/PostGIS)

**Owner: TV3 (Backend & Database / Security)**
Tích hợp cùng: TV4 (AI YOLOv12s service) & TV5 (Google Routes & Risk Scoring service)

---

## 1. Kiến trúc & Công nghệ
- **Framework**: Python 3.11+, FastAPI (Modular Monolith)
- **Database**: PostgreSQL 16 + PostGIS 3.4/3.5 (`geography(Point, 4326)` + GiST spatial index)
- **ORM & Migrations**: SQLAlchemy 2.0, Alembic
- **Bảo mật**: JWT (OAuth2 Password Bearer), Passlib (Bcrypt), RBAC server-side (`USER`, `REVIEWER`, `ADMIN`)
- **Tích hợp**:
  - `POST /api/v1/ai/detect`: Service inference YOLOv12s (TV4)
  - `POST /api/v1/routes/compare`: Adapter Google Routes + PostGIS risk scoring (TV5)

---

## 2. Cấu trúc thư mục

```text
backend/
├── app/
│   ├── main.py                  # Khởi tạo FastAPI, CORS, GET /health, mount router v1
│   ├── core/
│   │   ├── config.py            # Pydantic Settings, biến môi trường, CORS, JWT config
│   │   └── security.py          # Hashing mật khẩu, tạo/giải mã JWT token, dependencies RBAC
│   ├── db/
│   │   ├── session.py           # Engine SQLAlchemy, SessionLocal, get_db()
│   │   ├── init_db.py           # Kích hoạt PostGIS, tạo 8 bảng, seed 3 user + risk weights
│   │   └── geo.py               # Helper WKTElement POINT(lon lat) SRID 4326
│   ├── models/
│   │   └── entities.py          # 8 SQLAlchemy models + 3 Enums (UserRole, ReportStatus, VoteType)
│   ├── api/v1/
│   │   └── api.py               # Router v1 gộp các sub-routers
│   └── modules/
│       ├── auth/                # Login, register, profile
│       ├── reports/             # Báo cáo hư hỏng mặt đường, lọc bounds, my reports
│       ├── images/              # Upload ảnh, kiểm định MIME/kích thước
│       ├── votes/               # Community vote (SUPPORT, OPPOSE, UNSURE)
│       ├── review/              # Review queue, duyệt/từ chối kèm lý do + audit log
│       ├── ai/                  # YOLOv12s inference service (TV4)
│       └── routing/             # Google Routes & Risk scoring (TV5)
├── migrations/                  # Alembic migrations
├── tests/                       # Pytest suite kiểm thử tự động
├── Dockerfile                   # Container image cho Cloud Run và Docker Compose
├── docker-compose.yml           # Khởi chạy PostGIS container và FastAPI
├── pyproject.toml               # Quản lý dependencies & cấu hình công cụ
└── requirements.txt             # Khóa phiên bản thư viện
```

---

## 3. Hướng dẫn chạy nhanh

### Cách 1: Chạy bằng Docker Compose (Khuyên dùng - tự động bật PostGIS)
```bash
cd backend
docker compose up --build -d
```
Backend sẽ tự động chạy tại: `http://localhost:8000`
- Swagger Docs: `http://localhost:8000/docs`
- Health check: `http://localhost:8000/health`

### Cách 2: Chạy trực tiếp qua Virtualenv
```bash
cd backend
python -m venv .venv
# Trên Windows:
.venv\Scripts\activate
# Trên Linux/macOS:
source .venv/bin/activate

pip install -r requirements.txt

# Khởi tạo PostGIS & seed data:
python -m app.db.init_db

# Chạy server FastAPI:
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

---

## 4. Chạy kiểm thử tự động (Pytest)
```bash
pytest -v
```
Toàn bộ test cases cho Health check, Ping v1, Enums, Models và PostGIS Point tọa độ sẽ chạy và hiển thị kết quả `PASS`.
