package com.devflow.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expiration;
    private final long refreshExpiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        this.expiration = expiration;
        this.refreshExpiration = refreshExpiration;
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
