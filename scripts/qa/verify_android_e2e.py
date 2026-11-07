"""Opt-in Android UI -> real isolated Docker FastAPI/PostGIS/YOLO gate.

Requires an already booted emulator, Android SDK/JDK, and the built
saferoute-tv345:integration image. No shared database or demo seed is used.
Reviewer is registered through HTTP and granted a real DB role only in this
disposable test database. No token, password or uploaded image is printed.
"""
import os
import secrets
import subprocess
import time
from pathlib import Path

import httpx


def main():
    root = Path(__file__).resolve().parents[2]
    sdk = Path(os.environ["ANDROID_HOME"])
    adb = str(sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))
    env = os.environ.copy()
    password = secrets.token_hex(24)
    secret = secrets.token_hex(32)
    env.update(POSTGRES_PASSWORD=password, POSTGRES_DB="saferoute_android_test",
               DATABASE_URL=f"postgresql+psycopg2://postgres:{password}@db:5432/saferoute_android_test",
               SECRET_KEY=secret, ENVIRONMENT="production", PERSISTENT_UPLOADS="true")
    from dotenv import dotenv_values
    key = dotenv_values(root / "backend/.env").get("GOOGLE_ROUTES_API_KEY") or env.get("GOOGLE_ROUTES_API_KEY")
    if key:
        env["GOOGLE_ROUTES_API_KEY"] = key
    maps_key = ""
    properties = root / "android/local.properties"
    if properties.exists():
        for line in properties.read_text(encoding="utf-8-sig").splitlines():
            if line.startswith("MAPS_API_KEY="):
                maps_key = line.split("=", 1)[1].strip()
    maps_key = env.get("MAPS_API_KEY") or maps_key
    suffix = secrets.token_hex(5)
    network = "saferoute-android-" + suffix
    volume = "saferoute-android-photos-" + suffix
    ids = []
    created_network = created_volume = False

    def run(command):
        result = subprocess.run(command, cwd=root, env=env, text=True, check=False, capture_output=True, encoding="utf-8", errors="replace")
        output = result.stdout + result.stderr
        for value in (password, secret, key, maps_key):
            if value:
                output = output.replace(value, "[REDACTED]")
        if result.returncode:
            raise RuntimeError(output)
        return result.stdout.strip()

    try:
        assert "device" in run([adb, "get-state"]), "Boot an emulator first"
        run(["docker", "network", "create", network]); created_network = True
        run(["docker", "volume", "create", volume]); created_volume = True
        db = run(["docker", "run", "-d", "--rm", "--network", network, "--network-alias", "db", "-e", "POSTGRES_PASSWORD", "-e", "POSTGRES_DB", "postgis/postgis:16-3.4"]); ids.append(db)
        for _ in range(60):
            if subprocess.run(["docker", "exec", db, "pg_isready", "-h", "127.0.0.1", "-U", "postgres"], check=False, capture_output=True).returncode == 0:
                break
            time.sleep(1)
        run(["docker", "run", "--rm", "--network", network, "-e", "DATABASE_URL", "saferoute-tv345:integration", "alembic", "upgrade", "head"])
        command = ["docker", "run", "-d", "--rm", "--network", network, "-p", "127.0.0.1::8000", "-v", volume + ":/srv/uploads"]
        for name in ("DATABASE_URL", "SECRET_KEY", "ENVIRONMENT", "PERSISTENT_UPLOADS", "GOOGLE_ROUTES_API_KEY"):
            if name in env:
                command += ["-e", name]
        app = run(command + ["saferoute-tv345:integration"]); ids.append(app)
        port = run(["docker", "port", app, "8000/tcp"]).rsplit(":", 1)[1]
        with httpx.Client(base_url="http://127.0.0.1:" + port, timeout=35) as client:
            for _ in range(120):
                try:
                    if client.get("/ready").status_code == 200:
                        break
                except httpx.HTTPError:
                    pass
                time.sleep(1)
            assert client.get("/ready").status_code == 200
            assert client.post("/api/v1/auth/register", json={"email": "android-review@example.com", "password": "local-device-test-password"}).status_code == 200
            run(["docker", "exec", db, "psql", "-U", "postgres", "-d", "saferoute_android_test", "-c", "UPDATE users SET role='REVIEWER' WHERE email='android-review@example.com'"])
            gradle = str(root / "android" / ("gradlew.bat" if os.name == "nt" else "gradlew"))
            run([gradle, "-p", str(root / "android"), "-PAPI_BASE_URL=http://10.0.2.2:" + port + "/", ":app:assembleDebug", ":app:assembleDebugAndroidTest", "--console=plain", "--no-daemon", "--max-workers=2", "-Dorg.gradle.jvmargs=-Xmx1024m"])
            run([adb, "install", "-r", str(root / "android/app/build/outputs/apk/debug/app-debug.apk")])
            run([adb, "install", "-r", str(root / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")])
            output = run([adb, "shell", "am", "instrument", "-w", "-r", "-e", "class", "com.comeforfood.saferoute.LiveBackendDeviceTest", "-e", "backendE2E", "true", "-e", "liveRoutes", "true" if key else "false", "-e", "liveMaps", "true" if maps_key else "false", "com.comeforfood.saferoute.test/androidx.test.runner.AndroidJUnitRunner"])
            print(output, flush=True)
            assert "OK (1 test)" in output and "FAILURES" not in output, "Android UI gate failed"
            rows = client.get("/api/v1/reports").json()
            assert len(rows) == 1 and rows[0]["status"] == "VERIFIED"
            rid = rows[0]["id"]
            assert client.get(f"/api/v1/reports/{rid}").json()["support_votes"] == 1
            run(["docker", "restart", app])
            port = run(["docker", "port", app, "8000/tcp"]).rsplit(":", 1)[1]
            client.base_url = "http://127.0.0.1:" + port
            for _ in range(120):
                try:
                    if client.get("/ready").status_code == 200:
                        break
                except httpx.HTTPError:
                    pass
                time.sleep(1)
            detail = client.get(f"/api/v1/reports/{rid}").json()
            assert detail["status"] == "VERIFIED" and len(detail["audit_logs"]) == 1
            print("PASS: Android UI register -> photo/AI/manual -> create -> vote -> reviewer login/review -> map; backend restart persistence", flush=True)
            print("GOOGLE_MAPS_LIVE:", "PASS" if maps_key else "NOT_RUN (key not configured)", flush=True)
            print("GOOGLE_ROUTES_LIVE:", "PASS" if key else "NOT_RUN (key not configured)", flush=True)
            print("DEVICE:", run([adb, "shell", "getprop", "ro.build.version.release"]), run([adb, "shell", "getprop", "ro.product.model"]), flush=True)
    finally:
        for container in reversed(ids):
            subprocess.run(["docker", "stop", container], check=False, capture_output=True)
        if created_network:
            subprocess.run(["docker", "network", "rm", network], check=False, capture_output=True)
        if created_volume:
            subprocess.run(["docker", "volume", "rm", volume], check=False, capture_output=True)
        print("CLEANUP: only disposable Android E2E resources removed", flush=True)


if __name__ == "__main__":
    main()
