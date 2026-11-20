package com.devflow.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil 类型化令牌")
class JwtUtilTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000L, 120_000L);

    @Test
    @DisplayName("Access 与 Refresh 不能混用")
    void accessAndRefreshTokensAreNotInterchangeable() {
        String access = jwtUtil.generateToken("u1", "alice", "MEMBER", 7L);
        String refresh = jwtUtil.generateRefreshToken("u1", 7L);

        assertThat(jwtUtil.validateAccessToken(access)).isTrue();
        assertThat(jwtUtil.validateRefreshToken(access)).isFalse();
        assertThat(jwtUtil.validateRefreshToken(refresh)).isTrue();
        assertThat(jwtUtil.validateAccessToken(refresh)).isFalse();
    }

    @Test
    @DisplayName("缺少 P0 claims 的旧令牌即使签名有效也拒绝")
    void legacyTokenWithoutRequiredClaimsIsRejected() {
        Date now = new Date();
        String legacy = Jwts.builder()
                .issuer(JwtUtil.ISSUER)
                .audience().add(JwtUtil.AUDIENCE_API).add(JwtUtil.AUDIENCE_AUTH).and()
                .subject("u1")
                .claim("username", "alice")
                .claim("role", "MEMBER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(KEY)
                .compact();

        assertThat(jwtUtil.validateToken(legacy)).isTrue();
        assertThat(jwtUtil.validateAccessToken(legacy)).isFalse();
        assertThat(jwtUtil.validateRefreshToken(legacy)).isFalse();
    }

    @Test
    @DisplayName("Access 与 Refresh 都保留会话版本")
    void generatedTokensRetainSessionVersion() {
        String access = jwtUtil.generateToken("u1", "alice", "MEMBER", 12L);
        String refresh = jwtUtil.generateRefreshToken("u1", 12L);

        assertThat(jwtUtil.getSessionVersionFromToken(access)).isEqualTo(12L);
        assertThat(jwtUtil.getSessionVersionFromToken(refresh)).isEqualTo(12L);
    }
}
