package com.devflow.service.impl;

import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.entity.ProjectFeedback;
import com.devflow.entity.ProjectGuest;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectFeedbackMapper;
import com.devflow.mapper.ProjectGuestMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.service.AuditService;
import com.devflow.service.NotificationService;
import com.devflow.service.PermissionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 游客分享服务单测：token 校验、提交校验与限流、待确认队列、反馈转化。
 * 重点回归：转化 Bug 时 GUEST 分支必须写 reporterId（列 NOT NULL，2026-10-23 修过一次）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuestServiceImpl 游客分享")
class GuestServiceImplTest {

    @Mock
    private ProjectGuestMapper projectGuestMapper;
    @Mock
    private ProjectFeedbackMapper projectFeedbackMapper;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private com.devflow.mapper.RequirementMapper requirementMapper;
    @Mock
    private BugMapper bugMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AuditService auditService;
    @Mock
    private PermissionService permissionService;

    @InjectMocks
    private GuestServiceImpl service;

    private static final String TOKEN = "gf_token";
    private static final String PROJECT_ID = "p1";
    private static final String GUEST_ID = "g1";

    private ProjectGuest guest() {
        ProjectGuest g = new ProjectGuest();
        g.setId(GUEST_ID);
        g.setProjectId(PROJECT_ID);
        g.setTokenHash("hash");
        g.setRevoked(0);
        return g;
    }

    private Project project() {
        Project p = new Project();
        p.setId(PROJECT_ID);
        p.setName("测试项目");
        p.setDescription("desc");
        p.setProjectKey("TEST");
        p.setEnterpriseId("e1");
        p.setOwnerId("owner1");
        return p;
    }

    // ============================================
    // token 校验
    // ============================================

    @Test
    @DisplayName("token 查不到 / 已撤销 → 404")
    void invalidOrRevokedToken() {
        when(projectGuestMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.guestProjectInfo(TOKEN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(404))
                .hasMessageContaining("游客链接无效");

        ProjectGuest revoked = guest();
        revoked.setRevoked(1);
        when(projectGuestMapper.selectOne(any())).thenReturn(revoked);
        assertThatThrownBy(() -> service.guestProjectInfo(TOKEN))
                .hasMessageContaining("游客链接无效或已被撤销");
    }

    @Test
    @DisplayName("token 已过期 → 404 过期提示")
    void expiredToken() {
        ProjectGuest expired = guest();
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(projectGuestMapper.selectOne(any())).thenReturn(expired);
        assertThatThrownBy(() -> service.guestProjectInfo(TOKEN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(404))
                .hasMessageContaining("已过期");
    }

    @Test
    @DisplayName("有效 token → 返回项目脱敏信息 + 已通过需求")
    void validTokenReturnsProjectInfo() {
        when(projectGuestMapper.selectOne(any())).thenReturn(guest());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project());
        when(requirementMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> info = service.guestProjectInfo(TOKEN);
        assertThat(info).containsKeys("project", "requirements");
        @SuppressWarnings("unchecked")
        Map<String, Object> project = (Map<String, Object>) info.get("project");
        assertThat(project.get("name")).isEqualTo("测试项目");
        assertThat(project.get("projectKey")).isEqualTo("TEST");
    }

    // ============================================
    // 游客提交 Bug
    // ============================================

    @Test
    @DisplayName("提交 Bug：进待确认队列（pending=1），报告人=游客，通知项目负责人")
    void submitGuestBugCreatesPendingBug() {
        when(projectGuestMapper.selectOne(any())).thenReturn(guest());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project());

        Bug bug = service.submitGuestBug(TOKEN, "  标题  ", "描述", "环境", "  小王  ", "w@t.com", "1.2.3.4");

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).insert(captor.capture());
        Bug saved = captor.getValue();
        assertThat(saved.getPending()).isEqualTo(1);
        assertThat(saved.getReporterType()).isEqualTo("GUEST");
        assertThat(saved.getReporterId()).isEqualTo(GUEST_ID);
        assertThat(saved.getGuestId()).isEqualTo(GUEST_ID);
        assertThat(saved.getGuestNickname()).isEqualTo("小王");
        assertThat(saved.getStatus()).isEqualTo("NEW");
        assertThat(saved.getSeverity()).isEqualTo("MINOR");
        assertThat(saved.getPriority()).isEqualTo("MEDIUM");

        verify(notificationService).createNotification(eq("owner1"), eq("GUEST_SUBMIT"),
                contains("待确认"), contains("小王"), any());
        // 首次提交回填昵称/邮箱
        verify(projectGuestMapper).updateById(any(ProjectGuest.class));
        assertThat(bug.getPending()).isEqualTo(1);
    }

    @Test
    @DisplayName("提交校验：标题/昵称必填，标题超 200 拒绝")
    void submissionValidation() {
        when(projectGuestMapper.selectOne(any())).thenReturn(guest());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project());

        assertThatThrownBy(() -> service.submitGuestBug(TOKEN, "", null, null, "小王", null, "ip"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("标题必填");
        assertThatThrownBy(() -> service.submitGuestBug(TOKEN, "标题", null, null, " ", null, "ip"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("昵称必填");
        assertThatThrownBy(() -> service.submitGuestBug(TOKEN, "x".repeat(201), null, null, "小王", null, "ip"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("限流：单 token+IP 每小时 10 条，第 11 条 429")
    void rateLimitTenPerHour() {
        when(projectGuestMapper.selectOne(any())).thenReturn(guest());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project());

        for (int i = 0; i < 10; i++) {
            service.submitGuestBug(TOKEN, "标题" + i, null, null, "小王", null, "1.2.3.4");
        }
        assertThatThrownBy(() ->
                service.submitGuestBug(TOKEN, "第11条", null, null, "小王", null, "1.2.3.4"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(429))
                .hasMessageContaining("提交过于频繁");
        // 前 10 条都落库
        verify(bugMapper, times(10)).insert(any(Bug.class));
    }

    // ============================================
    // 反馈处理
    // ============================================

    @Test
    @DisplayName("反馈状态：非法值 400；无 bug:edit 权限 403；通过后写审计")
    void updateFeedbackStatus() {
        assertThatThrownBy(() -> service.updateFeedbackStatus("f1", "MAYBE", "u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的反馈状态");

        ProjectFeedback fb = feedback("ACCEPTED");
        when(projectFeedbackMapper.selectById("f1")).thenReturn(fb);
        when(permissionService.hasProjectPermission("u1", PROJECT_ID, "bug:edit")).thenReturn(false);
        assertThatThrownBy(() -> service.updateFeedbackStatus("f1", "ACCEPTED", "u1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403))
                .hasMessageContaining("仅测试人员或项目管理员");

        when(permissionService.hasProjectPermission("u1", PROJECT_ID, "bug:edit")).thenReturn(true);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project());
        ProjectFeedback updated = service.updateFeedbackStatus("f1", "REJECTED", "u1");
        assertThat(updated.getStatus()).isEqualTo("REJECTED");
        verify(auditService).log(eq("u1"), any(), eq(PROJECT_ID), eq("feedback.status"),
                eq("feedback"), eq("f1"), contains("REJECTED"));
    }

    @Test
    @DisplayName("建议转 Bug（游客来源）：reporterId 必须回填 guestId——NOT NULL 列回归测试")
    void convertGuestFeedbackSetsReporterId() {
        ProjectFeedback fb = feedback("ACCEPTED");
        when(projectFeedbackMapper.selectById("f1")).thenReturn(fb);
        when(permissionService.hasProjectPermission("u1", PROJECT_ID, "bug:create")).thenReturn(true);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project());

        service.convertFeedbackToBug("f1", "u1");

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).insert(captor.capture());
        Bug bug = captor.getValue();
        assertThat(bug.getReporterId()).isEqualTo(GUEST_ID);
        assertThat(bug.getReporterType()).isEqualTo("GUEST");
        assertThat(bug.getGuestNickname()).isEqualTo("小王");
        assertThat(bug.getPending()).isEqualTo(0);
        assertThat(bug.getStatus()).isEqualTo("NEW");

        // 反馈置 CONVERTED
        assertThat(fb.getStatus()).isEqualTo("CONVERTED");
        verify(projectFeedbackMapper).updateById(fb);
    }

    @Test
    @DisplayName("建议转 Bug（站内用户来源）：reporterId = 提交人")
    void convertUserFeedbackSetsReporterId() {
        ProjectFeedback fb = feedback("OPEN");
        fb.setSubmitterType("USER");
        fb.setSubmitterUserId("user9");
        fb.setGuestId(null);
        fb.setGuestNickname(null);
        when(projectFeedbackMapper.selectById("f1")).thenReturn(fb);
        when(permissionService.hasProjectPermission("u1", PROJECT_ID, "bug:create")).thenReturn(true);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project());

        service.convertFeedbackToBug("f1", "u1");

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper).insert(captor.capture());
        assertThat(captor.getValue().getReporterId()).isEqualTo("user9");
        assertThat(captor.getValue().getReporterType()).isEqualTo("USER");
    }

    @Test
    @DisplayName("建议转 Bug：已转过的 400，无 bug:create 权限 403，反馈不存在 404")
    void convertFeedbackEdgeCases() {
        assertThatThrownBy(() -> service.convertFeedbackToBug("missing", "u1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(404))
                .hasMessageContaining("反馈不存在");

        ProjectFeedback converted = feedback("CONVERTED");
        when(projectFeedbackMapper.selectById("f1")).thenReturn(converted);
        when(permissionService.hasProjectPermission("u1", PROJECT_ID, "bug:create")).thenReturn(true);
        assertThatThrownBy(() -> service.convertFeedbackToBug("f1", "u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已转化为 Bug");

        ProjectFeedback fb = feedback("OPEN");
        when(projectFeedbackMapper.selectById("f1")).thenReturn(fb);
        when(permissionService.hasProjectPermission("u1", PROJECT_ID, "bug:create")).thenReturn(false);
        assertThatThrownBy(() -> service.convertFeedbackToBug("f1", "u1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403))
                .hasMessageContaining("仅测试人员或项目管理员");
    }

    // ============================================
    // 确认游客 Bug
    // ============================================

    @Test
    @DisplayName("确认游客 Bug：pending 1→0；非待确认/不存在 → 404")
    void confirmGuestBug() {
        assertThatThrownBy(() -> service.confirmGuestBug("missing", "u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待确认");

        Bug normal = new Bug();
        normal.setId("b1");
        normal.setPending(0);
        normal.setProjectId(PROJECT_ID);
        when(bugMapper.selectById("b1")).thenReturn(normal);
        assertThatThrownBy(() -> service.confirmGuestBug("b1", "u1"))
                .hasMessageContaining("待确认");

        Bug pending = new Bug();
        pending.setId("b2");
        pending.setPending(1);
        pending.setProjectId(PROJECT_ID);
        pending.setTitle("游客Bug");
        when(bugMapper.selectById("b2")).thenReturn(pending);
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project());

        service.confirmGuestBug("b2", "u1");
        assertThat(pending.getPending()).isEqualTo(0);
        verify(bugMapper).updateById(pending);
        verify(auditService).log(eq("u1"), any(), eq(PROJECT_ID), eq("guest.bug.confirm"),
                eq("bug"), eq("b2"), contains("游客Bug"));
    }

    // ============================================
    // 游客查看自己的提交
    // ============================================

    @Test
    @DisplayName("游客提交记录：返回 bugs + feedbacks 两组")
    void guestSubmissionsShape() {
        when(projectGuestMapper.selectOne(any())).thenReturn(guest());
        when(projectMapper.selectById(PROJECT_ID)).thenReturn(project());
        when(bugMapper.selectList(any())).thenReturn(List.of());
        when(projectFeedbackMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> result = service.guestSubmissions(TOKEN);
        assertThat(result).containsKeys("bugs", "feedbacks");
    }

    private ProjectFeedback feedback(String status) {
        ProjectFeedback fb = new ProjectFeedback();
        fb.setId("f1");
        fb.setProjectId(PROJECT_ID);
        fb.setTitle("建议增加深色模式");
        fb.setContent("太亮了");
        fb.setSubmitterType("GUEST");
        fb.setGuestId(GUEST_ID);
        fb.setGuestNickname("小王");
        fb.setStatus(status);
        return fb;
    }
}
