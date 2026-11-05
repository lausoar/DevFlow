package com.devflow.service.impl;

import com.devflow.entity.Project;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.ProjectAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 项目可见性口径单测：成员可见自己参与的项目，企业超管额外可见本企业全部项目。
 * 可见性是安全边界——用户报过的两轮问题都源于口径不统一。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectAccessService 项目可见性")
class ProjectAccessServiceImplTest {

    @Mock
    private ProjectMemberMapper projectMemberMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private ProjectMapper projectMapper;

    @InjectMocks
    private ProjectAccessServiceImpl service;

    private com.devflow.entity.ProjectMember membership(String projectId) {
        com.devflow.entity.ProjectMember m = new com.devflow.entity.ProjectMember();
        m.setProjectId(projectId);
        m.setUserId("u1");
        return m;
    }

    private com.devflow.entity.User user(String role, String enterpriseId) {
        com.devflow.entity.User u = new com.devflow.entity.User();
        u.setId("u1");
        u.setRole(role);
        u.setEnterpriseId(enterpriseId);
        return u;
    }

    private Project project(String id) {
        Project p = new Project();
        p.setId(id);
        p.setEnterpriseId("e1");
        return p;
    }

    @Test
    @DisplayName("普通成员：只可见自己参与的项目")
    void memberSeesOnlyJoinedProjects() {
        when(projectMemberMapper.selectList(any())).thenReturn(List.of(membership("p1"), membership("p2")));
        when(userMapper.selectById("u1")).thenReturn(user("MEMBER", "e1"));

        List<String> ids = service.visibleProjectIds("u1");
        assertThat(ids).containsExactly("p1", "p2");
        assertThat(service.canView("u1", "p1")).isTrue();
        assertThat(service.canView("u1", "p3")).isFalse();
    }

    @Test
    @DisplayName("未参与任何项目的普通成员：可见集为空（看不到同企业其他项目）")
    void nonMemberSeesNothing() {
        when(projectMemberMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectById("u1")).thenReturn(user("MEMBER", "e1"));

        assertThat(service.visibleProjectIds("u1")).isEmpty();
        assertThat(service.canView("u1", "p1")).isFalse();
    }

    @Test
    @DisplayName("企业超管：参与的项目 + 本企业全部项目（去重）")
    void superAdminSeesEnterpriseProjects() {
        when(projectMemberMapper.selectList(any())).thenReturn(List.of(membership("p1")));
        when(userMapper.selectById("u1")).thenReturn(user("SUPER_ADMIN", "e1"));
        when(projectMapper.selectList(any())).thenReturn(List.of(project("p1"), project("p2"), project("p3")));

        List<String> ids = service.visibleProjectIds("u1");
        assertThat(ids).containsExactlyInAnyOrder("p1", "p2", "p3");
    }

    @Test
    @DisplayName("经理/管理员（非超管）：不加企业视野，仅成员口径")
    void managerDoesNotGetEnterpriseView() {
        when(projectMemberMapper.selectList(any())).thenReturn(List.of(membership("p1")));
        when(userMapper.selectById("u1")).thenReturn(user("MANAGER", "e1"));

        assertThat(service.visibleProjectIds("u1")).containsExactly("p1");
    }

    @Test
    @DisplayName("无企业用户：可见集为空（越权防护）")
    void userWithoutEnterpriseSeesNothing() {
        when(projectMemberMapper.selectList(any())).thenReturn(List.of());
        when(userMapper.selectById("u1")).thenReturn(user("MEMBER", null));

        assertThat(service.visibleProjectIds("u1")).isEmpty();
    }

    @Test
    @DisplayName("参数边界：userId 空 / projectId 空一律不可见")
    void blankParamsDenied() {
        assertThat(service.visibleProjectIds("")).isEmpty();
        assertThat(service.visibleProjectIds(null)).isEmpty();
        assertThat(service.canView("u1", "")).isFalse();
        assertThat(service.canView("u1", null)).isFalse();
    }
}
