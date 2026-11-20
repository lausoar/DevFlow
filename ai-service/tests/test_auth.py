"""JWT 验签测试。

回归的是 2026-11 修复的一个阻断级缺陷：Java 侧 JJWT 按密钥长度
选择签名算法（32~47 字节 → HS256、48~63 → HS384、>=64 → HS512），
而这里原先只允许 HS256，导致 53 字节密钥签出的 Token 一律 401 ——
AI 服务整体不可用，前端「AI 中心」四个标签页全废。
"""
from __future__ import annotations

import base64
import time

import httpx
import pytest
from fastapi import HTTPException
from starlette.requests import Request

from app.core import auth as auth_module
from app.services import backend_client as backend_module


def _secret_b64(nbytes: int) -> str:
    """生成与 Java 侧一致的 Base64 密钥（长度决定算法）"""
    return base64.b64encode(bytes(range(nbytes))).decode()


def _make_token(
    secret_b64: str,
    algorithm: str,
    sub: str = "u-1",
    **claim_overrides,
) -> str:
    from jose import jwt

    claims = {
        "sub": sub,
        "exp": int(time.time()) + 300,
        "iss": "devflow",
        "token_type": "access",
        "aud": ["devflow-api", "devflow-ai"],
        "jti": "jti-1",
        "session_version": 1,
    }
    claims.update(claim_overrides)
    return jwt.encode(claims, base64.b64decode(secret_b64), algorithm=algorithm)


@pytest.fixture
def set_secret(monkeypatch):
    """把 settings.jwt_secret 换成测试密钥"""

    def _apply(secret_b64: str):
        monkeypatch.setattr(auth_module.settings, "jwt_secret", secret_b64, raising=False)

    return _apply


@pytest.mark.parametrize(
    "nbytes,algorithm",
    [
        (32, "HS256"),   # 32 字节 → HS256
        (48, "HS384"),   # 48 字节 → HS384（本机默认 .env 就是 53 字节，走这条）
        (64, "HS512"),   # 64 字节 → HS512
    ],
)
def test_accepts_all_hmac_family_algorithms(set_secret, nbytes, algorithm):
    secret = _secret_b64(nbytes)
    set_secret(secret)
    claims = auth_module._verify_jwt(_make_token(secret, algorithm))
    assert claims["sub"] == "u-1"


@pytest.mark.parametrize(
    "claim_overrides",
    [
        {"token_type": "refresh"},
        {"aud": ["devflow-api"]},
        {"iss": "another-issuer"},
        {"sub": ""},
        {"sub": "   "},
    ],
)
def test_rejects_non_access_or_wrong_claims(set_secret, claim_overrides):
    secret = _secret_b64(32)
    set_secret(secret)
    token = _make_token(secret, "HS256", **claim_overrides)
    with pytest.raises(HTTPException) as exc:
        auth_module._verify_jwt(token)
    assert exc.value.status_code == 401


def test_rejects_token_signed_with_wrong_secret(set_secret):
    set_secret(_secret_b64(48))
    # 用另一个密钥签发（内容不同），验签必须失败
    other = base64.b64encode(bytes(reversed(range(48)))).decode()
    assert other != _secret_b64(48)
    token = _make_token(other, "HS384")
    with pytest.raises(HTTPException) as exc:
        auth_module._verify_jwt(token)
    assert exc.value.status_code == 401


def test_rejects_expired_token(set_secret):
    secret = _secret_b64(48)
    set_secret(secret)
    expired = _make_token(secret, "HS384", exp=int(time.time()) - 60)
    with pytest.raises(HTTPException) as exc:
        auth_module._verify_jwt(expired)
    assert exc.value.status_code == 401


def test_fails_closed_without_secret(set_secret):
    """未配置密钥时必须 503，不能静默放行"""
    set_secret("")
    with pytest.raises(HTTPException) as exc:
        auth_module._verify_jwt(_make_token(_secret_b64(48), "HS384"))
    assert exc.value.status_code == 503


def test_rejects_non_hmac_algorithm(set_secret):
    """alg=none 之类的非 HMAC Token 一律拒绝（防算法混淆攻击）"""
    set_secret(_secret_b64(48))
    header = base64.urlsafe_b64encode(b'{"alg":"none","typ":"JWT"}').decode().rstrip("=")
    payload = base64.urlsafe_b64encode(b'{"sub":"attacker"}').decode().rstrip("=")
    with pytest.raises(HTTPException) as exc:
        auth_module._verify_jwt(f"{header}.{payload}.")
    assert exc.value.status_code == 401


@pytest.fixture
def mock_backend_http(monkeypatch):
    """Route BackendClient through an in-memory HTTP transport."""
    real_async_client = httpx.AsyncClient

    def _install(handler):
        transport = httpx.MockTransport(handler)

        class _Client:
            def __init__(self, *args, **kwargs):
                self._client = real_async_client(*args, transport=transport, **kwargs)

            async def __aenter__(self):
                await self._client.__aenter__()
                return self

            async def __aexit__(self, *args):
                return await self._client.__aexit__(*args)

            async def request(self, *args, **kwargs):
                return await self._client.request(*args, **kwargs)

        monkeypatch.setattr(backend_module.httpx, "AsyncClient", _Client)

    return _install


def _http_request(authorization: str | None = None, service_token: str | None = None) -> Request:
    headers = []
    if authorization is not None:
        headers.append((b"authorization", authorization.encode()))
    if service_token is not None:
        headers.append((b"x-service-token", service_token.encode()))
    return Request({"type": "http", "method": "GET", "path": "/", "headers": headers})


@pytest.mark.asyncio
async def test_user_introspection_uses_original_header_and_request_state(
    set_secret, mock_backend_http
):
    secret = _secret_b64(32)
    set_secret(secret)
    authorization = f"Bearer {_make_token(secret, 'HS256')}"
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        assert request.url.path.endswith("/auth/me")
        assert request.headers["authorization"] == authorization
        return httpx.Response(200, json={"code": 200, "data": {"id": "u-1"}})

    mock_backend_http(handler)
    request = _http_request(authorization)
    claims = await auth_module.require_user(request, authorization)
    forwarded = await auth_module.require_user_token(request, authorization)

    assert claims["sub"] == "u-1"
    assert forwarded == authorization
    assert request.state.is_service is False
    assert len(calls) == 1  # global dependency + require_user_token must not introspect twice


@pytest.mark.asyncio
@pytest.mark.parametrize("java_status", [401, 403])
async def test_user_introspection_denial_maps_to_401(
    set_secret, mock_backend_http, java_status
):
    secret = _secret_b64(32)
    set_secret(secret)
    authorization = f"Bearer {_make_token(secret, 'HS256')}"
    mock_backend_http(lambda request: httpx.Response(java_status))

    with pytest.raises(HTTPException) as exc:
        await auth_module.require_user(_http_request(authorization), authorization)
    assert exc.value.status_code == 401


@pytest.mark.asyncio
@pytest.mark.parametrize("failure", ["network", "malformed"])
async def test_user_introspection_fails_closed_when_status_is_unknown(
    set_secret, mock_backend_http, failure
):
    secret = _secret_b64(32)
    set_secret(secret)
    authorization = f"Bearer {_make_token(secret, 'HS256')}"

    def handler(request: httpx.Request) -> httpx.Response:
        if failure == "network":
            raise httpx.ConnectError("backend unavailable", request=request)
        return httpx.Response(200, json={"code": 200, "data": None})

    mock_backend_http(handler)
    with pytest.raises(HTTPException) as exc:
        await auth_module.require_user(_http_request(authorization), authorization)
    assert exc.value.status_code == 503


@pytest.mark.asyncio
async def test_service_token_is_separate_and_skips_introspection(
    monkeypatch, set_secret, mock_backend_http
):
    monkeypatch.setattr(auth_module.settings, "ai_service_token", "service-secret")
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(request)
        return httpx.Response(500)

    mock_backend_http(handler)
    request = _http_request(service_token="service-secret")
    claims = await auth_module.require_user(request, authorization=None)

    assert claims["scope"] == "service"
    assert request.state.is_service is True
    assert calls == []
