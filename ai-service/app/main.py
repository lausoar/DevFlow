"""
DevFlow AI Service - FastAPI 应用入口
"""
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.auth import require_user
from app.core.config import get_settings
from app.api.v1 import router as v1_router

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期：启动 / 关闭事件"""
    print("[STARTUP] DevFlow AI Service starting...")
    if not (settings.jwt_secret or "").strip():
        print("[WARN] JWT_SECRET 未配置：所有 /api/v1 接口都会返回 503（认证 fail-closed）")
    if not (settings.ai_service_token or "").strip():
        print("[WARN] AI_SERVICE_TOKEN 未配置：Java 后端同步知识库将被拒绝（401）")
    yield
    print("👋 DevFlow AI Service 已关闭")


app = FastAPI(
    title=settings.app_name,
    description="DevFlow 企业级 AI 智能服务 - RAG / Agent / LLM / Code Review",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# CORS：只允许配置的前端来源（不再 allow_origins=["*"]，否则任意网页可跨域消耗 LLM 额度）
_allowed_origins = [
    origin.strip()
    for origin in (settings.cors_allow_origins or "").split(",")
    if origin.strip()
]
app.add_middleware(
    CORSMiddleware,
    allow_origins=_allowed_origins,
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type", "X-Service-Token"],
)

# 注册路由：整组 /api/v1 统一要求认证
app.include_router(v1_router, prefix=settings.api_prefix, dependencies=[Depends(require_user)])


@app.get("/health")
async def health_check():
    """健康检查（无需认证，供负载均衡探活）"""
    return {"status": "ok", "service": "devflow-ai"}


if __name__ == "__main__":
    import uvicorn

    # 默认只监听回环地址；需要对外暴露时显式设置 AI_HOST=0.0.0.0，并确保前面有网关鉴权
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=False)
