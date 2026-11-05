"""
嵌入向量服务
支持两种模式（.env 的 EMBEDDING_PROVIDER）：
- api（默认）：调用 OpenAI 兼容的 embeddings 接口（OpenAI / 通义 / 智谱等）
- local：使用 sentence-transformers 本地模型，零 API 成本
  （中文推荐 BAAI/bge-small-zh-v1.5；模型经 HF 镜像下载，已入库向量切换模型后需重建）
"""
from __future__ import annotations

import asyncio
import os
import threading

from openai import AsyncOpenAI
from app.core.config import get_settings

settings = get_settings()

_client = AsyncOpenAI(
    api_key=settings.embedding_api_key or "sk-placeholder",
    base_url=settings.embedding_base_url,
)

_local_model = None
_local_lock = threading.Lock()


def _get_local_model():
    """懒加载本地嵌入模型（在线程池中调用，避免阻塞事件循环）"""
    global _local_model
    if _local_model is None:
        with _local_lock:
            if _local_model is None:
                # 国内网络默认走 HF 镜像
                os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
                from sentence_transformers import SentenceTransformer

                _local_model = SentenceTransformer(settings.embedding_local_model)
    return _local_model


async def embed(texts: list[str]) -> list[list[float]]:
    """
    批量生成文本嵌入向量
    - local 模式：sentence-transformers 本地推理
    - api 模式：OpenAI 兼容接口（OPENAI / 通义 / 智谱 等，需在 .env 配置 EMBEDDING_*）
    """
    if settings.embedding_provider == "local":
        model = await asyncio.to_thread(_get_local_model)
        vectors = await asyncio.to_thread(model.encode, texts, normalize_embeddings=True)
        return [v.tolist() for v in vectors]

    resp = await _client.embeddings.create(
        model=settings.embedding_model,
        input=texts,
    )
    return [item.embedding for item in resp.data]


def is_embedding_configured() -> bool:
    """是否可生成嵌入：local 模式始终可用；api 模式要求配置了有效 Key"""
    if settings.embedding_provider == "local":
        return True
    key = (settings.embedding_api_key or "").strip()
    return bool(key) and key not in ("sk-placeholder", "sk-your-api-key-here")
