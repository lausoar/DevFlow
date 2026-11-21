package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Enterprise;
import com.devflow.entity.User;
import com.devflow.mapper.DepartmentMapper;
import com.devflow.mapper.DepartmentMemberMapper;
import com.devflow.mapper.EnterpriseMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成员移除/退出/解散后必须让在途 Token 失效。
 *
 * P3 修的真实缺陷：这三个方法原先只清 enterprise_id，不递增 session_version，
 * 被移除的人在 Token 过期前（默认 24h）仍能访问原企业数据 —— JwtAuthFilter
 * 只比对 session_version。
 */
@ExtendWith(MockitoExtension.class)
class EnterpriseMembershipRevocationTest {

    @Mock private EnterpriseMapper enterpriseMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private UserMapper userMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private DepartmentMemberMapper departmentMemberMapper;
    @Mock private ProjectMemberMapper projectMemberMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @InjectMocks private EnterpriseServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void registerTableInfo() {
        // LambdaUpdateWrapper 需要 TableInfo 缓存；单测没有跑 MyBatis 扫描，手动注册
        for (Class<?> entity : List.of(User.class, Enterprise.class, com.devflow.entity.Project.class)) {
            if (com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(entity) == null) {
                com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                        new org.apache.ibatis.builder.MapperBuilderAssistant(
                                new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                        entity);
            }
        }
    }

    private User user(String id, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(id);
        user.setEmail(id + "@example.invalid");
        user.setEnterpriseId("e1");
        user.setRole(role);
        return user;
    }

    private Enterprise enterprise() {
        Enterprise enterprise = new Enterprise();
        enterprise.setId("e1");
        enterprise.setName("测试企业");
        return enterprise;
    }

    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<User> capturedUserUpdate() {
        ArgumentCaptor<LambdaUpdateWrapper<User>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userMapper).update(eq(null), captor.capture());
        return captor.getValue();
    }

    @Test
    void removeMemberBumpsSessionVersionSoInFlightTokensDie() {
        when(userMapper.selectById("admin")).thenReturn(user("admin", "ADMIN"));
        when(userMapper.selectById("member")).thenReturn(user("member", "MEMBER"));
        when(projectMapper.selectList(any())).thenReturn(List.of());
        when(projectMemberMapper.delete(any())).thenReturn(0);

        service.removeMember("e1", "member", "admin");

        assertThat(capturedUserUpdate().getSqlSet()).contains("session_version");
    }

    @Test
    void leaveEnterpriseBumpsSessionVersion() {
        when(enterpriseMapper.selectById("e1")).thenReturn(enterprise());
        when(userMapper.selectById("member")).thenReturn(user("member", "MEMBER"));
        when(departmentMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.selectList(any())).thenReturn(List.of());

        service.leaveEnterprise("e1", "member");

        assertThat(capturedUserUpdate().getSqlSet()).contains("session_version");
    }

    @Test
    void dissolveEnterpriseBumpsSessionVersionForAllMembers() {
        when(enterpriseMapper.selectById("e1")).thenReturn(enterprise());
        when(userMapper.selectById("owner")).thenReturn(user("owner", "SUPER_ADMIN"));
        when(departmentMapper.selectList(any())).thenReturn(List.of());
        when(projectMapper.update(any(), any())).thenReturn(1);
        when(enterpriseMapper.update(any(), any())).thenReturn(1);

        service.dissolveEnterprise("e1", "owner");

        assertThat(capturedUserUpdate().getSqlSet()).contains("session_version");
    }

    @Test
    void creatingEnterpriseCleansPreviousTenantMembershipAndBumpsVersion() {
        User owner = user("owner", "MEMBER");
        owner.setEnterpriseId("e-old");
        when(userMapper.selectById("owner")).thenReturn(owner);
        com.devflow.entity.Project oldProject = new com.devflow.entity.Project();
        oldProject.setId("p-old");
        when(projectMapper.selectList(any())).thenReturn(List.of(oldProject));
        var request = new com.devflow.dto.request.CreateEnterpriseRequest();
        request.setName("新企业");

        service.createEnterprise(request, "owner");

        verify(projectMemberMapper).delete(any());
        verify(departmentMemberMapper).delete(any());
        assertThat(capturedUserUpdate().getSqlSet()).contains("session_version");
    }

    @Test
    void firstEnterpriseCreationDoesNotInvalidateCurrentSession() {
        User owner = user("owner", "MEMBER");
        owner.setEnterpriseId(null);
        when(userMapper.selectById("owner")).thenReturn(owner);
        var request = new com.devflow.dto.request.CreateEnterpriseRequest();
        request.setName("首个企业");

        service.createEnterprise(request, "owner");

        // 首次创建必须能继续使用当前 Token 建项目；不能无谓地强制重新登录
        assertThat(capturedUserUpdate().getSqlSet()).doesNotContain("session_version");
    }

    @Test
    void roleUpdateUsesNarrowWriteAndCannotRestoreEnterpriseMembership() {
        when(userMapper.selectById("admin")).thenReturn(user("admin", "ADMIN"));
        when(userMapper.selectById("member")).thenReturn(user("member", "MEMBER"));
        when(userMapper.update(eq(null), any())).thenReturn(1);

        service.updateMemberRole("e1", "member", "MEMBER", "admin");

        // 即使 role 没变，也只能更新 role 列；绝不能把整实体里的旧 enterprise/session 写回
        verify(userMapper, never()).updateById(any(User.class));
        verify(userMapper).update(eq(null), any());
    }

    @Test
    void selfRemovalIsStillRejected() {
        assertThatThrownBy(() -> service.removeMember("e1", "admin", "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能移除自己");
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void removingHigherRankedMemberIsRejected() {
        when(userMapper.selectById("manager")).thenReturn(user("manager", "MANAGER"));
        when(userMapper.selectById("admin")).thenReturn(user("admin", "ADMIN"));

        assertThatThrownBy(() -> service.removeMember("e1", "admin", "manager"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能移除职级不低于自己的成员");
        verify(userMapper, never()).update(any(), any());
    }
}
