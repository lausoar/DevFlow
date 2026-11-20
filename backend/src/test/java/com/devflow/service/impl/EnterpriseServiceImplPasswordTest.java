package com.devflow.service.impl;

import com.devflow.common.EnterpriseRoles;
import com.devflow.entity.Enterprise;
import com.devflow.entity.User;
import com.devflow.mapper.DepartmentMapper;
import com.devflow.mapper.DepartmentMemberMapper;
import com.devflow.mapper.EnterpriseMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnterpriseServiceImpl 重置密码会话失效")
class EnterpriseServiceImplPasswordTest {

    @Mock
    private EnterpriseMapper enterpriseMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private DepartmentMapper departmentMapper;
    @Mock
    private DepartmentMemberMapper departmentMemberMapper;
    @Mock
    private ProjectMemberMapper projectMemberMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private EnterpriseServiceImpl service;

    @Test
    @DisplayName("超管重置成员密码递增 sessionVersion")
    void adminPasswordResetIncrementsSessionVersion() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId("e1");
        enterprise.setDeleted(0);
        User operator = user("admin", "e1", EnterpriseRoles.SUPER_ADMIN, 2L);
        User member = user("member", "e1", EnterpriseRoles.MEMBER, 6L);
        member.setUsername("alice");
        when(enterpriseMapper.selectById("e1")).thenReturn(enterprise);
        when(userMapper.selectById("admin")).thenReturn(operator);
        when(userMapper.selectById("member")).thenReturn(member);
        when(passwordEncoder.encode(anyString())).thenReturn("temporary-hash");

        String temporaryPassword = service.resetMemberPassword("e1", "member", "admin");

        assertThat(temporaryPassword).isNotBlank();
        verify(passwordEncoder).encode(temporaryPassword);
        assertThat(member.getPassword()).isEqualTo("temporary-hash");
        assertThat(member.getSessionVersion()).isEqualTo(7L);
        assertThat(member.getMustChangePassword()).isEqualTo(1);
        assertThat(member.getPasswordUpdatedAt()).isNotNull();
        verify(userMapper).updateById(member);
    }

    private static User user(String id, String enterpriseId, String role, long sessionVersion) {
        User user = new User();
        user.setId(id);
        user.setEnterpriseId(enterpriseId);
        user.setRole(role);
        user.setSessionVersion(sessionVersion);
        return user;
    }
}
