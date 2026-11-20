"""
服务认证：所有 /api/v1 路由都要求已认证。

设计取舍：
- 前端调用 AI 服务时已携带用户 JWT（frontend/src/lib/api/client.ts 的 aiClient），
  这里用与 Java 后端相同的 HMAC 密钥直接验签，不另建一套账号体系；
- Java 后端同步知识库属于服务间调用，使用 X-Service-Token 头（AI_SERVICE_TOKEN 配置）；
- 未配置 JWT_SECRET 时直接拒绝（fail-closed），避免"忘配密钥 = 认证被静默关闭"。
"""
from __future__ import annotations

import base64
import hmac
from typing import Optional

import httpx
from fastapi import Header, HTTPException, Request

from app.core.config import get_settings
from app.services.backend_client import BackendClient

settings = get_settings()


def _extract_bearer(authorization: Optional[str]) -> Optional[str]:
    if not authorization:
        return None
    value = authorization.strip()
    if value.lower().startswith("bearer "):
        return value[7:].strip()
    return value or None


def _verify_jwt(token: str) -> dict:
    """校验用户 JWT（与 Java 后端同一 HMAC 密钥，Base64 编码与 Java 侧一致）

    Java 侧用 JJWT 的 signWith(SecretKey)，算法由密钥长度决定：
    48~63 字节选 HS384、>=64 字节选 HS512。这里只放开 HS256 会让所有
    53 字节密钥签出的 Token 一律 401（AI 服务整体不可用），所以三种 HMAC
    算法都要接受——具体用哪一种仍由 Token 头部声明，非 HMAC 家族一律拒绝。
    """
    from jose import JWTError, jwt

    secret = (settings.jwt_secret or "").strip()
    if not secret:
        raise HTTPException(status_code=503, detail="AI 服务未配置 JWT_SECRET，拒绝提供服务")
    try:
        key = base64.b64decode(secret)
        claims = jwt.decode(
            token,
            key,
            algorithms=["HS256", "HS384", "HS512"],
            audience="devflow-ai",
            issuer="devflow",
        )
    except JWTError as e:
        raise HTTPException(status_code=401, detail="登录凭证无效或已过期") from e
    except Exception as e:  # noqa: BLE001 - 密钥格式错误同样按认证失败处理
        raise HTTPException(status_code=401, detail="登录凭证无效") from e
    return _validate_access_claims(claims)


def _validate_access_claims(claims: dict) -> dict:
    """验签之外，只接受面向 AI 服务的 Java access token。"""
    subject = claims.get("sub")
    if (
        claims.get("token_type") != "access"
        or not isinstance(subject, str)
        or not subject.strip()
    ):
        raise HTTPException(status_code=401, detail="需要有效的访问令牌")
    return claims


async def _introspect_user(authorization: str, subject: str) -> None:
    """由 Java 判定黑名单、账号状态和 session 版本，失败时关闭访问。"""
    try:
        current_user = await BackendClient(authorization).get_current_user()
    except httpx.HTTPStatusError as e:
        if e.response.status_code in (401, 403):
            raise HTTPException(status_code=401, detail="登录凭证已失效") from e
        raise HTTPException(status_code=503, detail="暂时无法确认用户授权状态") from e
    except Exception as e:  # 网络、响应格式和后端业务错误均不能视为已授权
        raise HTTPException(status_code=503, detail="暂时无法确认用户授权状态") from e

    backend_subject = current_user.get("id") if isinstance(current_user, dict) else None
    if not isinstance(backend_subject, str) or not backend_subject.strip():
        raise HTTPException(status_code=503, detail="后端用户响应格式无效")
    if backend_subject != subject:
        raise HTTPException(status_code=401, detail="登录凭证与用户身份不一致")


async def require_user(
    request: Request,
    authorization: Optional[str] = Header(None),
) -> dict:
    """路由依赖：要求已认证（用户 JWT 或服务令牌），同一请求只内省一次。"""
    if getattr(request.state, "auth_complete", False):
        return request.state.auth_claims

    token = _extract_bearer(authorization)
    if token:
        claims = _verify_jwt(token)
        await _introspect_user(authorization or "", claims["sub"])
        request.state.user_id = claims["sub"]
        request.state.authorization = authorization
        request.state.is_service = False
        request.state.auth_claims = claims
        request.state.auth_complete = True
        return claims

    service_token = request.headers.get("x-service-token")
    expected = (settings.ai_service_token or "").strip()
    if expected and service_token and hmac.compare_digest(service_token, expected):
        claims = {"sub": "service", "scope": "service"}
        request.state.user_id = None
        request.state.authorization = None
        request.state.is_service = True
        request.state.auth_claims = claims
        request.state.auth_complete = True
        return claims

    raise HTTPException(status_code=401, detail="需要登录凭证")


async def require_user_token(
    request: Request,
    authorization: Optional[str] = Header(None),
) -> str:
    """
    要求"真实用户 JWT"（不接受服务令牌），返回 Authorization 头的原值。
    Agent 工具需要以该用户身份访问 Java 后端，因此必须拿到可透传的用户令牌。
    """
    if not getattr(request.state, "auth_complete", False):
        await require_user(request, authorization)
    if getattr(request.state, "is_service", False):
        raise HTTPException(status_code=403, detail="该接口需要以用户身份调用")
    return getattr(request.state, "authorization", None) or authorization or ""
