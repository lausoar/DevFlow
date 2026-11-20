"""P0 RAG scope authorization and service-principal regression tests."""
from __future__ import annotations

import base64
import time
from types import SimpleNamespace
from unittest.mock import AsyncMock, Mock

import httpx
import pytest
from fastapi.testclient import TestClient
from jose import jwt

from app.api.v1 import rag as rag_api
from app.core import auth as auth_module
from app.main import app
from app.services import rag_service as rag_module
from app.services.agent_service import AgentService
from app.services.rag_service import RagAuthorizationError, RagScopeError

TEST_SECRET = base64.b64encode(b"0123456789abcdef0123456789abcdef").decode()
SERVICE_TOKEN = "test-service-token"


def _authorization(sub: str = "u-1") -> str:
    token = jwt.encode(
        {
            "sub": sub,
            "exp": int(time.time()) + 300,
            "iss": "devflow",
            "token_type": "access",
            "aud": ["devflow-api", "devflow-ai"],
            "jti": "jti-1",
            "session_version": 1,
        },
        base64.b64decode(TEST_SECRET),
        algorithm="HS256",
    )
    return f"Bearer {token}"


def _search_request(project_id="p-1", enterprise_id=None):
    return SimpleNamespace(
        query="handbook",
        project_id=project_id,
        enterprise_id=enterprise_id,
        top_k=5,
        doc_type=None,
    )


@pytest.fixture
def client(monkeypatch):
    monkeypatch.setattr(auth_module.settings, "jwt_secret", TEST_SECRET)
    monkeypatch.setattr(auth_module.settings, "ai_service_token", SERVICE_TOKEN)
    introspect = AsyncMock()
    monkeypatch.setattr(auth_module, "_introspect_user", introspect)
    with TestClient(app) as test_client:
        test_client.introspect_mock = introspect
        yield test_client


@pytest.fixture
def backend_factory(monkeypatch):
    backend = SimpleNamespace(get_project=AsyncMock(return_value={"id": "p-1"}))
    factory = Mock(return_value=backend)
    monkeypatch.setattr(rag_module, "BackendClient", factory)
    return factory, backend


@pytest.fixture
def vector_mocks(monkeypatch):
    embed_mock = AsyncMock(return_value=[[0.1, 0.2]])
    query_mock = AsyncMock(return_value={
        "documents": [["document body"]],
        "metadatas": [[{"title": "Doc", "doc_id": "d-1"}]],
        "distances": [[0.25]],
    })
    monkeypatch.setattr(rag_module, "is_embedding_configured", lambda: True)
    monkeypatch.setattr(rag_module, "embed", embed_mock)
    monkeypatch.setattr(rag_module.chroma, "query", query_mock)
    return embed_mock, query_mock


@pytest.mark.asyncio
async def test_user_search_authorizes_forwarded_token_before_retrieval(
    backend_factory, vector_mocks
):
    factory, backend = backend_factory
    embed_mock, query_mock = vector_mocks
    service = rag_module.RagService()

    results = await service.search(_search_request(), user_token="Bearer user")

    assert len(results) == 1
    factory.assert_called_once_with("Bearer user")
    backend.get_project.assert_awaited_once_with("p-1")
    embed_mock.assert_awaited_once_with(["handbook"])
    assert query_mock.await_args.kwargs["where"] == {"project_id": "p-1"}


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "project_response,expected_status",
    [
        (None, 403),                 # Java uses data:null for an invisible project
        ([], 503),                   # malformed success payload
        ({"id": "different"}, 503),  # response does not establish the requested scope
    ],
)
async def test_denied_or_malformed_project_never_reaches_vector_store(
    backend_factory, vector_mocks, project_response, expected_status
):
    _, backend = backend_factory
    backend.get_project.return_value = project_response
    embed_mock, query_mock = vector_mocks
    service = rag_module.RagService()

    with pytest.raises(RagAuthorizationError) as exc:
        await service.search(_search_request(), user_token="Bearer user")

    assert exc.value.status_code == expected_status
    embed_mock.assert_not_awaited()
    query_mock.assert_not_awaited()


@pytest.mark.asyncio
async def test_backend_outage_fails_closed_before_retrieval(backend_factory, vector_mocks):
    _, backend = backend_factory
    backend.get_project.side_effect = httpx.ConnectError("backend unavailable")
    embed_mock, query_mock = vector_mocks
    service = rag_module.RagService()

    with pytest.raises(RagAuthorizationError) as exc:
        await service.search(_search_request(), user_token="Bearer user")

    assert exc.value.status_code == 503
    embed_mock.assert_not_awaited()
    query_mock.assert_not_awaited()


@pytest.mark.asyncio
async def test_user_enterprise_only_scope_is_rejected(backend_factory, vector_mocks):
    factory, _ = backend_factory
    embed_mock, query_mock = vector_mocks
    service = rag_module.RagService()

    with pytest.raises(RagScopeError):
        await service.search(
            _search_request(project_id=None, enterprise_id="e-1"),
            user_token="Bearer user",
        )

    factory.assert_not_called()
    embed_mock.assert_not_awaited()
    query_mock.assert_not_awaited()


@pytest.mark.asyncio
async def test_service_principal_can_search_enterprise_scope(backend_factory, vector_mocks):
    factory, _ = backend_factory
    _, query_mock = vector_mocks
    service = rag_module.RagService()

    results = await service.search(
        _search_request(project_id=None, enterprise_id="e-1"),
        is_service=True,
    )

    assert len(results) == 1
    factory.assert_not_called()
    assert query_mock.await_args.kwargs["where"] == {"enterprise_id": "e-1"}


@pytest.mark.asyncio
async def test_query_with_context_propagates_principal_and_denial():
    service = rag_module.RagService()
    service.search = AsyncMock(side_effect=RagAuthorizationError("denied"))
    request = SimpleNamespace(
        question="What is documented?",
        project_id="p-1",
        enterprise_id=None,
        top_k=5,
    )

    with pytest.raises(RagAuthorizationError):
        await service.query_with_context(request, user_token="Bearer user")

    call = service.search.await_args
    assert call.args[0].project_id == "p-1"
    assert call.kwargs == {"user_token": "Bearer user", "is_service": False}


def test_direct_search_route_allows_visible_user_project(client, backend_factory, vector_mocks):
    factory, backend = backend_factory
    authorization = _authorization()

    response = client.post(
        "/api/v1/rag/search",
        json={"query": "handbook", "project_id": "p-1"},
        headers={"Authorization": authorization},
    )

    assert response.status_code == 200
    assert response.json()[0]["content"] == "document body"
    factory.assert_called_once_with(authorization)
    backend.get_project.assert_awaited_once_with("p-1")


def test_direct_query_route_denial_is_not_empty_retrieval(client, backend_factory, vector_mocks):
    _, backend = backend_factory
    backend.get_project.return_value = None
    embed_mock, query_mock = vector_mocks

    response = client.post(
        "/api/v1/rag/query",
        json={"question": "What is documented?", "project_id": "p-1"},
        headers={"Authorization": _authorization()},
    )

    assert response.status_code == 403
    embed_mock.assert_not_awaited()
    query_mock.assert_not_awaited()


def test_direct_service_search_allows_enterprise_scope(client, backend_factory, vector_mocks):
    factory, _ = backend_factory

    response = client.post(
        "/api/v1/rag/search",
        json={"query": "handbook", "enterprise_id": "e-1"},
        headers={"X-Service-Token": SERVICE_TOKEN},
    )

    assert response.status_code == 200
    factory.assert_not_called()
    client.introspect_mock.assert_not_awaited()


@pytest.mark.parametrize("scope", [{"project_id": "p-1"}, {"enterprise_id": "e-1"}])
def test_direct_service_query_preserves_java_channel_scopes(
    client, backend_factory, vector_mocks, monkeypatch, scope
):
    from app.services.chat_service import ChatService

    factory, _ = backend_factory
    chat_mock = AsyncMock(return_value=SimpleNamespace(content="service answer"))
    monkeypatch.setattr(ChatService, "chat", chat_mock)

    response = client.post(
        "/api/v1/rag/query",
        json={"question": "What is documented?", **scope},
        headers={"X-Service-Token": SERVICE_TOKEN},
    )

    assert response.status_code == 200
    assert response.json()["answer"] == "service answer"
    factory.assert_not_called()
    client.introspect_mock.assert_not_awaited()
    chat_mock.assert_awaited_once()


def test_index_and_delete_are_service_principal_only(client, monkeypatch):
    index_mock = AsyncMock(return_value={"chunks": 0})
    delete_mock = AsyncMock()
    monkeypatch.setattr(rag_api.rag_service, "index_document", index_mock)
    monkeypatch.setattr(rag_api.rag_service, "delete_document", delete_mock)
    payload = {"doc_id": "d-1", "content": "body", "project_id": "p-1"}
    user_headers = {"Authorization": _authorization()}
    service_headers = {"X-Service-Token": SERVICE_TOKEN}

    assert client.post("/api/v1/rag/index", json=payload, headers=user_headers).status_code == 403
    assert client.delete("/api/v1/rag/index/d-1", headers=user_headers).status_code == 403
    index_mock.assert_not_awaited()
    delete_mock.assert_not_awaited()

    assert client.delete("/api/v1/rag/index/d-1", headers=service_headers).status_code == 400
    assert client.post("/api/v1/rag/index", json=payload, headers=service_headers).status_code == 200
    assert client.delete(
        "/api/v1/rag/index/d-1?project_id=p-1&enterprise_id=e-1",
        headers=service_headers,
    ).status_code == 200
    index_mock.assert_awaited_once()
    delete_mock.assert_awaited_once_with("d-1", project_id="p-1", enterprise_id="e-1")


@pytest.mark.asyncio
async def test_delete_document_is_tenant_scoped(monkeypatch):
    get_mock = AsyncMock(return_value={"ids": ["d-1_chunk_0"]})
    delete_mock = AsyncMock()
    monkeypatch.setattr(rag_module.chroma, "get", get_mock)
    monkeypatch.setattr(rag_module.chroma, "delete", delete_mock)
    service = rag_module.RagService()

    await service.delete_document("d-1", project_id="p-1")

    get_mock.assert_awaited_once_with(
        where={"$and": [{"doc_id": "d-1"}, {"project_id": "p-1"}]}
    )
    delete_mock.assert_awaited_once_with(ids=["d-1_chunk_0"])
    with pytest.raises(RagScopeError):
        await service.delete_document("d-1")

    get_mock.side_effect = RuntimeError("chroma unavailable")
    with pytest.raises(RuntimeError, match="chroma unavailable"):
        await service.delete_document("d-1", project_id="p-1")


@pytest.mark.asyncio
@pytest.mark.parametrize(
    "bound_project,expected_project",
    [("bound-project", "bound-project"), (None, "model-project")],
)
async def test_agent_project_selection_and_token_forwarding(bound_project, expected_project):
    agent = AgentService()
    agent.rag_service.search = AsyncMock(return_value=[])
    tool_call = SimpleNamespace(
        name="search_knowledge_base",
        arguments={"query": "handbook", "project_id": "model-project", "top_k": 3},
    )

    await agent._execute_tool(tool_call, token="Bearer user", project_id=bound_project)

    call = agent.rag_service.search.await_args
    assert call.args[0].project_id == expected_project
    assert call.kwargs["user_token"] == "Bearer user"


@pytest.mark.asyncio
async def test_agent_does_not_turn_scope_denial_into_empty_results():
    agent = AgentService()
    agent.rag_service.search = AsyncMock(side_effect=RagAuthorizationError("denied"))
    tool_call = SimpleNamespace(
        name="search_knowledge_base",
        arguments={"query": "handbook", "project_id": "model-project"},
    )

    with pytest.raises(RagAuthorizationError):
        await agent._execute_tool(tool_call, token="Bearer user", project_id="bound-project")
