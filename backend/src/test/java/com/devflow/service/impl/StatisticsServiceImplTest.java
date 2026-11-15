package com.devflow.service.impl;

import com.devflow.dto.response.BurndownResponse;
import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.entity.Requirement;
import com.devflow.entity.Sprint;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.SprintMapper;
import com.devflow.mapper.StatisticsAggregateMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 统计聚合的回归测试。
 *
 * 重点不是"数字对不对"（那由集成验证覆盖），而是两件单测才能钉住的事：
 *   1. 空数据不崩 —— 燃尽图曾对空 dataPoints 直接 get(size-1)，未来迭代必崩 500；
 *   2. **多项目自定义工作流时口径不串味** —— 这是把统计从内存过滤下推到 SQL 时
 *      最容易写错的地方：如果把所有项目的 DONE 状态码取并集塞进一条 SQL，
 *      A 项目某个"进行中"状态恰好与 B 项目的"完成"状态同名时就会被算错。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticsServiceImpl 聚合口径与空数据")
class StatisticsServiceImplTest {

    @Mock private ProjectMapper projectMapper;
    @Mock private TaskMapper taskMapper;
    @Mock private BugMapper bugMapper;
    @Mock private SprintMapper sprintMapper;
    @Mock private RequirementMapper requirementMapper;
    @Mock private com.devflow.mapper.ProjectMemberMapper projectMemberMapper;
    @Mock private UserMapper userMapper;
    @Mock private WorkflowService workflowService;
    @Mock private StatisticsAggregateMapper statisticsAggregateMapper;

    @InjectMocks private StatisticsServiceImpl service;

    private Project project(String id, String name) {
        Project p = new Project();
        p.setId(id);
        p.setName(name);
        p.setStatus("ACTIVE");
        return p;
    }

    @Test
    @DisplayName("没有项目时返回空结构，不抛异常")
    void emptyEnterpriseReturnsEmptyStructure() {
        when(projectMapper.selectList(any())).thenReturn(List.of());

        var stats = service.getEnterpriseStats("ent-1");

        assertThat(stats.getTaskOverview()).isNotNull();
        assertThat(stats.getTaskOverview().getTotalTasks()).isZero();
        assertThat(stats.getProjectProgressList()).isEmpty();
    }

    @Test
    @DisplayName("多项目自定义工作流：同名状态按各自分类归类，不串味")
    void sameStatusNameDifferentCategoryIsNotConfused() {
        // A 项目的 FINISHED 属于 DONE 分类；B 项目的 FINISHED 属于 IN_PROGRESS
        when(projectMapper.selectList(any())).thenReturn(
                List.of(project("pA", "A 项目"), project("pB", "B 项目")));

        when(statisticsAggregateMapper.taskCountByProjectAndStatus(any())).thenReturn(List.of(
                Map.of("projectId", "pA", "status", "FINISHED", "n", 3, "overdue", 0),
                Map.of("projectId", "pB", "status", "FINISHED", "n", 5, "overdue", 0),
                Map.of("projectId", "pA", "status", "TODO", "n", 2, "overdue", 1)
        ));
        when(statisticsAggregateMapper.bugCountByProjectSeverityStatus(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.requirementCountByProjectAndStatus(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.memberCountByProject(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.distinctMemberCount(any())).thenReturn(0);
        when(statisticsAggregateMapper.taskCreatedPerDay(any(), eq(14))).thenReturn(List.of());
        when(statisticsAggregateMapper.taskCompletedPerDay(any(), eq(14))).thenReturn(List.of());

        when(workflowService.statusCodesInCategory("pA", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn(List.of("FINISHED"));
        when(workflowService.statusCodesInCategory("pA", WorkflowService.TYPE_TASK, "IN_PROGRESS"))
                .thenReturn(List.of("DOING"));
        when(workflowService.statusCodesInCategory("pB", WorkflowService.TYPE_TASK, "DONE"))
                .thenReturn(List.of("DONE"));
        when(workflowService.statusCodesInCategory("pB", WorkflowService.TYPE_TASK, "IN_PROGRESS"))
                .thenReturn(List.of("FINISHED"));

        var stats = service.getEnterpriseStats("ent-1");

        // pA 的 3 条 FINISHED 是完成；pB 的 5 条 FINISHED 是进行中
        assertThat(stats.getTaskOverview().getCompletedTasks()).isEqualTo(3);
        assertThat(stats.getTaskOverview().getInProgressTasks()).isEqualTo(5);
        assertThat(stats.getTaskOverview().getTotalTasks()).isEqualTo(10);
        assertThat(stats.getTaskOverview().getOverdueTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("燃尽图：迭代尚未开始时 dataPoints 为空，返回 200 而不是抛异常")
    void burndownWithFutureSprintDoesNotCrash() {
        Sprint sprint = new Sprint();
        sprint.setId("s1");
        sprint.setProjectId("p1");
        sprint.setName("未来迭代");
        sprint.setStatus("PLANNING");
        sprint.setStartDate(LocalDate.now().plusDays(10));
        sprint.setEndDate(LocalDate.now().plusDays(24));
        when(sprintMapper.selectById("s1")).thenReturn(sprint);
        when(taskMapper.selectList(any())).thenReturn(List.of());
        when(workflowService.statusCodesInCategory(anyString(), anyString(), anyString()))
                .thenReturn(List.of("DONE"));

        BurndownResponse resp = service.getSprintBurndown("p1", "s1");

        assertThat(resp).isNotNull();
        assertThat(resp.getDataPoints()).isEmpty();
        assertThat(resp.getVelocity()).isZero();
        assertThat(resp.getEstimatedCompletionDate()).isNull();
    }

    @Test
    @DisplayName("燃尽图：按 completed_at 归日，而不是 updated_at")
    void burndownUsesCompletedAtNotUpdatedAt() {
        LocalDate start = LocalDate.now().minusDays(4);
        Sprint sprint = new Sprint();
        sprint.setId("s1");
        sprint.setProjectId("p1");
        sprint.setName("进行中");
        sprint.setStatus("ACTIVE");
        sprint.setStartDate(start);
        sprint.setEndDate(start.plusDays(10));
        when(sprintMapper.selectById("s1")).thenReturn(sprint);
        when(workflowService.statusCodesInCategory(anyString(), anyString(), anyString()))
                .thenReturn(List.of("DONE"));

        Task done = new Task();
        done.setId("t1");
        done.setStatus("DONE");
        done.setStoryPoints(5);
        // 昨天完成，但今天被改过标题 → updated_at 是今天
        done.setCompletedAt(LocalDate.now().minusDays(1).atTime(10, 0));
        done.setUpdatedAt(java.time.LocalDateTime.now());
        when(taskMapper.selectList(any())).thenReturn(List.of(done));

        BurndownResponse resp = service.getSprintBurndown("p1", "s1");

        // 昨天那一点应已扣减，而不是今天
        var yesterday = resp.getDataPoints().stream()
                .filter(p -> p.getDate().equals(LocalDate.now().minusDays(1).toString()))
                .findFirst().orElseThrow();
        assertThat(yesterday.getActualRemaining()).isZero();
        assertThat(yesterday.getCompletedTasks()).isEqualTo(1);
    }
}
