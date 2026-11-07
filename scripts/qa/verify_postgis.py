"""Verify an integrated backend on a disposable real PostGIS container.

Run with that snapshot's Python environment. Does not use Compose, init_db,
existing databases, local credentials, mounts or public interfaces. Removes only
the newly created container by its returned ID. No production risk policy set.
"""

import argparse
import os
import re
from pathlib import Path
import secrets
import subprocess
import sys
import time


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend", type=Path, required=True)
    parser.add_argument("--runxfail", action="store_true", help="Run legacy xfail assertions normally for an integration gate")
    args = parser.parse_args()
    backend = args.backend.resolve(strict=True)
    if not (backend / "migrations/versions/0001_initial_schema.py").is_file():
        raise SystemExit("Requires the actual integrated TV3 backend")
    password = secrets.token_hex(24)
    container = None
    env = os.environ.copy()
    env["POSTGRES_PASSWORD"] = password
    database = "saferoute_test_tv4_" + secrets.token_hex(4)

    def run(command, **kwargs):
        result = subprocess.run(command, capture_output=True, text=True, **kwargs)
        output = (result.stdout + result.stderr).replace(password, "[REDACTED]")
        output = re.sub(r"eyJ[A-Za-z0-9_-]+\.eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", "[REDACTED_TEST_JWT]", output)
        if result.returncode:
            raise RuntimeError(output)
        return output

    try:
        container = run([
            "docker", "run", "--detach", "--rm", "--label", "saferoute.owner=tv4-verification",
            "--publish", "127.0.0.1::5432", "--env", "POSTGRES_PASSWORD",
            "--env", f"POSTGRES_DB={database}", "postgis/postgis:16-3.4",
        ], env=env).strip()
        port = run(["docker", "port", container, "5432/tcp"]).strip().rsplit(":", 1)[1]
        url = f"postgresql+psycopg2://postgres:{password}@127.0.0.1:{port}/{database}"
        env["DATABASE_URL"] = url
        env["SAFEROUTE_TEST_DATABASE_URL"] = url
        os.environ["DATABASE_URL"] = url
        sys.path.insert(0, str(backend))
        from sqlalchemy import create_engine, inspect, text
        from alembic.autogenerate import compare_metadata
        from alembic.migration import MigrationContext
        from app.db.session import Base
        import app.models.entities  # noqa: F401

        engine = create_engine(url)
        for attempt in range(60):
            try:
                with engine.connect() as conn:
                    conn.execute(text("SELECT 1"))
                break
            except Exception:
                if attempt == 59:
                    raise RuntimeError("Disposable database readiness timeout") from None
                time.sleep(1)

        print("IMAGE:", run(["docker", "inspect", "--format", "{{.Image}}", container]).strip(), flush=True)
        with engine.connect() as conn:
            print("POSTGRES:", conn.scalar(text("SHOW server_version")), flush=True)
            print("POSTGIS:", conn.scalar(text("SELECT PostGIS_Full_Version()")), flush=True)
            assert not (set(inspect(conn).get_table_names()) & set(Base.metadata.tables))

        def migrate(target, direction="upgrade"):
            print(run([sys.executable, "-m", "alembic", direction, target], cwd=backend, env=env), flush=True)

        def verify_schema():
            with engine.connect() as conn:
                # The image includes tiger/topology in search_path. Compare the
                # application's public schema, not extension-owned relations.
                conn.execute(text("SET LOCAL search_path TO public"))
                inspector = inspect(conn)
                tables = set(Base.metadata.tables)
                assert len(tables) == 8 and tables <= set(inspector.get_table_names())
                assert conn.scalar(text("SELECT version_num FROM alembic_version")) == "0001_initial_schema"
                enums = {entry["name"]: entry["labels"] for entry in inspector.get_enums()}
                assert enums["report_status_enum"] == ["PENDING_VERIFICATION", "VERIFIED", "REJECTED"]
                assert enums["user_role_enum"] == ["USER", "REVIEWER", "ADMIN"]
                assert enums["vote_type_enum"] == ["SUPPORT", "OPPOSE", "UNSURE"]
                foreign_keys = sum(len(inspector.get_foreign_keys(table)) for table in tables)
                assert foreign_keys == 8
                idx = conn.scalar(text("SELECT indexdef FROM pg_indexes WHERE schemaname='public' AND indexname='idx_reports_location'"))
                assert "USING gist (location)" in idx
                typ = conn.scalar(text("SELECT format_type(a.atttypid,a.atttypmod) FROM pg_attribute a WHERE a.attrelid='reports'::regclass AND a.attname='location'"))
                assert typ.lower() == "geography(point,4326)"
                context = MigrationContext.configure(conn, opts={
                    "include_object": lambda obj, name, kind, reflected, compare_to:
                        not (kind == "table" and name == "spatial_ref_sys"),
                    "compare_type": True, "compare_server_default": True,
                })
                differences = compare_metadata(context, Base.metadata)
                assert not differences, repr(differences)
                print("SCHEMA_PASS: 8 model tables, 8 FKs, 3 enums, geography POINT/4326, GiST; metadata diff=[]", flush=True)

        migrate("head")
        verify_schema()
        migrate("base", "downgrade")
        with engine.connect() as conn:
            assert not (set(inspect(conn).get_table_names()) & set(Base.metadata.tables))
            assert not ({entry["name"] for entry in inspect(conn).get_enums()} &
                        {"report_status_enum", "user_role_enum", "vote_type_enum"})
            assert conn.scalar(text("SELECT count(*) FROM alembic_version")) == 0
            assert conn.scalar(text("SELECT extversion FROM pg_extension WHERE extname='postgis'"))
        print("ROLLBACK_PASS: model tables/enums removed, Alembic revision empty, PostGIS retained", flush=True)
        migrate("head")
        verify_schema()
        pytest_args = ["--runxfail"] if args.runxfail else []
        print(run([sys.executable, "-m", "pytest", "tests", "-q", "-ra", *pytest_args], cwd=backend, env=env), flush=True)
        engine.dispose()
        print("VERIFICATION_COMPLETE: real PostgreSQL/PostGIS; synthetic policy/coordinates only", flush=True)
    finally:
        if container:
            print(run(["docker", "stop", container]).strip(), flush=True)
            print("CLEANUP: disposable container stopped/auto-removed", flush=True)


if __name__ == "__main__":
    main()
