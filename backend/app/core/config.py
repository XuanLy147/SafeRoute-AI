import os

from dotenv import load_dotenv
from pydantic_settings import BaseSettings, SettingsConfigDict

load_dotenv()


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    PROJECT_NAME: str = "SafeRoute AI Backend"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    ENVIRONMENT: str = os.getenv("ENVIRONMENT", os.getenv("ENV", "development"))

    RISK_POLICY_JSON: str | None = None
    ENABLE_DEMO_ACCOUNTS: bool = False
    PERSISTENT_UPLOADS: bool = False

    # Google Routes API
    GOOGLE_ROUTES_API_KEY: str | None = os.getenv("GOOGLE_ROUTES_API_KEY")

    # Database
    DATABASE_URL: str = os.getenv("DATABASE_URL", "postgresql+psycopg2://postgres:postgres@localhost:5432/saferoute")

    # Security & JWT
    SECRET_KEY: str = os.getenv("SECRET_KEY", "saferoute-hackathon-24h-super-secret-key-2026")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24  # 24 giờ cho sự kiện Hackathon

    # CORS
    BACKEND_CORS_ORIGINS: list[str] = [
        "*",  # Cho phép tất cả nguồn (Android Studio emulator 10.0.2.2, thiết bị thật, web)
    ]

    # Uploads
    UPLOAD_DIR: str = os.getenv("UPLOAD_DIR", "uploads")
    MAX_IMAGE_SIZE_MB: int = 5
    ALLOWED_IMAGE_TYPES: list[str] = ["image/jpeg", "image/png", "image/webp"]

    def validate_production_security(self):
        """Kiểm tra nghiêm ngặt các rào chắn bảo mật trong môi trường production."""
        if self.ENVIRONMENT.lower() == "production":
            if not self.PERSISTENT_UPLOADS:
                raise ValueError("Production requires persistent private photo storage")
            if len(self.SECRET_KEY) < 32 or self.SECRET_KEY in {
                "saferoute-hackathon-24h-super-secret-key-2026",
                "change-this-in-production-to-a-secure-random-key",
            }:
                raise ValueError(
                    "FATAL SECURITY: Không được dùng SECRET_KEY mặc định hoặc rỗng trong môi trường production!"
                )


settings = Settings()
settings.validate_production_security()
