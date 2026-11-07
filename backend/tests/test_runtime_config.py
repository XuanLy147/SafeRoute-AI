import pytest

from app.core.config import Settings


@pytest.mark.parametrize(
    "secret",
    ["", "short", "saferoute-hackathon-24h-super-secret-key-2026", "change-this-in-production-to-a-secure-random-key"],
)
def test_production_rejects_default_short_or_template_key(secret):
    config = Settings(_env_file=None, ENVIRONMENT="production", SECRET_KEY=secret, PERSISTENT_UPLOADS=True)
    with pytest.raises(ValueError):
        config.validate_production_security()


def test_production_requires_persistent_storage():
    config = Settings(_env_file=None, ENVIRONMENT="production", SECRET_KEY="a" * 64, PERSISTENT_UPLOADS=False)
    with pytest.raises(ValueError):
        config.validate_production_security()


def test_runtime_database_matches_migration_settings():
    from app.core.config import settings
    from app.db.session import DATABASE_URL

    assert DATABASE_URL == settings.DATABASE_URL


def test_openapi_bearer_matches_json_login():
    from app.main import app

    schema = app.openapi()
    assert schema["components"]["securitySchemes"]["HTTPBearer"] == {"type": "http", "scheme": "bearer"}
    assert "application/json" in schema["paths"]["/api/v1/auth/login"]["post"]["requestBody"]["content"]
