"""
AI 助手接口 - 日报、总结、代码审查、任务生成等
"""
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field
from typing import Optional
from app.core.auth import require_user_token
from app.services.assistant_service import AssistantService

router = APIRouter()
assistant_router = router
assistant_service = AssistantService()


class DailyReportRequest(BaseModel):
    """日报生成请求"""
    user_id: str
    project_id: Optional[str] = None
    date: Optional[str] = None  # YYYY-MM-DD


class SummaryRequest(BaseModel):
    """内容总结请求"""
    content: str = Field(max_length=200_000)
    summary_type: str = "general"  # general / meeting / sprint / project


class TaskGenerateRequest(BaseModel):
    """AI 生成任务请求"""
    description: str = Field(max_length=20_000)
    project_id: Optional[str] = None


class CodeReviewRequest(BaseModel):
    """AI 代码审查请求"""
    code: str = Field(max_length=200_000)
    language: str = "typescript"
    context: Optional[str] = None


class BugAnalysisRequest(BaseModel):
    """AI 分析 Bug 请求"""
    title: str = Field(max_length=300)
    description: str = Field(max_length=20_000)
    logs: Optional[str] = Field(default=None, max_length=200_000)
    environment: Optional[str] = None


@router.post("/daily-report")
async def generate_daily_report(
    request: DailyReportRequest,
    user_token: str = Depends(require_user_token),
):
    """
    AI 生成日报（以当前用户身份查询真实任务/Bug 数据，需要用户 JWT）
    """
    try:
        result = await assistant_service.generate_daily_report(request, token=user_token)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"生成日报失败: {str(e)}")


@router.post("/summary")
async def generate_summary(request: SummaryRequest):
    """
    AI 总结内容
    """
    try:
        result = await assistant_service.generate_summary(request)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"生成总结失败: {str(e)}")


@router.post("/generate-tasks")
async def generate_tasks(request: TaskGenerateRequest):
    """
    AI 拆解/生成任务
    """
    try:
        result = await assistant_service.generate_tasks(request)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"生成任务失败: {str(e)}")


@router.post("/code-review")
async def code_review(request: CodeReviewRequest):
    """
    AI 代码审查
    """
    try:
        result = await assistant_service.code_review(request)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"代码审查失败: {str(e)}")


@router.post("/analyze-bug")
async def analyze_bug(request: BugAnalysisRequest):
    """
    AI 分析 Bug
    """
    try:
        result = await assistant_service.analyze_bug(request)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"分析 Bug 失败: {str(e)}")
