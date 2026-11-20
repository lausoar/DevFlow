package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.result.R;
import com.devflow.entity.Bug;
import com.devflow.entity.Deployment;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.DeploymentMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InboxControllerTest {

    @Mock private TaskMapper taskMapper;
    @Mock private BugMapper bugMapper;
    @Mock private DeploymentMapper deploymentMapper;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private PermissionService permissionService;
    @Mock private WorkflowService workflowService;
    @Mock private AccessGuard guard;
    @Mock private Authentication authentication;

    @InjectMocks private InboxController controller;

    @Test
    void noVisibleProjectsReturnsEmptyBucketsWithoutQuerying() {
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of());

        Map<String, Object> data = controller.inbox(authentication).getData();

        assertThat(data.get("total")).isEqualTo(0);
        assertThat((List<?>) data.get("assignments")).isEmpty();
    }

    @Test
    void assignmentBucketSkipsTasksInDoneCategory() {
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of("p1"));
        Task done = task("t1", "DONE", "u1");
        Task doing = task("t2", "DOING", "u1");
        when(taskMapper.selectList(any())).thenReturn(List.of(done, doing));
        when(workflowService.statusCodesInCategory("p1", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn(List.of("DONE"));
        when(deploymentMapper.selectList(any())).thenReturn(List.of());
        when(bugMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = controller.inbox(authentication).getData();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) data.get("assignments");
        assertThat(assignments).hasSize(1);
        assertThat(assignments.get(0).get("id")).isEqualTo("t2");
    }

    @Test
    void approvalBucketExcludesOwnDeploymentAndCallersWithoutEditPermission() {
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of("p1", "p2"));
        Deployment own = deployment("d1", "p1", "u1");
        Deployment foreign = deployment("d2", "p2", "u2");
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(deploymentMapper.selectList(any())).thenReturn(List.of(own, foreign));
        when(permissionService.hasProjectPermission("u1", "p2", com.devflow.common.PermissionCodes.PROJECT_EDIT))
                .thenReturn(false);
        when(bugMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> data = controller.inbox(authentication).getData();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> approvals = (List<Map<String, Object>>) data.get("approvals");
        assertThat(approvals).isEmpty();
    }

    @Test
    void triageBucketRequiresBugEditPermission() {
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectAccessService.visibleProjectIds("u1")).thenReturn(List.of("p1"));
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(deploymentMapper.selectList(any())).thenReturn(List.of());
        Bug guestBug = new Bug();
        guestBug.setId("b1");
        guestBug.setProjectId("p1");
        guestBug.setTitle("游客反馈");
        guestBug.setPending(1);
        when(bugMapper.selectList(any())).thenReturn(List.of(guestBug));
        when(permissionService.hasProjectPermission(eq("u1"), eq("p1"), anyString())).thenReturn(true);

        Map<String, Object> data = controller.inbox(authentication).getData();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> triage = (List<Map<String, Object>>) data.get("triage");
        assertThat(triage).hasSize(1);
        assertThat(data.get("total")).isEqualTo(1);
    }

    private Task task(String id, String status, String assignee) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId("p1");
        task.setTitle("任务 " + id);
        task.setStatus(status);
        task.setAssigneeId(assignee);
        return task;
    }

    private Deployment deployment(String id, String projectId, String triggeredBy) {
        Deployment deployment = new Deployment();
        deployment.setId(id);
        deployment.setProjectId(projectId);
        deployment.setStatus("PENDING_APPROVAL");
        deployment.setTriggeredBy(triggeredBy);
        deployment.setVersion("v1");
        return deployment;
    }
}
