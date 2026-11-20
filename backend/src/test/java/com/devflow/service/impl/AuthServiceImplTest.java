package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.devflow.common.exception.BusinessException;
import com.devflow.dto.response.AuthResponse;
import com.devflow.entity.User;
import com.devflow.mapper.EnterpriseMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.MfaService;
import com.devflow.service.TokenBlacklistService;
import com.devflow.util.JwtUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl Refresh 轮换")
class AuthServiceImplTest {

    private static final String USER_ID = "u1";
    private static final String ACCESS = "access-token";
    private static final String REFRESH = "refresh-token";

    @Mock
    private UserMapper userMapper;
    @Mock
    private EnterpriseMapper enterpriseMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private MfaService mfaService;

    @InjectMocks
    private AuthServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), User.class);
    }

    @Test
    @DisplayName("刷新成功先原子消费旧 Refresh，再签发新令牌对")
    void refreshConsumesOldTokenBeforeRotating() {
        User user = activeUser(7L);
        user.setPassword("secret-hash");
        user.setMustChangePassword(1);
        stubRefreshUser(user);
        when(jwtUtil.getSessionVersionFromToken(REFRESH)).thenReturn(7L);
        when(jwtUtil.getRemainingExpiration(REFRESH)).thenReturn(45_000L);
        when(tokenBlacklistService.consume(REFRESH, 45_000L)).thenReturn(true);
        when(jwtUtil.generateToken(USER_ID, "alice", "MEMBER", 7L)).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(USER_ID, 7L)).thenReturn("new-refresh");

        AuthResponse response = service.refreshToken(REFRESH);

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        assertThat(response.getUser().getPassword()).isNull();
        assertThat(response.getMustChangePassword()).isTrue();
        InOrder order = inOrder(tokenBlacklistService, jwtUtil);
        order.verify(tokenBlacklistService).consume(REFRESH, 45_000L);
        order.verify(jwtUtil).generateToken(USER_ID, "alice", "MEMBER", 7L);
        order.verify(jwtUtil).generateRefreshToken(USER_ID, 7L);
    }

    @Test
    @DisplayName("重放或已撤销 Refresh（consume=false）拒绝且不换发")
    void replayedOrBlacklistedRefreshIsDenied() {
        stubRefreshUser(activeUser(7L));
        when(jwtUtil.getSessionVersionFromToken(REFRESH)).thenReturn(7L);
        when(jwtUtil.getRemainingExpiration(REFRESH)).thenReturn(45_000L);
        when(tokenBlacklistService.consume(REFRESH, 45_000L)).thenReturn(false);

        assertThatThrownBy(() -> service.refreshToken(REFRESH))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401))
                .hasMessageContaining("已使用或已撤销");
        verify(jwtUtil, never()).generateToken(anyString(), anyString(), anyString(), anyLong());
        verify(jwtUtil, never()).generateRefreshToken(anyString(), anyLong());
    }

    @Test
    @DisplayName("Access Token 不能提交到刷新接口")
    void accessTokenCannotBeRefreshed() {
        when(jwtUtil.validateRefreshToken(ACCESS)).thenReturn(false);

        assertThatThrownBy(() -> service.refreshToken(ACCESS))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401));
        verifyNoInteractions(userMapper, tokenBlacklistService);
    }

    @Test
    @DisplayName("非 ACTIVE 用户不能刷新")
    void inactiveUserCannotRefresh() {
        User user = activeUser(7L);
        user.setStatus("INACTIVE");
        stubRefreshUser(user);

        assertThatThrownBy(() -> service.refreshToken(REFRESH))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401));
        verify(tokenBlacklistService, never()).consume(anyString(), anyLong());
    }

    @Test
    @DisplayName("令牌会话版本必须与用户当前版本一致")
    void sessionVersionMismatchCannotRefresh() {
        stubRefreshUser(activeUser(8L));
        when(jwtUtil.getSessionVersionFromToken(REFRESH)).thenReturn(7L);

        assertThatThrownBy(() -> service.refreshToken(REFRESH))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401));
        verify(tokenBlacklistService, never()).consume(anyString(), anyLong());
    }

    @Test
    @DisplayName("consume 后、签发前会话版本变化仍拒绝刷新")
    void sessionVersionChangeAfterConsumeIsDenied() {
        when(jwtUtil.validateRefreshToken(REFRESH)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(REFRESH)).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser(7L), activeUser(8L));
        when(jwtUtil.getSessionVersionFromToken(REFRESH)).thenReturn(7L);
        when(jwtUtil.getRemainingExpiration(REFRESH)).thenReturn(45_000L);
        when(tokenBlacklistService.consume(REFRESH, 45_000L)).thenReturn(true);

        assertThatThrownBy(() -> service.refreshToken(REFRESH))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(401))
                .hasMessageContaining("登录状态已失效");
        InOrder order = inOrder(userMapper, tokenBlacklistService);
        order.verify(userMapper).selectById(USER_ID);
        order.verify(tokenBlacklistService).consume(REFRESH, 45_000L);
        order.verify(userMapper).selectById(USER_ID);
        verify(jwtUtil, never()).generateToken(anyString(), anyString(), anyString(), anyLong());
        verify(jwtUtil, never()).generateRefreshToken(anyString(), anyLong());
    }

    @Test
    @DisplayName("logout 先持久化递增 sessionVersion，再写 Redis 黑名单")
    void logoutIncrementsSessionVersionBeforeBlacklistingTokens() {
        when(jwtUtil.validateAccessToken(ACCESS)).thenReturn(true);
        when(jwtUtil.validateRefreshToken(REFRESH)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(ACCESS)).thenReturn(USER_ID);
        when(jwtUtil.getRemainingExpiration(ACCESS)).thenReturn(10_000L);
        when(jwtUtil.getRemainingExpiration(REFRESH)).thenReturn(20_000L);

        service.logout(ACCESS, REFRESH);

        ArgumentCaptor<LambdaUpdateWrapper<User>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        InOrder order = inOrder(userMapper, tokenBlacklistService);
        order.verify(userMapper).update(isNull(), captor.capture());
        order.verify(tokenBlacklistService).blacklist(ACCESS, 10_000L);
        order.verify(tokenBlacklistService).blacklist(REFRESH, 20_000L);
        LambdaUpdateWrapper<User> update = captor.getValue();
        update.getSqlSegment();
        assertThat(update.getParamNameValuePairs()).containsValue(USER_ID);
        assertThat(update.getSqlSet())
                .contains("session_version = COALESCE(session_version, 0) + 1");
    }

    @Test
    @DisplayName("Redis 黑名单失败不回滚已完成的数据库注销")
    void blacklistFailureDoesNotUndoDurableLogout() {
        when(jwtUtil.validateAccessToken(ACCESS)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(ACCESS)).thenReturn(USER_ID);
        when(jwtUtil.getRemainingExpiration(ACCESS)).thenReturn(10_000L);
        doThrow(new BusinessException(503, "redis down"))
                .when(tokenBlacklistService).blacklist(ACCESS, 10_000L);

        assertThatCode(() -> service.logout(ACCESS, null)).doesNotThrowAnyException();

        verify(userMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(tokenBlacklistService).blacklist(ACCESS, 10_000L);
    }

    @Test
    @DisplayName("logout 只撤销通过对应类型校验的令牌")
    void logoutDoesNotBlacklistWrongTokenTypes() {
        service.logout("refresh-presented-as-access", "access-presented-as-refresh");

        verify(jwtUtil, times(2)).validateAccessToken("refresh-presented-as-access");
        verify(jwtUtil, times(2)).validateRefreshToken("access-presented-as-refresh");
        verifyNoInteractions(userMapper);
        verify(tokenBlacklistService, never()).blacklist(anyString(), anyLong());
    }

    private void stubRefreshUser(User user) {
        when(jwtUtil.validateRefreshToken(REFRESH)).thenReturn(true);
        when(jwtUtil.getUserIdFromToken(REFRESH)).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(user);
    }

    private static User activeUser(long sessionVersion) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setRole("MEMBER");
        user.setStatus("ACTIVE");
        user.setSessionVersion(sessionVersion);
        user.setMustChangePassword(0);
        return user;
    }
}
