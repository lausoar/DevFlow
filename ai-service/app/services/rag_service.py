"""
RAG 服务 - 文档向量化、检索增强生成
"""
from typing import Optional
from app.services.chroma_client import ChromaClient
from app.services.embedding_service import embed, is_embedding_configured

chroma = ChromaClient()


class RagService:
    """RAG 检索增强生成服务"""

    async def index_document(self, request) -> dict:
        """
        索引文档到向量数据库
        将长文档切分为多个 chunk，生成嵌入向量后写入 ChromaDB
        """
        if not is_embedding_configured():
            raise RuntimeError("未配置嵌入向量服务（请在 .env 设置 EMBEDDING_API_KEY / EMBEDDING_BASE_URL / EMBEDDING_MODEL）")

        chunks = self._split_text(request.content, max_tokens=500)

        # 先清理该文档的旧分片，保证重复索引（文档更新）时幂等
        await self.delete_document(request.doc_id)

        if not chunks:
            return {"chunks": 0}

        doc_prefix = f"{request.doc_id}"
        ids = [f"{doc_prefix}_chunk_{i}" for i in range(len(chunks))]
        metadatas = [
            {
                "doc_id": request.doc_id,
                "title": request.title,
                "doc_type": request.doc_type,
                "chunk_index": i,
            }
            for i in range(len(chunks))
        ]

        # 生成嵌入向量（批处理）
        embeddings = await embed(chunks)

        await chroma.add(ids=ids, embeddings=embeddings, documents=chunks, metadatas=metadatas)
        return {"chunks": len(chunks)}

    async def search(self, request) -> list:
        """
        向量检索知识库
        嵌入服务或向量库不可用时返回空结果，由上层（RAG 问答/Agent）走纯 LLM 兜底
        """
        if not is_embedding_configured():
            return []
        try:
            query_embeddings = await embed([request.query])
            results = await chroma.query(query_embeddings=query_embeddings, n_results=request.top_k)
        except Exception:
            # 嵌入模型 404、向量库未启动等，均按"无结果"处理而非报错
            return []

        search_results = []
        if results and results.get("documents"):
            for doc, metadata, distance in zip(
                results["documents"][0],
                results["metadatas"][0],
                results["distances"][0],
            ):
                search_results.append({
                    "content": doc,
                    "score": 1 - distance,  # cosine distance -> similarity
                    "metadata": metadata,
                })
        return search_results

    async def query_with_context(self, request) -> dict:
        """
        RAG 问答 - 先检索相关知识，再交给 LLM 生成回答
        """
        from app.services.chat_service import ChatService
        from app.api.v1.chat import ChatRequest, ChatMessage

        search_results = await self.search(
            type("SearchReq", (), {
                "query": request.question,
                "project_id": request.project_id,
                "top_k": request.top_k,
                "doc_type": None,
            })()
        )

        context_parts = []
        for i, result in enumerate(search_results):
            context_parts.append(
                f"[文档 {i + 1}] {result['metadata'].get('title', '未知')}\n{result['content']}"
            )
        context = "\n\n---\n\n".join(context_parts) if context_parts else "未找到相关文档"

        augmented_question = f"""基于以下知识库内容回答问题。如果知识库中没有相关信息，请明确说明。

知识库内容：
{context}

问题：{request.question}

请用中文回答，保持简洁准确。"""

        chat_service = ChatService()
        chat_request = ChatRequest(
            messages=[ChatMessage(role="user", content=augmented_question)],
            temperature=0.3,
        )
        response = await chat_service.chat(chat_request)

        return {
            "answer": response.content,
            "sources": [
                {
                    "title": r["metadata"].get("title", ""),
                    "doc_id": r["metadata"].get("doc_id", ""),
                    "score": r["score"],
                }
                for r in search_results
            ],
        }

    async def delete_document(self, doc_id: str):
        """
        删除文档的所有索引
        集合为空时 Chroma 的 where 查询会返回 422，按"无索引"处理
        """
        try:
            results = await chroma.get(where={"doc_id": doc_id})
        except Exception:
            return
        if results and results.get("ids"):
            await chroma.delete(ids=results["ids"])

    def _split_text(self, text: str, max_tokens: int = 500) -> list[str]:
        """
        简单文本分块 - 按段落和长度切分
        """
        if not text or not text.strip():
            return []

        paragraphs = text.split("\n\n")
        chunks = []
        current_chunk = ""

        for para in paragraphs:
            para = para.strip()
            if not para:
                continue

            if len(para) > max_tokens * 4:
                if current_chunk:
                    chunks.append(current_chunk.strip())
                    current_chunk = ""
                for i in range(0, len(para), max_tokens * 3):
                    chunks.append(para[i:i + max_tokens * 4].strip())
                continue

            if len(current_chunk) + len(para) > max_tokens * 4:
                chunks.append(current_chunk.strip())
                current_chunk = para
            else:
                current_chunk += "\n\n" + para if current_chunk else para

        if current_chunk.strip():
            chunks.append(current_chunk.strip())

        return chunks
