"""
RAG 知识库接口 - 文档向量化、检索增强生成

数据隔离：index 写入 enterprise_id/project_id；search/query 必须带范围，
没有任何范围参数时直接 400（宁可拒绝也不跨租户检索）。
"""
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel, Field
from typing import Optional
from app.services.rag_service import RagAuthorizationError, RagService, RagScopeError

router = APIRouter()
rag_router = router
rag_service = RagService()


class IndexDocumentRequest(BaseModel):
    """文档索引请求"""
    doc_id: str = Field(min_length=1, max_length=64)
    title: str = Field(default="", max_length=300)
    content: str = Field(default="", max_length=2_000_000)
    doc_type: str = "doc"  # doc / wiki / report
    project_id: Optional[str] = None
    enterprise_id: Optional[str] = None
    metadata: Optional[dict] = None


class SearchRequest(BaseModel):
    """知识库检索请求"""
    query: str = Field(min_length=1, max_length=2000)
    project_id: Optional[str] = None
    enterprise_id: Optional[str] = None
    top_k: int = Field(default=5, ge=1, le=50)
    doc_type: Optional[str] = None


class RagQueryRequest(BaseModel):
    """RAG 问答请求"""
    question: str = Field(min_length=1, max_length=4000)
    project_id: Optional[str] = None
    enterprise_id: Optional[str] = None
    top_k: int = Field(default=5, ge=1, le=50)


class SearchResult(BaseModel):
    """检索结果"""
    content: str
    score: float
    metadata: dict


@router.post("/index")
async def index_document(payload: IndexDocumentRequest, request: Request):
    """仅允许受信任的 Java 服务主体写入知识库。"""
    if not getattr(request.state, "is_service", False):
        raise HTTPException(status_code=403, detail="文档索引仅允许服务主体调用")
    if not payload.project_id and not payload.enterprise_id:
        raise HTTPException(status_code=400, detail="索引必须指定 project_id 或 enterprise_id（数据隔离）")
    try:
        result = await rag_service.index_document(payload)
        return {"status": "ok", "doc_id": payload.doc_id, "chunks": result["chunks"]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"索引失败: {str(e)}") from e


@router.post("/search", response_model=list[SearchResult])
async def search_knowledge(payload: SearchRequest, request: Request):
    """检索知识库，由 RagService 对当前用户或服务主体校验范围。"""
    try:
        return await rag_service.search(
            payload,
            user_token=getattr(request.state, "authorization", None),
            is_service=getattr(request.state, "is_service", False),
        )
    except RagScopeError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    except RagAuthorizationError as e:
        raise HTTPException(status_code=e.status_code, detail=e.detail) from e
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"检索失败: {str(e)}") from e


@router.post("/query")
async def rag_query(payload: RagQueryRequest, request: Request):
    """RAG 问答使用与直接检索相同的集中式范围授权。"""
    try:
        return await rag_service.query_with_context(
            payload,
            user_token=getattr(request.state, "authorization", None),
            is_service=getattr(request.state, "is_service", False),
        )
    except RagScopeError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    except RagAuthorizationError as e:
        raise HTTPException(status_code=e.status_code, detail=e.detail) from e
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"问答失败: {str(e)}") from e


@router.delete("/index/{doc_id}")
async def delete_document(
    doc_id: str,
    request: Request,
    project_id: Optional[str] = None,
    enterprise_id: Optional[str] = None,
):
    """仅允许受信任的 Java 服务主体删除指定租户范围内的索引。"""
    if not getattr(request.state, "is_service", False):
        raise HTTPException(status_code=403, detail="索引删除仅允许服务主体调用")
    if not project_id and not enterprise_id:
        raise HTTPException(status_code=400, detail="索引删除必须指定 project_id 或 enterprise_id")
    try:
        await rag_service.delete_document(
            doc_id,
            project_id=project_id,
            enterprise_id=enterprise_id,
        )
        return {"status": "ok", "doc_id": doc_id}
    except RagScopeError as e:
        raise HTTPException(status_code=400, detail=str(e)) from e
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"删除失败: {str(e)}") from e
