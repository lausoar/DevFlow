"""
RAG 知识库接口 - 文档向量化、检索增强生成
"""
from fastapi import APIRouter, HTTPException, UploadFile, File
from pydantic import BaseModel
from typing import Optional
from app.services.rag_service import RagService

router = APIRouter()
rag_router = router
rag_service = RagService()


class IndexDocumentRequest(BaseModel):
    """文档索引请求"""
    doc_id: str
    title: str
    content: str
    doc_type: str = "doc"  # doc / wiki / report
    project_id: Optional[str] = None
    metadata: Optional[dict] = None


class SearchRequest(BaseModel):
    """知识库检索请求"""
    query: str
    project_id: Optional[str] = None
    top_k: int = 5
    doc_type: Optional[str] = None


class RagQueryRequest(BaseModel):
    """RAG 问答请求"""
    question: str
    project_id: Optional[str] = None
    top_k: int = 5


class SearchResult(BaseModel):
    """检索结果"""
    content: str
    score: float
    metadata: dict


@router.post("/index")
async def index_document(request: IndexDocumentRequest):
    """
    索引文档到向量数据库
    """
    try:
        result = await rag_service.index_document(request)
        return {"status": "ok", "doc_id": request.doc_id, "chunks": result["chunks"]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"索引失败: {str(e)}")


@router.post("/search", response_model=list[SearchResult])
async def search_knowledge(request: SearchRequest):
    """
    检索知识库
    """
    try:
        results = await rag_service.search(request)
        return results
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"检索失败: {str(e)}")


@router.post("/query")
async def rag_query(request: RagQueryRequest):
    """
    RAG 问答 - 结合检索和 LLM 生成
    """
    try:
        result = await rag_service.query_with_context(request)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"问答失败: {str(e)}")


@router.delete("/index/{doc_id}")
async def delete_document(doc_id: str):
    """
    删除文档索引
    """
    try:
        await rag_service.delete_document(doc_id)
        return {"status": "ok", "doc_id": doc_id}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"删除失败: {str(e)}")
