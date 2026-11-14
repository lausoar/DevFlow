package com.devflow.security;

import com.devflow.service.TokenBlacklistService;
import com.devflow.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器 - 每次请求自动解析 Token，并检查黑名单
 *
 * Cookie 回退的收缩（见 docs/AUDIT_REPORT_2026-11.md §A6）：
 * df_token cookie 由前端 JS 写入（非 HttpOnly），一旦出现 XSS 就能被读取。
 * 因此只允许"文件下载/预览"这类无法携带 Authorization 头的 GET 请求回退读 cookie，
 * 其他所有接口必须显式带 Authorization 头。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    /** 允许从 cookie 取 token 的路径前缀（<img>/<a> 下载场景） */
    private static final String[] COOKIE_FALLBACK_PATHS = {"/files/download/", "/files/preview/"};

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = null;
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else if (allowsCookieFallback(request)) {
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("df_token".equals(cookie.getName())) {
                        token = cookie.getValue();
                        break;
                    }
                }
            }
        }

        if (token != null) {
            // 验证 Token 有效性且未被加入黑名单
            if (jwtUtil.validateToken(token) && !tokenBlacklistService.isBlacklisted(token)) {
                String userId = jwtUtil.getUserIdFromToken(token);
                String role = jwtUtil.getRoleFromToken(token);

                // 构建认证对象
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 仅 GET 的文件下载/预览允许 cookie 回退 */
    private boolean allowsCookieFallback(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        // context-path 为 /api/v1，这里按后缀匹配即可
        for (String prefix : COOKIE_FALLBACK_PATHS) {
            int idx = path.indexOf(prefix);
            if (idx >= 0) {
                return true;
            }
        }
        return false;
    }
}
