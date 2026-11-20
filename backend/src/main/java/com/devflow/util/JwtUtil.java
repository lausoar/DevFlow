package com.devflow.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtUtil {

    public static final String ISSUER = "devflow";
    public static final String AUDIENCE_API = "devflow-api";
    public static final String AUDIENCE_AI = "devflow-ai";
    public static final String AUDIENCE_AUTH = "devflow-auth";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

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
            log.warn("未配置 JWT_SECRET，已生成一次性随机密钥：重启后所有登录态失效。生产环境请显式配置");
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return Keys.hmacShaKeyFor(random);
    }

    public String generateToken(String userId, String username, String role, long sessionVersion) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE_API).add(AUDIENCE_AI).and()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .claim("token_type", TYPE_ACCESS)
                .claim("session_version", String.valueOf(sessionVersion))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(String userId, long sessionVersion) {
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add(AUDIENCE_AUTH).and()
                .subject(userId)
                .claim("token_type", TYPE_REFRESH)
                .claim("session_version", String.valueOf(sessionVersion))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean validateAccessToken(String token) {
        return validateTypedToken(token, TYPE_ACCESS, AUDIENCE_API);
    }

    public boolean validateRefreshToken(String token) {
        return validateTypedToken(token, TYPE_REFRESH, AUDIENCE_AUTH);
    }

    private boolean validateTypedToken(String token, String expectedType, String expectedAudience) {
        try {
            Claims claims = parseClaims(token);
            return ISSUER.equals(claims.getIssuer())
                    && expectedType.equals(claims.get("token_type", String.class))
                    && claims.getAudience().contains(expectedAudience)
                    && claims.getId() != null
                    && claims.getSubject() != null
                    && claims.get("session_version", String.class) != null;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getUserIdFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).get("username", String.class);
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public long getSessionVersionFromToken(String token) {
        String value = parseClaims(token).get("session_version", String.class);
        if (value == null) {
            throw new JwtException("缺少会话版本");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new JwtException("会话版本无效", e);
        }
    }

    public long getRemainingExpiration(String token) {
        Claims claims = parseClaims(token);
        return Math.max(0, claims.getExpiration().getTime() - System.currentTimeMillis());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
