package com.devflow.service;

import com.devflow.entity.ProjectMember;
import com.devflow.util.Masking;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 联系人可见性：管理角色看全量，普通成员只看脱敏邮箱。 */
class ContactVisibilityTest {

    private ProjectMember member(String email) {
        ProjectMember member = new ProjectMember();
        member.setUserId("u1");
        member.setEmail(email);
        return member;
    }

    @Test
    void masksEmailsForPlainMembers() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.enterprisePermissions("u1")).thenReturn(java.util.Set.of());
        ContactVisibility visibility = new ContactVisibility(permissions);

        List<ProjectMember> members = visibility.maskProjectMembers("u1",
                List.of(member("zhang@example.com")));

        assertThat(members.get(0).getEmail()).isEqualTo(Masking.email("zhang@example.com"));
        assertThat(members.get(0).getEmail()).isNotEqualTo("zhang@example.com");
    }

    @Test
    void keepsEmailsForMemberManagers() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.enterprisePermissions("admin"))
                .thenReturn(java.util.Set.of(com.devflow.common.PermissionCodes.MEMBER_MANAGE));
        ContactVisibility visibility = new ContactVisibility(permissions);

        List<ProjectMember> members = visibility.maskProjectMembers("admin",
                List.of(member("zhang@example.com")));

        assertThat(members.get(0).getEmail()).isEqualTo("zhang@example.com");
    }

    @Test
    void keepsEmailsForProjectManagers() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.enterprisePermissions("u1")).thenReturn(java.util.Set.of());
        when(permissions.hasProjectPermission("u1", "p1", com.devflow.common.PermissionCodes.MEMBER_MANAGE))
                .thenReturn(true);
        ContactVisibility visibility = new ContactVisibility(permissions);

        List<ProjectMember> members = visibility.maskProjectMembers("u1", "p1",
                List.of(member("zhang@example.com")));

        assertThat(members.get(0).getEmail()).isEqualTo("zhang@example.com");
    }

    @Test
    void handlesNullEmailWithoutThrowing() {
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.enterprisePermissions("u1")).thenReturn(java.util.Set.of());
        ContactVisibility visibility = new ContactVisibility(permissions);

        List<ProjectMember> members = visibility.maskProjectMembers("u1", List.of(member(null)));
        assertThat(members.get(0).getEmail()).isNull();
    }
}
