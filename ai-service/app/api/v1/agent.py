"""
Agent 接口 - 工具调用、智能体
"""
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel
from typing import Optional
from app.services.agent_service import AgentService

router = APIRouter()
agent_router = router
agent_service = AgentService()


class AgentRequest(BaseModel):
    """Agent 运行请求"""
    query: str
    project_id: Optional[str] = None
    conversation_id: Optional[str] = None


class AgentStatusResponse(BaseModel):
    """Agent 状态"""
    available_tools: list[str]
    models: list[str]


@router.post("/run")
async def run_agent(request: AgentRequest, authorization: Optional[str] = Header(None)):
    """
    运行 Agent - 自动选择工具完成任务

    Agent 会分析用户意图，自动调用业务数据查询、知识库搜索、代码分析、任务生成等工具。
    请求头中的 Authorization（用户 JWT）会透传给业务查询工具，以该用户身份访问后端数据。
    """
    try:
        result = await agent_service.run(request, token=authorization)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Agent 运行失败: {str(e)}")


@router.get("/status")
async def agent_status():
    """
    获取 Agent 状态和可用工具
    """
    return AgentStatusResponse(
        available_tools=[
            "search_knowledge_base",
            "query_my_tasks",
            "summarize_my_bugs",
            "list_projects",
            "get_project_progress",
            "analyze_code",
            "generate_tasks",
        ],
        models=["gpt-4o", "gpt-4o-mini", "gpt-3.5-turbo"],
    )
