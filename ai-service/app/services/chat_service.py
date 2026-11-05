"""
AI 聊天服务 - 封装 LLM 调用逻辑（兼容 OpenAI / DeepSeek / 通义 / Kimi / 火山 / GLM）
"""
from __future__ import annotations

import json
import uuid
from dataclasses import dataclass
from typing import AsyncGenerator, Optional
from openai import AsyncOpenAI
from app.core.config import get_settings

settings = get_settings()


@dataclass
class ToolCall:
    """工具调用结果"""
    name: str
    arguments: dict


@dataclass
class ToolResponse:
    """带工具调用的响应"""
    content: str
    tool_calls: list[ToolCall]
    model: str


class ChatService:
    """AI 聊天服务"""

    def __init__(self):
        self.system_prompt = """你是 DevFlow AI 助手，一个企业级研发协作平台的智能秘书。
你的职责包括：
- 帮助团队管理项目、任务和 Bug
- 生成日报、周报、会议纪要
- 代码审查和解释
- 分析 Bug 和提供修复建议
- 回答关于项目、文档和知识库的问题

回复要求：
- 使用中文回复
- 简洁专业，重点突出
- 适当使用 Markdown 格式
- 提供可操作的建议"""

        # 按厂商缓存 AsyncOpenAI 客户端
        self._clients: dict[str, AsyncOpenAI] = {}

    # ---------- 多厂商客户端 ----------
    def _client(self, provider_name: Optional[str] = None) -> AsyncOpenAI:
        name = (provider_name or settings.ai_provider).lower()
        if name not in self._clients:
            cfg = settings.get_provider(name)
            self._clients[name] = AsyncOpenAI(
                api_key=cfg["api_key"] or "sk-placeholder",
                base_url=cfg["base_url"],
            )
        return self._clients[name]

    def _model(self, provider_name: Optional[str] = None, override: Optional[str] = None) -> str:
        if override:
            return override
        return settings.get_provider(provider_name)["model"]

    def _configured(self, provider_name: Optional[str] = None) -> bool:
        return settings.is_provider_configured(provider_name)

    # Kimi k3 模型只允许 temperature=1，其他厂商无此限制
    _KIMI_FIXED_TEMPERATURE = {"kimi"}

    def _clamp_temperature(self, temperature: float, provider_name: str | None = None) -> float:
        name = (provider_name or settings.ai_provider).lower()
        if name in self._KIMI_FIXED_TEMPERATURE:
            return 1.0
        return temperature

    # ---------- 对话 ----------
    async def chat(self, request) -> "ChatResponse":
        """
        多轮对话
        """
        # 懒加载避免循环依赖（app.api.v1.chat 依赖本模块）
        from app.api.v1.chat import ChatResponse

        provider = getattr(request, "provider", None)
        if not self._configured(provider):
            return self._fallback_response(request.messages)

        messages = [{"role": "system", "content": self.system_prompt}]
        for msg in request.messages:
            messages.append({"role": msg.role, "content": msg.content})

        model = self._model(provider, request.model)
        client = self._client(provider)

        try:
            response = await client.chat.completions.create(
                model=model,
                messages=messages,
                temperature=self._clamp_temperature(request.temperature, provider),
                max_tokens=request.max_tokens,
            )
            return ChatResponse(
                id=str(uuid.uuid4()),
                content=response.choices[0].message.content or "",
                model=model,
                usage={
                    "prompt_tokens": response.usage.prompt_tokens if response.usage else 0,
                    "completion_tokens": response.usage.completion_tokens if response.usage else 0,
                    "total_tokens": response.usage.total_tokens if response.usage else 0,
                },
            )
        except Exception as e:
            # LLM 调用失败（Key 无效/过期、端点协议不匹配、网络等）时降级，附带原因便于排查
            fallback = self._fallback_response(request.messages)
            fallback.content = (
                f"⚠️ LLM 调用失败（{type(e).__name__}）：{str(e)[:200]}\n\n"
                f"请检查 ai-service/.env 中 {provider} 厂商的 API_KEY / BASE_URL / MODEL 配置。\n\n---\n\n"
                + fallback.content
            )
            return fallback

    async def chat_stream(self, request) -> AsyncGenerator[str, None]:
        """
        流式对话输出
        """
        provider = getattr(request, "provider", None)
        if not self._configured(provider):
            async for chunk in self._fallback_stream(request.messages):
                yield chunk
            return

        messages = [{"role": "system", "content": self.system_prompt}]
        for msg in request.messages:
            messages.append({"role": msg.role, "content": msg.content})

        model = self._model(provider, request.model)
        client = self._client(provider)

        try:
            stream = await client.chat.completions.create(
                model=model,
                messages=messages,
                temperature=self._clamp_temperature(request.temperature, provider),
                max_tokens=request.max_tokens,
                stream=True,
            )
        except Exception as e:
            # 流式调用失败同样降级，避免 SSE 直接 500
            text = f"⚠️ LLM 调用失败（{type(e).__name__}）：{str(e)[:200]}\n\n请检查 ai-service/.env 中 {provider} 厂商的配置。\n"
            for piece in text.split("\n"):
                yield json.dumps({"content": piece + "\n"}, ensure_ascii=False)
            return

        async for chunk in stream:
            if chunk.choices and chunk.choices[0].delta.content:
                content = chunk.choices[0].delta.content
                yield json.dumps({"content": content}, ensure_ascii=False)

    async def chat_with_tools(self, messages: list, tools: list, model: Optional[str] = None, provider: Optional[str] = None) -> ToolResponse:
        """
        带工具调用的对话 - Function Calling
        """
        provider = provider or settings.ai_provider
        model = model or self._model(provider)
        client = self._client(provider)

        response = await client.chat.completions.create(
            model=model,
            messages=messages,
            tools=tools,
            tool_choice="auto",
            temperature=self._clamp_temperature(0.4, provider),
        )

        choice = response.choices[0]
        content = choice.message.content or ""
        tool_calls = []

        if choice.message.tool_calls:
            for tc in choice.message.tool_calls:
                try:
                    args = json.loads(tc.function.arguments)
                except json.JSONDecodeError:
                    args = {}
                tool_calls.append(ToolCall(name=tc.function.name, arguments=args))

        return ToolResponse(content=content, tool_calls=tool_calls, model=model)

    # ---- 离线降级（未配置 LLM API Key 时返回规则回复） ----
    def _build_fallback_text(self, messages: list) -> str:
        """未配置 LLM Key 时，基于关键词给出友好引导，保证助手可用"""
        last_user = ""
        for m in reversed(messages):
            if getattr(m, "role", "") == "user":
                last_user = getattr(m, "content", "")
                break
        text = (last_user or "").strip()
        if not text:
            return "你好，我是 DevFlow AI 助手（当前为离线演示模式）。请在 ai-service/.env 中配置有效的 LLM API Key（或设置 AI_PROVIDER 切换厂商）以启用智能对话。"
        if any(k in text for k in ["你好", "hi", "hello", "在吗", "您好"]):
            return "你好！我是 DevFlow AI 助手。当前运行在离线演示模式，配置 LLM API Key 后即可提供智能回复。"
        if any(k in text for k in ["项目", "任务", "bug", "需求", "缺陷", "迭代"]):
            return "我可以协助你做项目、任务与 Bug 的管理建议（规划、拆解、定位）。当前为离线演示模式，配置 LLM Key 后将支持更复杂的研发协作能力。"
        if any(k in text for k in ["代码", "审查", "code", "review", "解释"]):
            return "代码审查与解释能力将在配置 LLM API Key 后启用。当前为离线演示模式。"
        if any(k in text for k in ["帮助", "你能", "做什么", "功能", "怎么用"]):
            return "我能做的包括：项目/任务/Bug 管理建议、日报周报生成、代码审查与解释、知识库问答等。当前为离线演示模式，配置 LLM Key 后能力将全面启用。"
        return (
            f"【离线演示模式】已收到你的消息：{text}\n\n"
            "当前未配置 LLM API Key，无法生成智能回复。\n"
            "请在 ai-service/.env 中填入对应厂商的 API Key，并设置 AI_PROVIDER（openai/deepseek/qwen/kimi/volcano/glm）后重启服务，即可启用真实对话。"
        )

    def _fallback_response(self, messages: list) -> "ChatResponse":
        from app.api.v1.chat import ChatResponse

        return ChatResponse(
            id=str(uuid.uuid4()),
            content=self._build_fallback_text(messages),
            model="offline-fallback",
            usage={"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
        )

    async def _fallback_stream(self, messages: list):
        content = self._build_fallback_text(messages)
        for piece in content.split("\n"):
            yield json.dumps({"content": piece + "\n"}, ensure_ascii=False)
