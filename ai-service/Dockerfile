# syntax=docker/dockerfile:1
# ============================================
# DevFlow AI 服务镜像（FastAPI + uvicorn）
# ============================================
# 注意：sentence-transformers / chromadb 会在首次使用时下载模型与数据，
# 需要挂载持久化卷，否则每次重建都要重新下载（BAAI/bge-small-zh-v1.5 约 100MB）。

FROM python:3.11-slim AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TZ=Asia/Shanghai \
    HF_HOME=/data/hf

WORKDIR /app

# 构建期依赖（编译 sentence-transformers 等的轮子时偶尔需要）
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential curl \
    && rm -rf /var/lib/apt/lists/*

COPY pyproject.toml ./
RUN pip install --upgrade pip && pip install .

COPY app ./app

# 非 root 运行；模型与向量缓存挂到 /data
RUN useradd -m -u 1001 devflow \
    && mkdir -p /data/hf \
    && chown -R devflow:devflow /app /data
USER devflow

ENV PYTHONPATH=/app

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8000/health || exit 1

# 容器内需监听 0.0.0.0（默认 127.0.0.1 只适合本机开发）
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers"]
