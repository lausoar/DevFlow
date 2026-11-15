"""离线降级与 Agent 工具链测试。

回归 2026-11 的两个缺陷：
1. `chat_with_tools()` 没走 `_configured()` 判断，未配置 Key 时
   拿占位 Key 真去请求厂商，把 401 直接冒成 500。
2. LLM 侧任何异常都应当降级成可读回复，而不是让 Agent 整体 500。
"""
from __future__ import annotations

import pytest

from app.services import chat_service as chat_module
from app.services.chat_service import ChatService


class _Msg:
    def __init__(self, role: str, content: str):
        self.role = role
        self.content = content


@pytest.fixture
def service() -> ChatService:
    return ChatService()


@pytest.fixture
def unconfigured(monkeypatch):
    """模拟"没配任何 LLM Key"的环境"""
    monkeypatch.setattr(ChatService, "_configured", lambda self, name=None: False, raising=True)


@pytest.fixture
def exploding_client(monkeypatch):
    """模拟"配了 Key 但厂商返回 401"的环境"""

    class _Boom:
        class chat:  # noqa: N801 - 模拟 openai 的对象层级
            class completions:  # noqa: N801
                @staticmethod
                async def create(**kwargs):
                    raise RuntimeError("Authentication Fails, Your api key is invalid")

    monkeypatch.setattr(ChatService, "_configured", lambda self, name=None: True, raising=True)
    monkeypatch.setattr(ChatService, "_client", lambda self, name=None: _Boom(), raising=True)


@pytest.mark.asyncio
async def test_chat_with_tools_degrades_when_unconfigured(service, unconfigured):
    resp = await service.chat_with_tools(
        messages=[_Msg("user", "我的任务有哪些")],
        tools=[],
    )
    assert resp.model == "offline-fallback"
    assert resp.tool_calls == []
    assert resp.content  # 必须给出可读的降级文案，不能是空


@pytest.mark.asyncio
async def test_chat_with_tools_degrades_when_provider_fails(service, exploding_client):
    resp = await service.chat_with_tools(
        messages=[_Msg("user", "我的任务有哪些")],
        tools=[],
    )
    assert resp.model == "offline-fallback"
    # 降级文案里要带上失败原因，方便排查配置问题
    assert "LLM 调用失败" in resp.content


def test_fallback_text_matches_intent(service):
    """降级文案按关键词分流，保证离线模式下也有像样的回复"""
    greet = service._build_fallback_text([_Msg("user", "你好")])
    assert "DevFlow AI 助手" in greet

    about_tasks = service._build_fallback_text([_Msg("user", "帮我看下项目里的任务和 Bug")])
    assert "项目" in about_tasks and "Bug" in about_tasks

    review = service._build_fallback_text([_Msg("user", "帮我做代码审查")])
    assert "代码审查" in review

    fallback = service._build_fallback_text([_Msg("user", "量子纠缠是什么")])
    assert "离线演示模式" in fallback


def test_fallback_text_without_user_message(service):
    text = service._build_fallback_text([_Msg("system", "你是助手")])
    assert "离线演示模式" in text


def test_provider_configured_ignores_placeholders():
    """占位 Key 必须视为"未配置"，否则会拿假 Key 去请求真实厂商"""
    from app.core.config import Settings

    s = Settings(openai_api_key="sk-placeholder")
    assert s.is_provider_configured("openai") is False

    s2 = Settings(openai_api_key="sk-real-key-123")
    assert s2.is_provider_configured("openai") is True
