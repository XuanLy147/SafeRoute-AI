FROM python:3.11-slim
WORKDIR /srv/backend
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*
COPY backend/requirements.txt backend/requirements-ai.txt ./
RUN pip install --no-cache-dir torch==2.6.0 torchvision==0.21.0 --index-url https://download.pytorch.org/whl/cpu && pip install --no-cache-dir -r requirements.txt -r requirements-ai.txt
COPY backend/ ./
COPY ["model ai/weights/best.pt", "model ai/weights/best.pt.sha256", "/srv/ai/weights/"]
RUN cd /srv/ai/weights && sha256sum -c best.pt.sha256 && useradd --uid 10001 --create-home saferoute && mkdir -p /srv/uploads && chown -R saferoute:saferoute /srv/uploads
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1 PORT=8000 UPLOAD_DIR=/srv/uploads YOLO_WEIGHTS_PATH=/srv/ai/weights/best.pt YOLO_CONFIG_DIR=/tmp/ultralytics
USER saferoute
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4)"
CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8000} --workers 1"]
