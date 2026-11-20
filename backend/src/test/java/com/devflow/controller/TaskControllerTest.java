package com.devflow.controller;

import com.devflow.entity.Project;
import com.devflow.entity.Task;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.SprintMapper;
import com.devflow.mapper.TaskDependencyMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.NotificationService;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.TaskService;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskControllerTest {

    @Mock private TaskService taskService;
    @Mock private TaskMapper taskMapper;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private TaskDependencyMapper taskDependencyMapper;
    @Mock private AutomationRuleService automationRuleService;
    @Mock private NotificationService notificationService;
    @Mock private SprintMapper sprintMapper;
    @Mock private AccessGuard guard;
    @Mock private WorkflowService workflowService;
    @Mock private ProjectMapper projectMapper;
    @Mock private Authentication authentication;

    @InjectMocks private TaskController controller;

    @Test
    void statusTransitionLocksProjectBeforeValidationAndWrite() throws Exception {
        Task task = new Task();
        task.setId("t1");
        task.setProjectId("p1");
        task.setStatus("TODO");
        when(taskMapper.selectById("t1")).thenReturn(task);
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        Project project = new Project();
        project.setId("p1");
        when(projectMapper.selectForUpdate("p1")).thenReturn(project);

        controller.updateStatus("t1", Map.of("status", "DOING"), authentication);

        var order = inOrder(projectMapper, workflowService, taskService);
        order.verify(projectMapper).selectForUpdate("p1");
        order.verify(workflowService).assertTransitionAllowed("p1", WorkflowService.TYPE_TASK, "TODO", "DOING");
        order.verify(taskService).updateStatus("t1", "DOING", "u1");
        assertThat(TaskController.class
                .getMethod("updateStatus", String.class, Map.class, Authentication.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }
}
