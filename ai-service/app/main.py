"""
DevFlow AI Service - FastAPI 应用入口
"""
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import get_settings
from app.api.v1 import router as v1_router

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期：启动 / 关闭事件"""
    print("[STARTUP] DevFlow AI Service starting...")
    print(f"[DOCS] http://localhost:8000/docs")
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

# CORS 配置 - 允许前端和后端访问
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册路由
app.include_router(v1_router, prefix=settings.api_prefix)


@app.get("/health")
async def health_check():
    """健康检查"""
    return {"status": "ok", "service": "devflow-ai"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=False)
