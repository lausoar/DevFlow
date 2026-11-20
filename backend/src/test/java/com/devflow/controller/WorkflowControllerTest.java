package com.devflow.controller;

import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Workflow;
import com.devflow.entity.Project;
import com.devflow.entity.WorkflowStatus;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.WorkflowMapper;
import com.devflow.mapper.WorkflowStatusMapper;
import com.devflow.mapper.WorkflowTransitionMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.WorkflowService;
import com.devflow.service.impl.WorkflowServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

    @Mock private WorkflowService workflowService;
    @Mock private WorkflowServiceImpl workflowServiceImpl;
    @Mock private WorkflowMapper workflowMapper;
    @Mock private WorkflowStatusMapper statusMapper;
    @Mock private WorkflowTransitionMapper transitionMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private BugMapper bugMapper;
    @Mock private RequirementMapper requirementMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private AccessGuard guard;
    @Mock private Authentication authentication;

    private WorkflowController controller;

    @BeforeEach
    void allowProjectManagement() {
        controller = new WorkflowController(workflowService, workflowServiceImpl, workflowMapper,
                statusMapper, transitionMapper, taskMapper, bugMapper, requirementMapper, projectMapper, guard);
        Project project = new Project();
        project.setId("p1");
        when(projectMapper.selectForUpdate("p1")).thenReturn(project);
        when(guard.userId(authentication)).thenReturn("manager");
    }

    private WorkflowController.StatusItem status(String code, String category, boolean initial) {
        WorkflowController.StatusItem item = new WorkflowController.StatusItem();
        item.setCode(code);
        item.setName(code);
        item.setCategory(category);
        item.setIsInitial(initial);
        return item;
    }

    private WorkflowController.UpdateWorkflowRequest request(WorkflowController.StatusItem... items) {
        WorkflowController.UpdateWorkflowRequest request = new WorkflowController.UpdateWorkflowRequest();
        request.setStatuses(List.of(items));
        return request;
    }

    private Workflow workflow(String id, String projectId, String issueType) {
        Workflow workflow = new Workflow();
        workflow.setId(id);
        workflow.setProjectId(projectId);
        workflow.setIssueType(issueType);
        return workflow;
    }

    private WorkflowStatus workflowStatus(String code) {
        WorkflowStatus status = new WorkflowStatus();
        status.setCode(code);
        return status;
    }

    @Test
    void updateRejectsMissingInUseCodeBeforeAnyDelete() {
        when(taskMapper.selectUsedStatusCodes("p1")).thenReturn(List.of("TODO", "DOING"));

        assertThatThrownBy(() -> controller.update("p1", WorkflowService.TYPE_TASK,
                request(status("TODO", WorkflowStatus.CATEGORY_TODO, true),
                        status("DONE", WorkflowStatus.CATEGORY_DONE, false)), authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DOING");

        var order = inOrder(projectMapper, taskMapper);
        order.verify(projectMapper).selectForUpdate("p1");
        order.verify(taskMapper).selectUsedStatusCodes("p1");
        verify(workflowService, never()).ensureProjectWorkflow(any(), any(), any());
        verify(transitionMapper, never()).delete(any());
        verify(statusMapper, never()).delete(any());
    }

    @Test
    void updateAllowsCompleteSurvivingCodeSet() {
        when(taskMapper.selectUsedStatusCodes("p1")).thenReturn(List.of("TODO", "DOING"));
        Workflow projectWorkflow = workflow("wf-project", "p1", WorkflowService.TYPE_TASK);
        when(workflowService.ensureProjectWorkflow("p1", WorkflowService.TYPE_TASK, "manager"))
                .thenReturn(projectWorkflow);
        when(workflowServiceImpl.describe("p1", WorkflowService.TYPE_TASK)).thenReturn(List.of());

        var response = controller.update("p1", WorkflowService.TYPE_TASK,
                request(status("TODO", WorkflowStatus.CATEGORY_TODO, true),
                        status("DOING", WorkflowStatus.CATEGORY_IN_PROGRESS, false)), authentication);

        assertThat(response.getData().get("workflow")).isEqualTo(projectWorkflow);
        verify(transitionMapper).delete(any());
        verify(statusMapper).delete(any());
        verify(statusMapper, times(2)).insert(any(WorkflowStatus.class));
        verify(workflowServiceImpl).reload();
    }

    @Test
    void resetValidatesAgainstGlobalFallbackBeforeAnyDelete() {
        Workflow own = workflow("wf-own", "p1", WorkflowService.TYPE_BUG);
        when(workflowMapper.selectList(any())).thenReturn(List.of(own));
        Workflow global = workflow("wf-global", null, WorkflowService.TYPE_BUG);
        when(workflowService.resolve(null, WorkflowService.TYPE_BUG)).thenReturn(global);
        when(workflowService.statuses("wf-global"))
                .thenReturn(List.of(workflowStatus("NEW"), workflowStatus("CLOSED")));
        when(bugMapper.selectUsedStatusCodes("p1")).thenReturn(List.of("NEW", "TRIAGE"));

        assertThatThrownBy(() -> controller.reset("p1", WorkflowService.TYPE_BUG, authentication))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("TRIAGE");

        var order = inOrder(projectMapper, bugMapper);
        order.verify(projectMapper).selectForUpdate("p1");
        order.verify(bugMapper).selectUsedStatusCodes("p1");
        verify(transitionMapper, never()).delete(any());
        verify(statusMapper, never()).delete(any());
        verify(workflowMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void resetAllowsCodesPresentInActualGlobalFallback() {
        Workflow own = workflow("wf-own", "p1", WorkflowService.TYPE_REQUIREMENT);
        when(workflowMapper.selectList(any())).thenReturn(List.of(own));
        Workflow global = workflow("wf-global", null, WorkflowService.TYPE_REQUIREMENT);
        when(workflowService.resolve(null, WorkflowService.TYPE_REQUIREMENT)).thenReturn(global);
        when(workflowService.statuses("wf-global"))
                .thenReturn(List.of(workflowStatus("OPEN"), workflowStatus("CUSTOM_DONE")));
        when(requirementMapper.selectUsedStatusCodes("p1"))
                .thenReturn(List.of("OPEN", "CUSTOM_DONE"));

        controller.reset("p1", WorkflowService.TYPE_REQUIREMENT, authentication);

        verify(transitionMapper).delete(any());
        verify(statusMapper).delete(any());
        verify(workflowMapper).deleteById("wf-own");
        verify(workflowServiceImpl).reload();
    }
}
