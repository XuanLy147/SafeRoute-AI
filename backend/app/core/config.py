import os
from typing import List
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    PROJECT_NAME: str = "SafeRoute AI Backend"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # Database
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        "postgresql+psycopg2://postgres:postgres@localhost:5432/saferoute"
    )
    
    # Security & JWT
    SECRET_KEY: str = os.getenv("SECRET_KEY", "saferoute-hackathon-24h-super-secret-key-2026")
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24  # 24 giờ cho sự kiện Hackathon
    
    # CORS
    BACKEND_CORS_ORIGINS: List[str] = [
        "*",  # Cho phép tất cả nguồn (Android Studio emulator 10.0.2.2, thiết bị thật, web)
    ]
    
    # Uploads
    UPLOAD_DIR: str = os.getenv("UPLOAD_DIR", "uploads")
    MAX_IMAGE_SIZE_MB: int = 5
    ALLOWED_IMAGE_TYPES: List[str] = ["image/jpeg", "image/png", "image/webp"]

settings = Settings()
