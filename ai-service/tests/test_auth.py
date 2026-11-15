"""JWT 验签测试。

回归的是 2026-11 修复的一个阻断级缺陷：Java 侧 JJWT 按密钥长度
选择签名算法（32~47 字节 → HS256、48~63 → HS384、>=64 → HS512），
而这里原先只允许 HS256，导致 53 字节密钥签出的 Token 一律 401 ——
AI 服务整体不可用，前端「AI 中心」四个标签页全废。
"""
from __future__ import annotations

import base64
import time

import pytest
from fastapi import HTTPException

from app.core import auth as auth_module


def _secret_b64(nbytes: int) -> str:
    """生成与 Java 侧一致的 Base64 密钥（长度决定算法）"""
    return base64.b64encode(bytes(range(nbytes))).decode()


def _make_token(secret_b64: str, algorithm: str, sub: str = "u-1") -> str:
    from jose import jwt

    return jwt.encode(
        {"sub": sub, "exp": int(time.time()) + 300},
        base64.b64decode(secret_b64),
        algorithm=algorithm,
    )


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
    from jose import jwt

    secret = _secret_b64(48)
    set_secret(secret)
    expired = jwt.encode(
        {"sub": "u-1", "exp": int(time.time()) - 60},
        base64.b64decode(secret),
        algorithm="HS384",
    )
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
