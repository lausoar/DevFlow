package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.devflow.entity.Sprint;
import com.devflow.entity.Task;
import com.devflow.mapper.SprintMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.WorkflowService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SprintControllerTest {

    @Mock private SprintMapper sprintMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private AccessGuard guard;
    @Mock private WorkflowService workflowService;
    @Mock private Authentication authentication;

    @InjectMocks private SprintController controller;

    @BeforeAll
    static void initTableInfo() {
        Configuration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Task.class);
    }

    @BeforeEach
    void allowManagement() {
        when(authentication.getPrincipal()).thenReturn("manager");
        when(guard.userId(authentication)).thenReturn("manager");
        when(projectAccessService.canView("manager", "p1")).thenReturn(true);
    }

    private Sprint sprint(String id, String status) {
        Sprint sprint = new Sprint();
        sprint.setId(id);
        sprint.setProjectId("p1");
        sprint.setStatus(status);
        return sprint;
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void activateCompletesOldSprintAndMovesOnlyNonDoneTasks() {
        Sprint target = sprint("s-new", "PLANNING");
        Sprint running = sprint("s-old", "ACTIVE");
        when(sprintMapper.selectById("s-new")).thenReturn(target);
        when(sprintMapper.selectList(any())).thenReturn(List.of(running));
        when(workflowService.statusCodesInCategory("p1", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn(List.of("SHIPPED"));
        when(taskMapper.selectList(any())).thenReturn(List.of(new Task()));

        controller.activate("s-new", authentication);

        assertThat(running.getStatus()).isEqualTo("COMPLETED");
        assertThat(target.getStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(taskMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<Task> move = captor.getValue();
        move.getSqlSegment();
        move.getSqlSet();
        assertThat(move.getParamNameValuePairs().values()).contains("s-old", "SHIPPED");
        assertThat(move.getParamNameValuePairs().values()).containsNull();
    }

    @Test
    void completeUsesSameHelperAndKeepsResponseShape() {
        Sprint sprint = sprint("s1", "ACTIVE");
        when(sprintMapper.selectById("s1")).thenReturn(sprint);
        when(workflowService.statusCodesInCategory("p1", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn(List.of("SHIPPED"));
        when(taskMapper.selectList(any())).thenReturn(List.of(new Task(), new Task()));

        var response = controller.complete("s1", authentication);

        Map<String, Object> data = response.getData();
        assertThat(data.get("sprint")).isEqualTo(sprint);
        assertThat(data.get("movedOut")).isEqualTo(2);
        assertThat(sprint.getStatus()).isEqualTo("COMPLETED");
        verify(taskMapper).update(isNull(), any());
    }

    @Test
    void completeDoesNotIssueMoveWhenEverythingIsDone() {
        Sprint sprint = sprint("s1", "ACTIVE");
        when(sprintMapper.selectById("s1")).thenReturn(sprint);
        when(workflowService.statusCodesInCategory("p1", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn(List.of("SHIPPED"));
        when(taskMapper.selectList(any())).thenReturn(List.of());

        var response = controller.complete("s1", authentication);

        assertThat(response.getData().get("movedOut")).isEqualTo(0);
        verify(taskMapper, never()).update(any(), any());
    }
}
