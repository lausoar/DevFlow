"""
Agent 服务 - 工具调用、多步骤推理
支持 Function Calling，可串联多个工具完成复杂任务
"""
import json
from typing import Optional
from app.services.chat_service import ChatService
from app.services.rag_service import RagService
from app.services.backend_client import BackendClient
from app.api.v1.chat import ChatRequest, ChatMessage


# 定义可用工具（Function Calling）
AVAILABLE_TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "search_knowledge_base",
            "description": "搜索企业知识库，获取相关文档内容",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "搜索关键词",
                    },
                    "top_k": {
                        "type": "integer",
                        "description": "返回结果数量，默认 3",
                    },
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "query_my_tasks",
            "description": "查询当前用户在各项目中被分配的任务列表，包含标题、状态、优先级、截止时间",
            "parameters": {
                "type": "object",
                "properties": {},
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "summarize_my_bugs",
            "description": "查询当前用户负责的 Bug 列表，包含标题、状态、严重程度",
            "parameters": {
                "type": "object",
                "properties": {},
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "list_projects",
            "description": "查询项目列表，返回项目ID、名称、状态，用于后续查询项目进度",
            "parameters": {
                "type": "object",
                "properties": {},
                "required": [],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_project_progress",
            "description": "查询项目整体进度：任务完成率、Bug 统计、当前 Sprint 进展。需要 project_id；不确定时先调用 list_projects",
            "parameters": {
                "type": "object",
                "properties": {
                    "project_id": {
                        "type": "string",
                        "description": "项目ID",
                    },
                },
                "required": ["project_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "analyze_code",
            "description": "分析代码片段，返回代码质量评估和改进建议",
            "parameters": {
                "type": "object",
                "properties": {
                    "code": {
                        "type": "string",
                        "description": "要分析的代码",
                    },
                    "language": {
                        "type": "string",
                        "description": "编程语言",
                    },
                },
                "required": ["code", "language"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "generate_tasks",
            "description": "将需求描述拆解为具体的可执行任务列表",
            "parameters": {
                "type": "object",
                "properties": {
                    "description": {
                        "type": "string",
                        "description": "需求描述",
                    },
                },
                "required": ["description"],
            },
        },
    },
]


class AgentService:
    """AI Agent - 工具调用 + 多步骤推理"""

    def __init__(self):
        self.chat_service = ChatService()
        self.rag_service = RagService()

    async def run(self, request, token: Optional[str] = None) -> dict:
        """
        运行 Agent - 支持多轮工具调用

        Agent 会分析用户意图，自动选择合适工具，串联完成复杂任务。
        token 为当前用户的 JWT，业务查询工具会以该用户身份访问后端数据。
        """
        messages = [
            ChatMessage(
                role="system",
                content="""你是 DevFlow 的 AI Agent 助手。你可以帮助用户：
1. 查询真实业务数据：我的任务、我的 Bug、项目列表、项目进度
2. 搜索企业知识库获取信息
3. 分析代码质量和潜在问题
4. 将需求拆解为任务

当用户询问"我的任务"、"项目进展"、"有哪些 Bug"这类问题时，优先调用对应的业务查询工具，
基于工具返回的真实数据回答，不要编造。数据为空时如实说明。
请根据用户的问题，选择最合适的工具来完成。如果需要多步骤，请依次执行。
回答请使用中文，保持简洁准确。""",
            )
        ]

        # 添加用户消息
        messages.append(ChatMessage(role="user", content=request.query))

        # 第一轮：让模型选择工具
        response = await self.chat_service.chat_with_tools(
            messages=messages,
            tools=AVAILABLE_TOOLS,
        )

        # 处理工具调用
        tool_results = []
        if response.tool_calls:
            for tool_call in response.tool_calls:
                result = await self._execute_tool(tool_call, token)
                tool_results.append({
                    "tool": tool_call.name,
                    "args": tool_call.arguments,
                    "result": result,
                })

            # 第二轮：让模型基于工具结果生成最终回答
            messages.append(ChatMessage(role="assistant", content=response.content or ""))
            for tr in tool_results:
                messages.append(
                    ChatMessage(
                        role="user",
                        content=f"工具 {tr['tool']} 的执行结果：\n{json.dumps(tr['result'], ensure_ascii=False, indent=2)}",
                    )
                )

            final_response = await self.chat_service.chat(
                ChatRequest(messages=messages, temperature=0.4)
            )

            return {
                "answer": final_response.content,
                "tool_calls": tool_results,
                "model": final_response.model,
            }

        # 没有工具调用，直接返回
        return {
            "answer": response.content,
            "tool_calls": [],
            "model": response.model,
        }

    async def _execute_tool(self, tool_call, token: Optional[str] = None) -> dict:
        """执行工具调用"""
        args = tool_call.arguments

        if tool_call.name == "search_knowledge_base":
            # 搜索知识库
            results = await self.rag_service.search(
                type("SearchReq", (), {
                    "query": args.get("query", ""),
                    "project_id": None,
                    "top_k": args.get("top_k", 3),
                    "doc_type": None,
                })()
            )
            return {"results": results}

        elif tool_call.name in ("query_my_tasks", "summarize_my_bugs", "list_projects", "get_project_progress"):
            # 业务数据查询 - 以当前用户身份访问 Java 后端
            backend = BackendClient(token)
            try:
                if tool_call.name == "query_my_tasks":
                    tasks = await backend.list_my_tasks()
                    return {
                        "total": len(tasks),
                        "tasks": [
                            {
                                "title": t.get("title"),
                                "status": t.get("status"),
                                "priority": t.get("priority"),
                                "due_date": t.get("dueDate"),
                                "project_id": t.get("projectId"),
                            }
                            for t in tasks
                        ],
                    }
                elif tool_call.name == "summarize_my_bugs":
                    bugs = await backend.list_my_bugs()
                    # 按状态聚合，便于模型给出汇总而非流水账
                    by_status: dict[str, int] = {}
                    for b in bugs:
                        status = b.get("status") or "UNKNOWN"
                        by_status[status] = by_status.get(status, 0) + 1
                    return {
                        "total": len(bugs),
                        "by_status": by_status,
                        "bugs": [
                            {
                                "title": b.get("title"),
                                "status": b.get("status"),
                                "severity": b.get("severity"),
                                "priority": b.get("priority"),
                                "project_id": b.get("projectId"),
                            }
                            for b in bugs
                        ],
                    }
                elif tool_call.name == "list_projects":
                    projects = await backend.list_projects()
                    return {
                        "total": len(projects),
                        "projects": [
                            {
                                "id": p.get("id"),
                                "name": p.get("name"),
                                "status": p.get("status"),
                            }
                            for p in projects
                        ],
                    }
                else:  # get_project_progress
                    dashboard = await backend.get_project_dashboard(args.get("project_id", ""))
                    return dashboard
            except Exception as e:  # 后端不可达、Token 失效等，交由模型向用户解释
                return {"error": f"查询 DevFlow 业务数据失败: {e}"}

        elif tool_call.name == "analyze_code":
            # 代码分析
            from app.services.assistant_service import AssistantService
            assistant = AssistantService()
            result = await assistant.code_review(
                type("CodeReviewReq", (), {
                    "code": args.get("code", ""),
                    "language": args.get("language", "typescript"),
                    "context": None,
                })()
            )
            return {"review": result["review"]}

        elif tool_call.name == "generate_tasks":
            # 任务拆解
            from app.services.assistant_service import AssistantService
            assistant = AssistantService()
            result = await assistant.generate_tasks(
                type("TaskGenReq", (), {
                    "description": args.get("description", ""),
                    "project_id": None,
                })()
            )
            return {"tasks": result["tasks_raw"]}

        return {"error": f"未知工具: {tool_call.name}"}
