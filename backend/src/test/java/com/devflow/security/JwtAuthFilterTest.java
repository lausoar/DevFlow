package com.devflow.security;

import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import com.devflow.service.TokenBlacklistService;
import com.devflow.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter 类型化 Access 鉴权")
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private JwtAuthFilter filter;

    @BeforeEach
    void clearSecurityContextBeforeTest() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Refresh Token 放在 Bearer 中也不能认证")
    void refreshTokenCannotAuthenticateAsAccess() throws Exception {
        MockHttpServletRequest request = requestWithToken("refresh-token");
        MockFilterChain chain = new MockFilterChain();
        when(jwtUtil.validateAccessToken("refresh-token")).thenReturn(false);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
        verify(tokenBlacklistService, never()).isBlacklisted(anyString());
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("用户会话版本变化后旧 Access Token 不能认证")
    void sessionVersionMismatchIsAnonymous() throws Exception {
        User user = new User();
        user.setId("u1");
        user.setStatus("ACTIVE");
        user.setRole("MEMBER");
        user.setSessionVersion(8L);
        MockHttpServletRequest request = requestWithToken("access-token");
        MockFilterChain chain = new MockFilterChain();
        when(jwtUtil.validateAccessToken("access-token")).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted("access-token")).thenReturn(false);
        when(jwtUtil.getUserIdFromToken("access-token")).thenReturn("u1");
        when(userMapper.selectById("u1")).thenReturn(user);
        when(jwtUtil.getSessionVersionFromToken("access-token")).thenReturn(7L);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
