import sys
from sqlalchemy import text
from passlib.context import CryptContext
from app.db.session import engine, SessionLocal, Base
from app.models.entities import User, UserRole, RiskWeight

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def init_db():
    try:
        # 1. Kích hoạt PostGIS extension trong PostgreSQL
        with engine.begin() as conn:
            conn.execute(text("CREATE EXTENSION IF NOT EXISTS postgis;"))

        # 2. Tạo tất cả 8 bảng
        Base.metadata.create_all(bind=engine)

        # 3. Seed 3 tài khoản mẫu (USER, REVIEWER, ADMIN) nếu chưa có
        db = SessionLocal()
        try:
            if db.query(User).count() == 0:
                seed_users = [
                    User(email="user@saferoute.ai", password_hash=pwd_context.hash("123456"), role=UserRole.USER),
                    User(email="reviewer@saferoute.ai", password_hash=pwd_context.hash("123456"), role=UserRole.REVIEWER),
                    User(email="admin@saferoute.ai", password_hash=pwd_context.hash("123456"), role=UserRole.ADMIN),
                ]
                db.add_all(seed_users)

            if db.query(RiskWeight).count() == 0:
                seed_weights = [
                    RiskWeight(category="D40", weight=10.0, severity="HIGH", verification_weight=1.0),
                    RiskWeight(category="D20", weight=6.0, severity="MEDIUM", verification_weight=1.0),
                    RiskWeight(category="D00", weight=3.0, severity="LOW", verification_weight=1.0),
                    RiskWeight(category="D10", weight=3.0, severity="LOW", verification_weight=1.0),
                    RiskWeight(category="Repair", weight=1.0, severity="LOW", verification_weight=0.5),
                ]
                db.add_all(seed_weights)

            db.commit()
            print("✅ Đã khởi tạo PostGIS, tạo 8 bảng và seed 3 tài khoản (USER/REVIEWER/ADMIN) thành công!")
        finally:
            db.close()
    except Exception as e:
        print(f"⚠️ Lưu ý khi khởi tạo DB: {e}", file=sys.stderr)
        print("💡 Hãy đảm bảo PostgreSQL + PostGIS đang chạy (ví dụ qua Docker Compose).", file=sys.stderr)
        raise

if __name__ == "__main__":
    init_db()
