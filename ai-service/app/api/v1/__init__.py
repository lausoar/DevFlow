"""
DevFlow AI Service - API v1 路由
"""
from fastapi import APIRouter

from app.api.v1.chat import router as chat_router

router = APIRouter()

# 核心聊天路由（仅依赖 openai，始终注册）
router.include_router(chat_router, prefix="/chat", tags=["AI 聊天"])


def _safe_include(module_path: str, attr: str, prefix: str, tags: list) -> None:
    """容错注册路由：依赖（chromadb / langchain / celery 等）未安装时跳过，避免影响核心聊天。"""
    try:
        module = __import__(module_path, fromlist=[attr])
        sub_router = getattr(module, attr)
        router.include_router(sub_router, prefix=prefix, tags=tags)
        print(f"[OK] registered router: {prefix}")
    except Exception as e:  # noqa: BLE001
        print(f"[WARN] skipped router {prefix} (missing dependency): {e}")


_safe_include("app.api.v1.rag", "rag_router", "/rag", ["RAG 知识库"])
_safe_include("app.api.v1.assistant", "assistant_router", "/assistant", ["AI 助手"])
_safe_include("app.api.v1.agent", "agent_router", "/agent", ["AI Agent"])
