# OopsCalendar Python 后端(FastAPI + uvicorn)
# DOCKER_HUB 默认同国内镜像,避免直连 Docker Hub 超时;海外可改为 docker.io
ARG DOCKER_HUB=docker.m.daocloud.io
FROM ${DOCKER_HUB}/library/python:3.12-slim-bookworm

WORKDIR /app

ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    HOST=0.0.0.0 \
    PORT=8080 \
    NEWS_DATA_DIR=/app/data \
    CORS_ALLOWED_ORIGINS=http://localhost,http://127.0.0.1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

COPY requirements.txt .
RUN pip install --no-cache-dir -i "${PIP_INDEX_URL}" -r requirements.txt

COPY main.py config.py ./
COPY app ./app

RUN mkdir -p /app/data

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/api/health', timeout=4)"

CMD ["python", "main.py"]
