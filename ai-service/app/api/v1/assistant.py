"""
AI 助手接口 - 日报、总结、代码审查、任务生成等
"""
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from typing import Optional
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
    content: str
    summary_type: str = "general"  # general / meeting / sprint / project


class TaskGenerateRequest(BaseModel):
    """AI 生成任务请求"""
    description: str
    project_id: Optional[str] = None


class CodeReviewRequest(BaseModel):
    """AI 代码审查请求"""
    code: str
    language: str = "typescript"
    context: Optional[str] = None


class BugAnalysisRequest(BaseModel):
    """AI 分析 Bug 请求"""
    title: str
    description: str
    logs: Optional[str] = None
    environment: Optional[str] = None


@router.post("/daily-report")
async def generate_daily_report(request: DailyReportRequest):
    """
    AI 生成日报
    """
    try:
        result = await assistant_service.generate_daily_report(request)
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
