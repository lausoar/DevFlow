"""
ChromaDB v2 HTTP 客户端（基于 httpx，避免依赖需要本地编译的 chromadb 包）
"""
from __future__ import annotations

import httpx
from app.core.config import get_settings

settings = get_settings()


class ChromaClient:
    """轻量级 ChromaDB v2 REST 客户端"""

    def __init__(self):
        self.base = f"http://{settings.chroma_host}:{settings.chroma_port}/api/v2"
        self.tenant = "default"
        self.database = "default"
        self.collection_name = settings.chroma_collection
        self._cid: str | None = None
        self._client: httpx.AsyncClient | None = None

    @property
    def client(self) -> httpx.AsyncClient:
        if self._client is None:
            self._client = httpx.AsyncClient(timeout=30)
        return self._client

    def _url(self, suffix: str) -> str:
        return f"{self.base}/tenants/{self.tenant}/databases/{self.database}{suffix}"

    async def ensure_collection(self) -> str:
        """确保数据库与集合存在，返回集合 ID"""
        if self._cid:
            return self._cid

        # 确保 database 存在（已存在则忽略报错）
        try:
            await self.client.post(
                f"{self.base}/tenants/{self.tenant}/databases",
                json={"name": self.database},
            )
        except Exception:
            pass

        # 创建 / 获取集合
        resp = await self.client.post(
            self._url("/collections"),
            json={
                "name": self.collection_name,
                "get_or_create": True,
                "metadata": {"hnsw:space": "cosine"},
            },
        )
        resp.raise_for_status()
        self._cid = resp.json()["id"]
        return self._cid

    async def add(self, ids, embeddings, documents, metadatas) -> None:
        cid = await self.ensure_collection()
        resp = await self.client.post(
            self._url(f"/collections/{cid}/add"),
            json={
                "ids": ids,
                "embeddings": embeddings,
                "documents": documents,
                "metadatas": metadatas,
            },
        )
        resp.raise_for_status()

    async def query(self, query_embeddings, n_results: int) -> dict:
        cid = await self.ensure_collection()
        resp = await self.client.post(
            self._url(f"/collections/{cid}/query"),
            json={
                "query_embeddings": query_embeddings,
                "n_results": n_results,
                "include": ["documents", "metadatas", "distances"],
            },
        )
        resp.raise_for_status()
        return resp.json()

    async def get(self, where: dict) -> dict:
        cid = await self.ensure_collection()
        resp = await self.client.post(
            self._url(f"/collections/{cid}/get"),
            # 注意：Chroma v2 的 include 枚举不含 "ids"（ids 始终返回），
            # 传入会导致 422，使删除旧分片的逻辑静默失效
            json={"where": where, "include": ["documents", "metadatas"]},
        )
        resp.raise_for_status()
        return resp.json()

    async def delete(self, ids: list[str]) -> None:
        cid = await self.ensure_collection()
        resp = await self.client.post(
            self._url(f"/collections/{cid}/delete"),
            json={"ids": ids},
        )
        resp.raise_for_status()
