package com.devflow.controller;

import com.devflow.dto.request.UpdateRequirementRequest;
import com.devflow.dto.response.RequirementResponse;
import com.devflow.entity.Project;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementChangeLogMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.RequirementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementControllerTest {

    @Mock private RequirementService requirementService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private RequirementMapper requirementMapper;
    @Mock private AccessGuard guard;
    @Mock private RequirementChangeLogMapper changeLogMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private Authentication authentication;

    @InjectMocks private RequirementController controller;

    @Test
    void statusTransitionLocksProjectBeforeServiceValidationAndWrite() throws Exception {
        RequirementResponse detail = RequirementResponse.builder().projectId("p1").build();
        when(requirementService.getRequirementDetail("r1")).thenReturn(detail);
        when(authentication.getPrincipal()).thenReturn("u1");
        when(guard.userId(authentication)).thenReturn("u1");
        when(projectAccessService.canView("u1", "p1")).thenReturn(true);
        Project project = new Project();
        project.setId("p1");
        when(projectMapper.selectForUpdate("p1")).thenReturn(project);
        UpdateRequirementRequest request = new UpdateRequirementRequest();
        request.setStatus("COMPLETED");

        controller.update("r1", request, authentication);

        var order = inOrder(projectMapper, requirementService);
        order.verify(projectMapper).selectForUpdate("p1");
        order.verify(requirementService).updateRequirement("r1", request, "u1");
        assertThat(RequirementController.class
                .getMethod("update", String.class, UpdateRequirementRequest.class, Authentication.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }
}
