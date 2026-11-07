"""Disposable Docker startup, real HTTP/AI/database and restart persistence gate."""
import io
import os
import secrets
import subprocess
import time
import httpx
from PIL import Image

def main():
    suffix=secrets.token_hex(5)
    network="saferoute-gate-"+suffix
    volume="saferoute-photos-"+suffix
    password=secrets.token_hex(20)
    env=os.environ.copy()
    env.update(POSTGRES_PASSWORD=password, POSTGRES_DB="saferoute_test_container",
               DATABASE_URL=f"postgresql+psycopg2://postgres:{password}@db:5432/saferoute_test_container",
               SECRET_KEY=secrets.token_hex(32), ENVIRONMENT="production",PERSISTENT_UPLOADS="true")
    ids=[]
    created_network=created_volume=False
    def run(args):
        result=subprocess.run(args,capture_output=True,text=True,env=env)
        if result.returncode:
            output=(result.stdout+result.stderr).replace(password,"[REDACTED]").replace(env["SECRET_KEY"],"[REDACTED]")
            raise RuntimeError(output)
        return result.stdout.strip()
    try:
        run(["docker","network","create",network]); created_network=True
        run(["docker","volume","create",volume]); created_volume=True
        db=run(["docker","run","-d","--rm","--network",network,"--network-alias","db",
                "-e","POSTGRES_PASSWORD","-e","POSTGRES_DB","postgis/postgis:16-3.4"]); ids.append(db)
        for _ in range(60):
            result=subprocess.run(["docker","exec",db,"pg_isready","-h","127.0.0.1","-U","postgres"],capture_output=True)
            if result.returncode==0: break
            time.sleep(1)
        run(["docker","run","--rm","--network",network,"-e","DATABASE_URL","saferoute-tv345:integration","alembic","upgrade","head"])
        app=run(["docker","run","-d","--rm","--network",network,"-p","127.0.0.1::8000",
                 "-v",volume+":/srv/uploads","-e","DATABASE_URL","-e","SECRET_KEY","-e","ENVIRONMENT",
                 "-e","PERSISTENT_UPLOADS","saferoute-tv345:integration"]);ids.append(app)
        port=run(["docker","port",app,"8000/tcp"]).rsplit(":",1)[1]
        with httpx.Client(base_url="http://127.0.0.1:"+port,timeout=30) as client:
            for _ in range(120):
                try:
                    if client.get("/health").status_code==200: break
                except httpx.HTTPError: pass
                time.sleep(1)
            ready=client.get("/ready")
            assert ready.status_code==200, ready.text
            assert ready.json()["ai_ready"] is True
            assert ready.json()["risk_ready"] is False
            credentials={"email":"container@example.com","password":"synthetic-password"}
            registered=client.post("/api/v1/auth/register",json=credentials)
            assert registered.status_code==200, registered.text
            headers={"Authorization":"Bearer "+registered.json()["access_token"]}
            buffer=io.BytesIO(); Image.new("RGB",(640,640),(128,128,128)).save(buffer,format="PNG")
            content=buffer.getvalue()
            uploaded=client.post("/api/v1/images",headers=headers,files={"file":("wrong.jpg",content,"image/png")})
            assert uploaded.status_code==200, uploaded.text
            image_url=uploaded.json()["url"]
            assert client.get(image_url).status_code==401
            assert client.get(image_url,headers=headers).content==content
            detected=client.post("/api/v1/ai/detect",files={"file":("road.png",content,"image/png")})
            assert detected.status_code==200, detected.text
            assert detected.json()["model_version"]=="SafeRoute-YOLOv12s-v1.1"
            created=client.post("/api/v1/reports",headers=headers,json={"latitude":10,"longitude":106,"category_user":"D40",
                "image_id":uploaded.json()["image_id"],"ai_detections":detected.json()["detections"]})
            assert created.status_code==201, created.text
            rid=created.json()["id"]
            assert client.post(f"/api/v1/reports/{rid}/votes",headers=headers,json={"vote_type":"SUPPORT"}).status_code==201
            decision={"status":"VERIFIED","reason":"Container integration fixture"}
            assert client.patch(f"/api/v1/reports/{rid}/status",headers=headers,json=decision).status_code==403
            run(["docker","exec",db,"psql","-U","postgres","-d","saferoute_test_container","-c",
                 "UPDATE users SET role='REVIEWER' WHERE email='container@example.com'"])
            assert client.patch(f"/api/v1/reports/{rid}/status",headers=headers,json=decision).status_code==200
            run(["docker","restart",app])
            port=run(["docker","port",app,"8000/tcp"]).rsplit(":",1)[1]
            client.base_url="http://127.0.0.1:"+port
            for _ in range(120):
                try:
                    if client.get("/ready").status_code==200: break
                except httpx.HTTPError: pass
                time.sleep(1)
            detail=client.get(f"/api/v1/reports/{rid}")
            assert detail.status_code==200 and detail.json()["status"]=="VERIFIED"
            assert len(detail.json()["audit_logs"])==1 and detail.json()["support_votes"]==1
            assert client.get(image_url,headers=headers).content==content
        print("PASS: non-root container startup, Alembic, readiness, real HTTP auth/image/AI/report/vote/review, process restart and volume persistence")
        print("IMAGE:",run(["docker","image","inspect","saferoute-tv345:integration","--format","{{.Id}}"] ))
        print("RISK_POLICY: absent, RISK_READY=false. GOOGLE/CLOUD: NOT_RUN")
    finally:
        for id_ in reversed(ids): subprocess.run(["docker","stop",id_],capture_output=True)
        if created_network: subprocess.run(["docker","network","rm",network],capture_output=True)
        if created_volume: subprocess.run(["docker","volume","rm",volume],capture_output=True)
        print("CLEANUP: only resources created by this run removed")

if __name__=="__main__": main()
