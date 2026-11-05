package com.devflow.service.impl;

import com.devflow.common.PermissionCodes;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.SysRolePermission;
import com.devflow.entity.User;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.SysRolePermissionMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 权限判定服务单测：角色→权限点映射（内存缓存）、企业/项目两级判定、拒绝审计。
 * 映射行模拟 sys_role_permission 表（scope:role → 权限点集合）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionServiceImpl 权限判定")
class PermissionServiceImplTest {

    @Mock
    private SysRolePermissionMapper sysRolePermissionMapper;
    @Mock
    private ProjectMemberMapper projectMemberMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private PermissionServiceImpl service;

    private static final String USER_ID = "u1";
    private static final String PROJECT_ID = "p1";

    @BeforeEach
    void loadMatrix() {
        // 模拟种子矩阵：企业级（超管全权/经理可邀请）、项目级（TESTER 三权限、MEMBER 无 bug 权限）
        List<SysRolePermission> rows = List.of(
                rp("ENTERPRISE", "SUPER_ADMIN", "enterprise:manage"),
                rp("ENTERPRISE", "SUPER_ADMIN", "member:invite"),
                rp("ENTERPRISE", "MANAGER", "member:invite"),
                rp("PROJECT", "TESTER", "bug:create"),
                rp("PROJECT", "TESTER", "bug:edit"),
                rp("PROJECT", "OWNER", "member:manage"));
        when(sysRolePermissionMapper.selectList(any())).thenReturn(rows);
        service.reload();
    }

    private static SysRolePermission rp(String scope, String role, String code) {
        SysRolePermission r = new SysRolePermission();
        r.setScope(scope);
        r.setRole(role);
        r.setPermissionCode(code);
        return r;
    }

    private static User user(String role) {
        User u = new User();
        u.setId(USER_ID);
        u.setRole(role);
        return u;
    }

    private static ProjectMember member(String role) {
        ProjectMember m = new ProjectMember();
        m.setProjectId(PROJECT_ID);
        m.setUserId(USER_ID);
        m.setRole(role);
        return m;
    }

    @Test
    @DisplayName("企业权限：超管命中映射放行")
    void enterpriseAllow() {
        when(userMapper.selectById(USER_ID)).thenReturn(user("SUPER_ADMIN"));
        assertThat(service.hasEnterprisePermission(USER_ID, PermissionCodes.ENTERPRISE_MANAGE)).isTrue();
        // 未映射的权限点不放行
        assertThat(service.hasEnterprisePermission(USER_ID, "audit:read")).isFalse();
    }

    @Test
    @DisplayName("企业权限：经理只有映射内权限，未映射权限拒绝并记审计")
    void enterpriseDenyWithAudit() {
        when(userMapper.selectById(USER_ID)).thenReturn(user("MANAGER"));
        assertThat(service.hasEnterprisePermission(USER_ID, PermissionCodes.MEMBER_INVITE)).isTrue();
        assertThat(service.hasEnterprisePermission(USER_ID, PermissionCodes.ENTERPRISE_MANAGE)).isFalse();

        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(eq(USER_ID), any(), isNull(), eq("permission.denied"),
                eq("permission"), code.capture(), any());
        assertThat(code.getValue()).isEqualTo(PermissionCodes.ENTERPRISE_MANAGE);
    }

    @Test
    @DisplayName("企业权限：用户不存在/角色为空/ID 空一律拒绝")
    void enterpriseEdgeDeny() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);
        assertThat(service.hasEnterprisePermission(USER_ID, PermissionCodes.ENTERPRISE_MANAGE)).isFalse();

        when(userMapper.selectById(USER_ID)).thenReturn(user(""));
        assertThat(service.hasEnterprisePermission(USER_ID, PermissionCodes.ENTERPRISE_MANAGE)).isFalse();

        assertThat(service.hasEnterprisePermission("", PermissionCodes.ENTERPRISE_MANAGE)).isFalse();
        assertThat(service.hasEnterprisePermission(null, PermissionCodes.ENTERPRISE_MANAGE)).isFalse();
    }

    @Test
    @DisplayName("项目权限：TESTER 按 bug:create 放行，MEMBER 拒绝（豁免逻辑的映射基础）")
    void projectTesterVsMember() {
        when(projectMemberMapper.selectOne(any())).thenReturn(member("TESTER"));
        assertThat(service.hasProjectPermission(USER_ID, PROJECT_ID, PermissionCodes.BUG_CREATE)).isTrue();

        when(projectMemberMapper.selectOne(any())).thenReturn(member("MEMBER"));
        assertThat(service.hasProjectPermission(USER_ID, PROJECT_ID, PermissionCodes.BUG_CREATE)).isFalse();
        assertThat(service.hasProjectPermission(USER_ID, PROJECT_ID, PermissionCodes.MEMBER_MANAGE)).isFalse();
    }

    @Test
    @DisplayName("项目权限：非项目成员/角色为空/参数为空一律拒绝")
    void projectEdgeDeny() {
        when(projectMemberMapper.selectOne(any())).thenReturn(null);
        assertThat(service.hasProjectPermission(USER_ID, PROJECT_ID, PermissionCodes.BUG_CREATE)).isFalse();

        when(projectMemberMapper.selectOne(any())).thenReturn(member(""));
        assertThat(service.hasProjectPermission(USER_ID, PROJECT_ID, PermissionCodes.BUG_CREATE)).isFalse();

        assertThat(service.hasProjectPermission(USER_ID, "", PermissionCodes.BUG_CREATE)).isFalse();
        assertThat(service.hasProjectPermission(null, PROJECT_ID, PermissionCodes.BUG_CREATE)).isFalse();
    }

    @Test
    @DisplayName("权限点集合接口：返回角色全量权限点的防御性拷贝")
    void permissionSets() {
        when(userMapper.selectById(USER_ID)).thenReturn(user("SUPER_ADMIN"));
        Set<String> ent = service.enterprisePermissions(USER_ID);
        assertThat(ent).containsExactlyInAnyOrder("enterprise:manage", "member:invite");

        when(projectMemberMapper.selectOne(any())).thenReturn(member("TESTER"));
        assertThat(service.projectPermissions(USER_ID, PROJECT_ID))
                .containsExactlyInAnyOrder("bug:create", "bug:edit");

        // 非成员返回空集合
        when(projectMemberMapper.selectOne(any())).thenReturn(null);
        assertThat(service.projectPermissions(USER_ID, PROJECT_ID)).isEmpty();
    }

    @Test
    @DisplayName("reload：映射变更后重新加载即时生效（矩阵修改热更新的基础）")
    void reloadPicksUpChanges() {
        when(userMapper.selectById(USER_ID)).thenReturn(user("MANAGER"));
        assertThat(service.hasEnterprisePermission(USER_ID, PermissionCodes.ENTERPRISE_MANAGE)).isFalse();

        when(sysRolePermissionMapper.selectList(any())).thenReturn(List.of(
                rp("ENTERPRISE", "MANAGER", "enterprise:manage")));
        service.reload();
        assertThat(service.hasEnterprisePermission(USER_ID, PermissionCodes.ENTERPRISE_MANAGE)).isTrue();
    }
}
