"""
DevFlow Java 后端数据客户端 - 供 Agent 工具查询业务数据

透传当前用户的 JWT（前端请求 AI 服务时已携带 Authorization 头），
以该用户身份调用 Java 后端接口，保证数据权限与用户一致。
"""
from __future__ import annotations

import httpx

from app.core.config import get_settings

settings = get_settings()


class BackendClient:
    """DevFlow Java 后端 API 客户端"""

    def __init__(self, token: str | None = None):
        self.base_url = settings.backend_api_url.rstrip("/")
        self.token = (token or "").strip()

    def _headers(self) -> dict:
        if not self.token:
            return {}
        if self.token.lower().startswith("bearer "):
            return {"Authorization": self.token}
        return {"Authorization": f"Bearer {self.token}"}

    async def _request(self, method: str, path: str, **kwargs):
        """请求后端并解包 R 统一响应结构（{code, message, data}）"""
        url = f"{self.base_url}{path}"
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.request(method, url, headers=self._headers(), **kwargs)
        resp.raise_for_status()
        body = resp.json()
        if isinstance(body, dict) and "code" in body:
            if body.get("code") != 200:
                raise RuntimeError(f"后端接口错误({body.get('code')}): {body.get('message')}")
            return body.get("data")
        return body

    @staticmethod
    def _unwrap_page(data) -> list[dict]:
        """兼容 Page（{records: [...]}）与 List 两种返回"""
        if isinstance(data, dict) and "records" in data:
            data = data["records"]
        return data or []

    # ---------- 业务数据查询（Agent 工具用） ----------

    async def list_my_tasks(self, limit: int = 50) -> list[dict]:
        """当前用户被分配的任务（跨项目）"""
        data = await self._request("GET", "/tasks/my")
        return self._unwrap_page(data)[:limit]

    async def list_my_bugs(self, limit: int = 50) -> list[dict]:
        """当前用户负责的 Bug（跨项目）"""
        data = await self._request("GET", "/bugs/my")
        return self._unwrap_page(data)[:limit]

    async def list_projects(self, limit: int = 20) -> list[dict]:
        """项目列表"""
        data = await self._request("GET", "/projects")
        return self._unwrap_page(data)[:limit]

    async def get_project_dashboard(self, project_id: str) -> dict:
        """项目仪表盘：任务完成率、Bug 统计、当前 Sprint 进展"""
        return await self._request("GET", f"/projects/{project_id}/dashboard")
