package com.devflow.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具类
 *
 * 密钥来自 jwt.secret（Base64）。未配置时生成一次性随机密钥：
 * 好处是仓库里不再存在可被拿去伪造任意 Token 的固定密钥，
 * 代价是重启后旧 Token 全部失效（本机开发可接受，生产必须配置 JWT_SECRET）。
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;
    private final long refreshExpiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.key = resolveKey(secret);
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
    }

    /** 解析配置密钥；缺失或非法时生成一次性随机密钥并告警 */
    private static SecretKey resolveKey(String secret) {
        if (secret != null && !secret.isBlank()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(secret.trim());
                if (bytes.length >= 32) {
                    return Keys.hmacShaKeyFor(bytes);
                }
                log.warn("JWT_SECRET 长度不足 32 字节，已改用一次性随机密钥；请尽快配置合规密钥");
            } catch (IllegalArgumentException e) {
                log.warn("JWT_SECRET 不是合法的 Base64，已改用一次性随机密钥；请检查配置");
            }
        } else {
            log.warn("未配置 JWT_SECRET，已生成一次性随机密钥：重启后所有登录态失效。"
                    + "生产环境请通过环境变量注入 Base64 编码的 32 字节以上密钥");
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return Keys.hmacShaKeyFor(random);
    }

    /** 生成 Access Token */
    public String generateToken(String userId, String username, String role) {
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    /** 生成 Refresh Token */
    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(key)
                .compact();
    }

    /** 从 Token 中提取用户 ID */
    public String getUserIdFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** 从 Token 中提取用户名 */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).get("username", String.class);
    }

    /** 从 Token 提取角色 */
    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /** 验证 Token 是否有效 */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** 获取 Token 剩余有效时间（毫秒），用于黑名单 TTL */
    public long getRemainingExpiration(String token) {
        Claims claims = parseClaims(token);
        long exp = claims.getExpiration().getTime();
        return Math.max(0, exp - System.currentTimeMillis());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
