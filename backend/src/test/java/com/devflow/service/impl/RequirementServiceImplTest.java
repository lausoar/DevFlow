package com.devflow.service.impl;

import com.devflow.common.exception.BusinessException;
import com.devflow.dto.response.RequirementResponse;
import com.devflow.entity.Project;
import com.devflow.entity.Requirement;
import com.devflow.entity.RequirementTask;
import com.devflow.entity.Task;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.RequirementApprovalMapper;
import com.devflow.mapper.RequirementChangeLogMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.RequirementTaskMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.UserMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequirementServiceImplTest {

    @Mock private RequirementMapper requirementMapper;
    @Mock private WorkflowService workflowService;
    @Mock private RequirementApprovalMapper approvalMapper;
    @Mock private RequirementTaskMapper requirementTaskMapper;
    @Mock private RequirementChangeLogMapper changeLogMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private ProjectMemberMapper projectMemberMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks private RequirementServiceImpl service;

    private Requirement requirement() {
        Requirement requirement = new Requirement();
        requirement.setId("r1");
        requirement.setProjectId("p1");
        requirement.setRequesterId("requester");
        requirement.setTitle("需求");
        return requirement;
    }

    private Task task(String id, String projectId) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId(projectId);
        task.setTitle("任务 " + id);
        task.setStatus("DOING");
        return task;
    }

    private RequirementTask relation(String taskId) {
        RequirementTask relation = new RequirementTask();
        relation.setRequirementId("r1");
        relation.setTaskId(taskId);
        return relation;
    }

    @Test
    void linkTaskAllowsSameProject() {
        when(requirementMapper.selectById("r1")).thenReturn(requirement());
        when(taskMapper.selectById("t1")).thenReturn(task("t1", "p1"));
        when(requirementTaskMapper.selectCount(any())).thenReturn(0L);

        service.linkTask("r1", "t1");

        ArgumentCaptor<RequirementTask> captor = ArgumentCaptor.forClass(RequirementTask.class);
        verify(requirementTaskMapper).insert(captor.capture());
        assertThat(captor.getValue().getRequirementId()).isEqualTo("r1");
        assertThat(captor.getValue().getTaskId()).isEqualTo("t1");
    }

    @Test
    void linkTaskRejectsCrossProjectBeforeRelationWrite() {
        when(requirementMapper.selectById("r1")).thenReturn(requirement());
        when(taskMapper.selectById("t-other")).thenReturn(task("t-other", "p2"));

        assertThatThrownBy(() -> service.linkTask("r1", "t-other"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于同一项目");

        verify(requirementTaskMapper, never()).selectCount(any());
        verify(requirementTaskMapper, never()).insert(any(RequirementTask.class));
    }

    @Test
    void responseFiltersLegacyCrossProjectTaskBeforeResolvingDetails() {
        Requirement requirement = requirement();
        when(requirementMapper.selectById("r1")).thenReturn(requirement);
        Project project = new Project();
        project.setId("p1");
        project.setName("项目一");
        when(projectMapper.selectById("p1")).thenReturn(project);
        when(requirementTaskMapper.selectList(any()))
                .thenReturn(List.of(relation("t1"), relation("t-legacy")));

        Task visible = task("t1", "p1");
        Task legacy = task("t-legacy", "p2");
        legacy.setAssigneeId("cross-project-assignee");
        when(taskMapper.selectById("t1")).thenReturn(visible);
        when(taskMapper.selectById("t-legacy")).thenReturn(legacy);

        RequirementResponse response = service.getRequirementDetail("r1");

        assertThat(response.getLinkedTasks()).hasSize(1);
        assertThat(response.getLinkedTasks().get(0).getTaskId()).isEqualTo("t1");
        verify(userMapper, never()).selectById("cross-project-assignee");
    }
}
