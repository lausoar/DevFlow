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

from fastapi import Header, HTTPException, Request

from app.core.config import get_settings

settings = get_settings()


def _extract_bearer(authorization: Optional[str]) -> Optional[str]:
    if not authorization:
        return None
    value = authorization.strip()
    if value.lower().startswith("bearer "):
        return value[7:].strip()
    return value or None


def _verify_jwt(token: str) -> dict:
    """校验用户 JWT（与 Java 后端同一 HMAC 密钥，Base64 编码与 Java 侧一致）"""
    from jose import JWTError, jwt

    secret = (settings.jwt_secret or "").strip()
    if not secret:
        raise HTTPException(status_code=503, detail="AI 服务未配置 JWT_SECRET，拒绝提供服务")
    try:
        key = base64.b64decode(secret)
        return jwt.decode(token, key, algorithms=["HS256"])
    except JWTError as e:
        raise HTTPException(status_code=401, detail="登录凭证无效或已过期") from e
    except Exception as e:  # noqa: BLE001 - 密钥格式错误同样按认证失败处理
        raise HTTPException(status_code=401, detail="登录凭证无效") from e


async def require_user(
    request: Request,
    authorization: Optional[str] = Header(None),
) -> dict:
    """路由依赖：要求已认证（用户 JWT 或服务令牌）。"""
    token = _extract_bearer(authorization)
    if token:
        claims = _verify_jwt(token)
        request.state.user_id = claims.get("sub")
        request.state.authorization = authorization
        request.state.is_service = False
        return claims

    service_token = request.headers.get("x-service-token")
    expected = (settings.ai_service_token or "").strip()
    if expected and service_token and hmac.compare_digest(service_token, expected):
        request.state.user_id = None
        request.state.authorization = None
        request.state.is_service = True
        return {"sub": "service", "scope": "service"}

    raise HTTPException(status_code=401, detail="需要登录凭证")


async def require_user_token(
    request: Request,
    authorization: Optional[str] = Header(None),
) -> str:
    """
    要求"真实用户 JWT"（不接受服务令牌），返回 Authorization 头的原值。
    Agent 工具需要以该用户身份访问 Java 后端，因此必须拿到可透传的用户令牌。
    """
    await require_user(request, authorization)
    if getattr(request.state, "is_service", False):
        raise HTTPException(status_code=403, detail="该接口需要以用户身份调用")
    return authorization or ""
