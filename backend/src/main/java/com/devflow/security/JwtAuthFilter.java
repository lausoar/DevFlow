package com.devflow.security;

import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import com.devflow.service.TokenBlacklistService;
import com.devflow.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String[] COOKIE_FALLBACK_PATHS = {"/files/download/", "/files/preview/"};

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (allowsCookieFallback(request) && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("df_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void authenticate(String token) {
        try {
            if (!jwtUtil.validateAccessToken(token) || tokenBlacklistService.isBlacklisted(token)) {
                return;
            }
            String userId = jwtUtil.getUserIdFromToken(token);
            User user = userMapper.selectById(userId);
            if (user == null || !"ACTIVE".equals(user.getStatus())
                    || sessionVersion(user) != jwtUtil.getSessionVersionFromToken(token)) {
                return;
            }
            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (RuntimeException e) {
            log.error("认证状态校验失败，按未认证处理: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    public static long sessionVersion(User user) {
        return user.getSessionVersion() == null ? 0L : user.getSessionVersion();
    }

    private boolean allowsCookieFallback(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        for (String prefix : COOKIE_FALLBACK_PATHS) {
            if (path.contains(prefix)) {
                return true;
            }
        }
        return false;
    }
}
