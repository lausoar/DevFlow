package com.devflow.service.impl;

import com.devflow.entity.User;
import com.devflow.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 自助改密会话失效")
class UserServiceImplPasswordTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl service;

    @Test
    @DisplayName("自助改密递增 sessionVersion 并清除强制改密标记")
    void selfPasswordChangeIncrementsSessionVersion() {
        User user = new User();
        user.setId("u1");
        user.setPassword("old-hash");
        user.setSessionVersion(4L);
        user.setMustChangePassword(1);
        when(userMapper.selectById("u1")).thenReturn(user);
        when(passwordEncoder.matches("OldPass1", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPass2", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPass2")).thenReturn("new-hash");

        service.changePassword("u1", "OldPass1", "NewPass2");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        assertThat(user.getSessionVersion()).isEqualTo(5L);
        assertThat(user.getMustChangePassword()).isZero();
        assertThat(user.getPasswordUpdatedAt()).isNotNull();
        verify(userMapper).updateById(user);
    }
}
