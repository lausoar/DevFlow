package com.devflow.service.impl;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.ImPushService;
import com.devflow.service.NotificationRecipients;
import com.devflow.service.NotificationService;
import com.devflow.service.SubscriptionService;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BugServiceImplTest {

    @Mock private BugMapper bugMapper;
    @Mock private NotificationService notificationService;
    @Mock private ProjectMapper projectMapper;
    @Mock private ImPushService imPushService;
    @Mock private AutomationRuleService automationRuleService;
    @Mock private UserMapper userMapper;
    @Mock private NotificationRecipients notificationRecipients;
    @Mock private SubscriptionService subscriptionService;
    @Mock private WorkflowService workflowService;
    @Mock private com.devflow.service.ProjectAccessService projectAccessService;

    @InjectMocks private BugServiceImpl service;

    private Bug existingBug() {
        Bug bug = new Bug();
        bug.setId("b1");
        bug.setProjectId("p1");
        bug.setReporterId("reporter");
        bug.setAssigneeId("old-assignee");
        bug.setTitle("旧标题");
        bug.setStatus("NEW");
        bug.setVersion(4);
        return bug;
    }

    @Test
    void createPreservesWorkflowDerivedInitialStatus() {
        Bug bug = new Bug();
        bug.setTitle("自定义初态");
        bug.setStatus("TRIAGE");

        Bug created = service.create("p1", bug, "reporter");

        assertThat(created.getStatus()).isEqualTo("TRIAGE");
        verify(workflowService, never()).initialStatus(any(), any());
    }

    @Test
    void createUsesWorkflowDefaultOnlyWhenStatusAbsent() {
        Bug bug = new Bug();
        bug.setTitle("缺省初态");
        when(workflowService.initialStatus("p1", WorkflowService.TYPE_BUG)).thenReturn("INTAKE");

        Bug created = service.create("p1", bug, "reporter");

        assertThat(created.getStatus()).isEqualTo("INTAKE");
    }

    @Test
    void updateClearsAssigneeInSameVersionedOperation() throws Exception {
        Bug existing = existingBug();
        Bug reread = existingBug();
        reread.setAssigneeId(null);
        reread.setTitle("新标题");
        reread.setVersion(5);
        when(bugMapper.selectById("b1")).thenReturn(existing, reread);
        when(bugMapper.updateById(any(Bug.class))).thenReturn(1);

        Bug patch = new Bug();
        patch.setTitle("新标题");
        patch.setAssigneeId("");
        service.update("b1", patch, 4);

        ArgumentCaptor<Bug> captor = ArgumentCaptor.forClass(Bug.class);
        verify(bugMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo("b1");
        assertThat(captor.getValue().getTitle()).isEqualTo("新标题");
        assertThat(captor.getValue().getAssigneeId()).isNull();
        assertThat(captor.getValue().getVersion()).isEqualTo(4);
        assertThat(Bug.class.getDeclaredField("assigneeId").getAnnotation(TableField.class).updateStrategy())
                .isEqualTo(FieldStrategy.ALWAYS);
        verify(bugMapper, never()).update(isNull(), any());
        verify(bugMapper, times(2)).selectById("b1");
    }

    @Test
    void updateConflictStopsBeforeRereadAndNotification() {
        when(bugMapper.selectById("b1")).thenReturn(existingBug());
        when(bugMapper.updateById(any(Bug.class))).thenReturn(0);

        Bug patch = new Bug();
        patch.setTitle("并发修改");
        patch.setAssigneeId("");

        assertThatThrownBy(() -> service.update("b1", patch, 4))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被他人修改");

        verify(bugMapper, times(1)).selectById("b1");
        verify(bugMapper, never()).update(isNull(), any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any());
    }

    @Test
    void updateKeepsAssignmentNotification() {
        Bug existing = existingBug();
        Bug reread = existingBug();
        reread.setAssigneeId("new-assignee");
        reread.setTitle("新标题");
        when(bugMapper.selectById("b1")).thenReturn(existing, reread);
        when(bugMapper.updateById(any(Bug.class))).thenReturn(1);

        Bug patch = new Bug();
        patch.setTitle("新标题");
        patch.setAssigneeId("new-assignee");
        service.update("b1", patch, 4);

        verify(notificationService).createNotification(
                eq("new-assignee"), eq("BUG_UPDATE"), eq("Bug 指派变更"),
                contains("新标题"), contains("/projects/p1?tab=bugs"));
    }

    @Test
    void myBugsAreLimitedToCurrentlyVisibleProjectsAndExcludePending() {
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of("p1"));
        when(bugMapper.selectList(any())).thenReturn(List.of());

        service.listMyBugs("u1");

        verify(bugMapper).selectList(org.mockito.ArgumentMatchers.argThat(wrapper -> {
            String sql = wrapper.getSqlSegment();
            return sql.contains("assignee_id") && sql.contains("project_id") && sql.contains("pending");
        }));
    }

    @Test
    void myBugsAreEmptyWhenNoProjectIsVisible() {
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of());

        assertThat(service.listMyBugs("u1")).isEmpty();
        verify(bugMapper, org.mockito.Mockito.never()).selectList(any());
    }
}
