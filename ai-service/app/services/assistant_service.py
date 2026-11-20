"""
AI 助手服务 - 日报、总结、代码审查、Bug 分析等
"""
from typing import Optional
from app.services.chat_service import ChatService
from app.api.v1.chat import ChatRequest, ChatMessage


class AssistantService:
    """AI 助手服务 - 企业 AI 秘书"""

    def __init__(self):
        self.chat_service = ChatService()

    async def generate_daily_report(self, request, token: Optional[str] = None) -> dict:
        """
        AI 生成日报

        真实数据：以当前用户身份查询后端任务/Bug 列表（透传用户 JWT），
        不再只把 user_id 丢给模型让它"编"一份日报。
        查不到数据时明确告知，而不是生成看上去合理的虚假内容。
        """
        from app.services.backend_client import BackendClient

        client = BackendClient(token)
        tasks: list[dict] = []
        bugs: list[dict] = []
        data_error: Optional[str] = None
        try:
            tasks = await client.list_my_tasks(limit=100)
            bugs = await client.list_my_bugs(limit=100)
        except Exception as e:  # noqa: BLE001 - 后端不可用时降级为"无数据"提示
            data_error = str(e)

        # 项目范围过滤必须在拼 prompt 之前：否则日报里会混入其它项目的任务
        if getattr(request, "project_id", None):
            tasks = [t for t in tasks if t.get("projectId") == request.project_id]
            bugs = [b for b in bugs if b.get("projectId") == request.project_id]

        def brief(item: dict) -> str:
            bits = [item.get("title", "(无标题)")]
            if item.get("status"):
                bits.append(f"状态={item['status']}")
            if item.get("priority"):
                bits.append(f"优先级={item['priority']}")
            if item.get("dueDate"):
                bits.append(f"截止={item['dueDate']}")
            return "；".join(bits)

        task_lines = "\n".join(f"- {brief(t)}" for t in tasks) or "（无）"
        bug_lines = "\n".join(f"- {brief(b)}" for b in bugs) or "（无）"

        prompt = f"""请基于下面**真实数据**为用户生成今日工作日报。
日期: {request.date or '今天'}
{'项目范围: ' + request.project_id if request.project_id else '范围: 我参与的所有项目'}

【我的任务】（共 {len(tasks)} 条）
{task_lines}

【我负责的 Bug】（共 {len(bugs)} 条）
{bug_lines}

要求：
1. 只依据上面列出的数据，不要编造不存在的任务或 Bug；
2. 按"已完成 / 进行中 / 待办与阻塞 / 明日计划"四段输出，每条注明对应任务或 Bug 标题；
3. 如果任务或 Bug 列表为空，直接说明"今日没有分配给我的任务或 Bug"，不要虚构；
4. 使用 Markdown，中文，简洁。"""

        response = await self.chat_service.chat(
            ChatRequest(messages=[ChatMessage(role="user", content=prompt)])
        )

        return {
            "report": response.content,
            "model": response.model,
            "stats": {"tasks": len(tasks), "bugs": len(bugs)},
            "dataError": data_error,
        }

    async def generate_summary(self, request) -> dict:
        """
        AI 总结内容
        """
        type_prompts = {
            "general": "请用简洁的语言总结以下内容的要点：",
            "meeting": "请整理以下会议内容为会议纪要，包括：议题、讨论要点、决议、待办事项：",
            "sprint": "请总结本次 Sprint 的完成情况，包括：完成率、关键成果、遗留问题、改进建议：",
            "project": "请分析项目当前状态，包括：进度、风险、资源、下一步建议：",
        }

        prefix = type_prompts.get(request.summary_type, type_prompts["general"])

        response = await self.chat_service.chat(
            ChatRequest(
                messages=[ChatMessage(role="user", content=f"{prefix}\n\n{request.content}")],
                temperature=0.5,
            )
        )

        return {"summary": response.content, "model": response.model}

    async def generate_tasks(self, request) -> dict:
        """
        AI 拆解/生成任务
        """
        prompt = f"""请将以下需求描述拆解为具体的可执行任务列表。

需求描述：{request.description}

每个任务请包含：
- 任务标题
- 任务描述（简短）
- 预估故事点（1/2/3/5/8）
- 优先级建议（HIGH/MEDIUM/LOW）
- 前置依赖（如有）

请以 JSON 数组格式输出，每个任务包含 title, description, storyPoints, priority, dependencies 字段。"""

        response = await self.chat_service.chat(
            ChatRequest(
                messages=[ChatMessage(role="user", content=prompt)],
                temperature=0.4,
            )
        )

        return {"tasks_raw": response.content, "model": response.model}

    async def code_review(self, request) -> dict:
        """
        AI 代码审查
        """
        prompt = f"""请对以下 {request.language} 代码进行专业审查。

```{request.language}
{request.code}
```

{f'上下文说明：{request.context}' if request.context else ''}

请从以下维度分析：
1. **Bug 风险** - 潜在的逻辑错误、边界条件、空指针
2. **安全问题** - 注入风险、XSS、敏感信息泄露
3. **性能问题** - 不必要的计算、内存泄漏、N+1 查询
4. **代码质量** - 命名、可读性、复杂度、重复代码
5. **改进建议** - 具体的优化方案

请用中文输出，使用 Markdown 格式。"""

        response = await self.chat_service.chat(
            ChatRequest(
                messages=[ChatMessage(role="user", content=prompt)],
                temperature=0.3,
            )
        )

        return {"review": response.content, "model": response.model}

    async def analyze_bug(self, request) -> dict:
        """
        AI 分析 Bug
        """
        log_section = f"**日志**:\n{request.logs}" if request.logs else ''
        environment_section = f"**环境**: {request.environment}" if request.environment else ''
        prompt = f"""请分析以下 Bug 并提供修复建议。

**标题**: {request.title}
**描述**: {request.description}
{log_section}
{environment_section}

请分析：
1. 可能的根本原因
2. 复现步骤
3. 修复方案
4. 预防措施

请用中文输出，使用 Markdown 格式。"""

        response = await self.chat_service.chat(
            ChatRequest(
                messages=[ChatMessage(role="user", content=prompt)],
                temperature=0.3,
            )
        )

        return {"analysis": response.content, "model": response.model}
