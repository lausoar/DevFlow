package com.devflow.controller;

import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.mapper.ProjectMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.BugService;
import com.devflow.service.GuestService;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
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
class BugControllerTest {

    @Mock private BugService bugService;
    @Mock private PermissionService permissionService;
    @Mock private GuestService guestService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private AccessGuard guard;
    @Mock private WorkflowService workflowService;
    @Mock private ProjectMapper projectMapper;
    @Mock private Authentication authentication;

    @InjectMocks private BugController controller;

    @Test
    void statusTransitionLocksProjectBeforeValidationAndWrite() throws Exception {
        Bug bug = new Bug();
        bug.setId("b1");
        bug.setProjectId("p1");
        bug.setStatus("NEW");
        bug.setAssigneeId("u1");
        bug.setReporterId("reporter");
        when(bugService.getById("b1")).thenReturn(bug);
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        Project project = new Project();
        project.setId("p1");
        when(projectMapper.selectForUpdate("p1")).thenReturn(project);

        controller.changeStatus("b1", Map.of("status", "DOING"), authentication);

        var order = inOrder(projectMapper, workflowService, bugService);
        order.verify(projectMapper).selectForUpdate("p1");
        order.verify(workflowService).assertTransitionAllowed("p1", WorkflowService.TYPE_BUG, "NEW", "DOING");
        order.verify(bugService).changeStatus("b1", "DOING", "u1");
        assertThat(BugController.class
                .getMethod("changeStatus", String.class, Map.class, Authentication.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }
}
