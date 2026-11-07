"""Explicit development seed. Run Alembic first; never creates schema."""

from app.core.config import settings
from app.core.security import get_password_hash
from app.db.session import SessionLocal
from app.models.entities import User, UserRole


def init_db():
    if settings.ENVIRONMENT not in ("development", "test") or not settings.ENABLE_DEMO_ACCOUNTS:
        raise RuntimeError("Demo seed requires development/test and ENABLE_DEMO_ACCOUNTS=true")
    with SessionLocal.begin() as db:
        for role in UserRole:
            email = role.value.lower() + "@saferoute.ai"
            if not db.query(User).filter(User.email == email).first():
                db.add(User(email=email, password_hash=get_password_hash("123456"), role=role))


if __name__ == "__main__":
    init_db()
