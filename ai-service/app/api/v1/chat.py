"""
AI 聊天接口 - 支持多轮对话、流式输出
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional
from app.services.chat_service import ChatService

router = APIRouter()
chat_service = ChatService()


class ChatMessage(BaseModel):
    """聊天消息"""
    role: str  # user / assistant / system
    content: str


class ChatRequest(BaseModel):
    """聊天请求"""
    messages: list[ChatMessage]
    model: Optional[str] = None
    provider: Optional[str] = None  # 指定厂商: openai/deepseek/qwen/kimi/volcano/glm，缺省用 AI_PROVIDER
    temperature: float = 0.7
    max_tokens: int = 2000
    stream: bool = False
    project_id: Optional[str] = None  # 关联项目上下文


class ChatResponse(BaseModel):
    """聊天响应"""
    id: str
    content: str
    model: str
    usage: dict


@router.post("/completions", response_model=ChatResponse)
async def chat_completions(request: ChatRequest):
    """
    AI 聊天接口 - 支持多轮对话
    """
    try:
        result = await chat_service.chat(request)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"AI 服务异常: {str(e)}")


@router.post("/stream")
async def chat_stream(request: ChatRequest):
    """
    AI 流式聊天 - SSE 输出
    """
    from fastapi.responses import StreamingResponse

    async def generate():
        async for chunk in chat_service.chat_stream(request):
            yield f"data: {chunk}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(generate(), media_type="text/event-stream")
