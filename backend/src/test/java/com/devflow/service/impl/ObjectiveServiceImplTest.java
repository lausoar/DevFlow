package com.devflow.service.impl;

import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Objective;
import com.devflow.entity.Project;
import com.devflow.mapper.ObjectiveAggregateMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectiveServiceImplTest {

    @Mock private ObjectiveAggregateMapper objectiveMapper;
    @Mock private ProjectMapper projectMapper;
    @Mock private RequirementMapper requirementMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private WorkflowService workflowService;

    @InjectMocks private ObjectiveServiceImpl service;

    private Objective objective;

    @BeforeEach
    void setUp() {
        // LambdaUpdateWrapper 需要 TableInfo 缓存；单测没有跑 MyBatis 扫描，手动注册
        for (Class<?> entity : List.of(Project.class, com.devflow.entity.Requirement.class)) {
            if (com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(entity) == null) {
                com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                        new org.apache.ibatis.builder.MapperBuilderAssistant(
                                new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""),
                        entity);
            }
        }
        objective = new Objective();
        objective.setId("o1");
        objective.setEnterpriseId("e1");
        objective.setName("Q4 平台化");
        objective.setStartDate(LocalDate.of(2026, 10, 1));
        objective.setEndDate(LocalDate.of(2026, 12, 31));
    }

    @Test
    void creatingRequirementLinkRejectsForeignEnterpriseObjective() {
        when(objectiveMapper.selectById("o1")).thenReturn(objective);

        assertThatThrownBy(() -> service.requireInEnterprise("o1", "e2"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(403));
    }

    @Test
    void creatingRequirementLinkAcceptsSameEnterprise() {
        when(objectiveMapper.selectById("o1")).thenReturn(objective);
        assertThat(service.requireInEnterprise("o1", "e1")).isSameAs(objective);
    }

    @Test
    void assignProjectRejectsCrossEnterpriseProject() {
        when(objectiveMapper.selectById("o1")).thenReturn(objective);
        Project project = new Project();
        project.setId("p1");
        project.setEnterpriseId("e2");
        when(projectMapper.selectById("p1")).thenReturn(project);

        assertThatThrownBy(() -> service.assignProject("o1", "p1", "u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于同一企业");
    }

    @Test
    void rollupSeparatesHoursFromRequirementAxis() {
        when(objectiveMapper.selectById("o1")).thenReturn(objective);
        when(objectiveMapper.projectsOfObjective("o1")).thenReturn(List.of(
                Map.of("id", "p1", "name", "平台", "ownerId", "u1", "status", "ACTIVE")));
        when(objectiveMapper.worklogHoursByProject(anyList(), any(), any()))
                .thenReturn(List.of(Map.of("projectId", "p1", "hours", 12.5)));
        when(objectiveMapper.completedTasksByProject(anyList(), any(), any()))
                .thenReturn(List.of(Map.of("projectId", "p1", "n", 3)));
        when(taskMapper.selectList(any())).thenReturn(List.of(
                task("t1", "DONE"), task("t2", "DOING")));
        when(workflowService.statusCodesInCategory("p1", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn(List.of("DONE"));
        when(requirementMapper.selectList(any())).thenReturn(List.of(requirement("r1")));
        when(workflowService.statusCodesInCategory("p1", WorkflowService.TYPE_REQUIREMENT, "DONE"))
                .thenReturn(List.of("COMPLETED"));
        when(objectiveMapper.requirementCountByObjective("o1")).thenReturn(List.of(
                Map.of("projectId", "p1", "status", "COMPLETED", "n", 2)));

        Map<String, Object> result = service.rollup("o1");

        assertThat(result.get("worklogHours")).isEqualTo(12.5);
        assertThat(result.get("completedInPeriod")).isEqualTo(3L);
        assertThat(result.get("totalTasks")).isEqualTo(2L);
        @SuppressWarnings("unchecked")
        Map<String, Object> requirements = (Map<String, Object>) result.get("requirements");
        assertThat(requirements.get("total")).isEqualTo(2L);
        assertThat(requirements.get("done")).isEqualTo(2L);
        assertThat(requirements.get("progress")).isEqualTo(100.0);
    }

    @Test
    void deleteDetachesProjectsAndRequirementsBeforeRemoval() {
        when(objectiveMapper.selectById("o1")).thenReturn(objective);

        service.delete("o1", "u1");

        verify(projectMapper).update(eq(null), any());
        verify(requirementMapper).update(eq(null), any());
        verify(objectiveMapper).deleteById("o1");
    }

    @Test
    void createDefaultsOwnerToOperatorAndStatusActive() {
        ArgumentCaptor<Objective> captor = ArgumentCaptor.forClass(Objective.class);
        Objective input = new Objective();
        input.setName("新目标");

        Objective result = service.create("e1", input, "u9");

        verify(objectiveMapper).insert(captor.capture());
        assertThat(captor.getValue().getOwnerId()).isEqualTo("u9");
        assertThat(captor.getValue().getStatus()).isEqualTo(Objective.STATUS_ACTIVE);
        assertThat(captor.getValue().getEnterpriseId()).isEqualTo("e1");
        assertThat(result.getName()).isEqualTo("新目标");
    }

    @Test
    void createRejectsEndDateBeforeStartDate() {
        Objective input = new Objective();
        input.setName("坏日期");
        input.setStartDate(LocalDate.of(2026, 12, 1));
        input.setEndDate(LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> service.create("e1", input, "u1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结束日期");
    }

    private com.devflow.entity.Task task(String id, String status) {
        com.devflow.entity.Task task = new com.devflow.entity.Task();
        task.setId(id);
        task.setStatus(status);
        return task;
    }

    private com.devflow.entity.Requirement requirement(String id) {
        com.devflow.entity.Requirement requirement = new com.devflow.entity.Requirement();
        requirement.setId(id);
        requirement.setProjectId("p1");
        requirement.setObjectiveId("o1");
        return requirement;
    }
}
