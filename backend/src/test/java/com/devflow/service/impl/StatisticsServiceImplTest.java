package com.devflow.service.impl;

import com.devflow.dto.response.BurndownResponse;
import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.entity.Requirement;
import com.devflow.entity.Sprint;
import com.devflow.entity.Task;
import com.devflow.entity.TaskStatusEvent;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.SprintMapper;
import com.devflow.mapper.StatisticsAggregateMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.TaskStatusEventMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.WorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock private TaskStatusEventMapper taskStatusEventMapper;

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
    @DisplayName("企业需求统计：同一状态码按各项目 REQUIREMENT DONE 分类计算，分布保留原始码")
    void requirementAggregationUsesProjectSpecificDoneCodes() {
        when(projectMapper.selectList(any())).thenReturn(
                List.of(project("pA", "A 项目"), project("pB", "B 项目")));
        when(statisticsAggregateMapper.taskCountByProjectAndStatus(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.bugCountByProjectSeverityStatus(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.requirementCountByProjectAndStatus(any())).thenReturn(List.of(
                Map.of("projectId", "pA", "status", "SHIPPED", "n", 4),
                Map.of("projectId", "pB", "status", "SHIPPED", "n", 3),
                Map.of("projectId", "pA", "status", "CUSTOM_OPEN", "n", 2)
        ));
        when(statisticsAggregateMapper.memberCountByProject(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.distinctMemberCount(any())).thenReturn(0);
        when(statisticsAggregateMapper.taskCreatedPerDay(any(), eq(14))).thenReturn(List.of());
        when(statisticsAggregateMapper.taskCompletedPerDay(any(), eq(14))).thenReturn(List.of());
        when(workflowService.statusCodesInCategory("pA", WorkflowService.TYPE_REQUIREMENT, "DONE"))
                .thenReturn(List.of("SHIPPED"));
        when(workflowService.statusCodesInCategory("pB", WorkflowService.TYPE_REQUIREMENT, "DONE"))
                .thenReturn(List.of("DONE"));

        var stats = service.getEnterpriseStats("ent-1");

        assertThat(stats.getRequirementOverview().getTotalRequirements()).isEqualTo(9);
        assertThat(stats.getRequirementOverview().getCompletedRequirements()).isEqualTo(4);
        assertThat(stats.getRequirementOverview().getOpenRequirements()).isEqualTo(5);
        assertThat(stats.getRequirementStatusDistribution())
                .containsEntry("SHIPPED", 7)
                .containsEntry("CUSTOM_OPEN", 2);
    }

    @Test
    @DisplayName("企业趋势：直接汇总进入 DONE 的事件行，不按当前状态码二次过滤")
    void taskTrendUsesDoneTransitionRows() {
        String today = LocalDate.now().toString();
        when(projectMapper.selectList(any())).thenReturn(List.of(project("pA", "A 项目")));
        when(statisticsAggregateMapper.taskCountByProjectAndStatus(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.bugCountByProjectSeverityStatus(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.requirementCountByProjectAndStatus(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.memberCountByProject(any())).thenReturn(List.of());
        when(statisticsAggregateMapper.distinctMemberCount(any())).thenReturn(0);
        when(statisticsAggregateMapper.taskCreatedPerDay(any(), eq(14))).thenReturn(List.of());
        when(statisticsAggregateMapper.taskCompletedPerDay(any(), eq(14))).thenReturn(List.of(
                Map.of("projectId", "pA", "d", today, "n", 2)));

        var stats = service.getEnterpriseStats("ent-1");

        assertThat(stats.getTaskTrend())
                .filteredOn(p -> p.getDate().equals(today))
                .singleElement()
                .satisfies(p -> assertThat(p.getCompleted()).isEqualTo(2));
    }

    private Sprint sprint(LocalDate start) {
        Sprint sprint = new Sprint();
        sprint.setId("s1");
        sprint.setProjectId("p1");
        sprint.setName("进行中");
        sprint.setStatus("ACTIVE");
        sprint.setStartDate(start);
        sprint.setEndDate(start.plusDays(10));
        return sprint;
    }

    private Task task(String id, int points, String status) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId("p1");
        task.setSprintId("s1");
        task.setStatus(status);
        task.setStoryPoints(points);
        return task;
    }

    private TaskStatusEvent event(String id, String taskId, String fromCategory,
                                  String toStatus, String toCategory, LocalDateTime createdAt) {
        TaskStatusEvent event = new TaskStatusEvent();
        event.setId(id);
        event.setTaskId(taskId);
        event.setProjectId("p1");
        event.setFromStatus("previous");
        event.setToStatus(toStatus);
        event.setFromCategory(fromCategory);
        event.setToCategory(toCategory);
        event.setCreatedAt(createdAt);
        return event;
    }

    private void stubBurndown(Sprint sprint, List<Task> tasks, List<TaskStatusEvent> events) {
        when(sprintMapper.selectById(sprint.getId())).thenReturn(sprint);
        when(taskMapper.selectList(any())).thenReturn(tasks);
        when(taskStatusEventMapper.selectList(any())).thenReturn(events);
    }

    private BurndownResponse.BurndownPoint point(BurndownResponse response, LocalDate date) {
        return response.getDataPoints().stream()
                .filter(p -> p.getDate().equals(date.toString()))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("燃尽图：迭代尚未开始时 dataPoints 为空，返回 200 而不是抛异常")
    void burndownWithFutureSprintDoesNotCrash() {
        Sprint sprint = sprint(LocalDate.now().plusDays(10));
        sprint.setName("未来迭代");
        sprint.setStatus("PLANNING");
        sprint.setEndDate(LocalDate.now().plusDays(24));
        when(sprintMapper.selectById("s1")).thenReturn(sprint);
        when(taskMapper.selectList(any())).thenReturn(List.of());

        BurndownResponse resp = service.getSprintBurndown("p1", "s1");

        assertThat(resp).isNotNull();
        assertThat(resp.getDataPoints()).isEmpty();
        assertThat(resp.getVelocity()).isZero();
        assertThat(resp.getEstimatedCompletionDate()).isNull();
    }

    @Test
    @DisplayName("燃尽图：按回填 DONE 事件归日，而不是当前 completed_at/updated_at")
    void burndownUsesBackfilledDoneEventInsteadOfCurrentTimestamps() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        Sprint sprint = sprint(today.minusDays(4));
        Task done = task("t1", 5, "DONE");
        done.setCompletedAt(today.atTime(8, 0));
        done.setUpdatedAt(LocalDateTime.now());
        stubBurndown(sprint, List.of(done), List.of(
                event("e1", "t1", null, "DONE", "DONE", yesterday.atTime(10, 0))));

        BurndownResponse resp = service.getSprintBurndown("p1", "s1");

        var completed = point(resp, yesterday);
        assertThat(completed.getActualRemaining()).isZero();
        assertThat(completed.getCompletedPoints()).isEqualTo(5);
        assertThat(completed.getCompletedTasks()).isEqualTo(1);
        assertThat(point(resp, yesterday.minusDays(1)).getActualRemaining()).isEqualTo(5);
    }

    @Test
    @DisplayName("燃尽图：当前 DONE 但没有事件时不伪造完成历史")
    void burndownDoesNotInferCompletionWithoutEvents() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        Sprint sprint = sprint(today.minusDays(2));
        Task done = task("t1", 5, "DONE");
        done.setCompletedAt(yesterday.atTime(10, 0));
        stubBurndown(sprint, List.of(done), List.of());

        BurndownResponse resp = service.getSprintBurndown("p1", "s1");

        assertThat(point(resp, yesterday).getActualRemaining()).isEqualTo(5);
        assertThat(point(resp, yesterday).getCompletedTasks()).isZero();
        assertThat(point(resp, today).getActualRemaining()).isEqualTo(5);
        assertThat(resp.getDataPoints()).allMatch(p -> p.getCompletedPoints() == 0);
    }

    @Test
    @DisplayName("燃尽图：重开恢复剩余点，其他任务当天完成量保持非负")
    void burndownReopenRestoresRemainingAndCountsOtherCompletions() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        Sprint sprint = sprint(today.minusDays(2));
        Task reopened = task("t1", 5, "DOING");
        Task completed = task("t2", 3, "DONE");
        // 故意乱序，服务必须按 task/time 排序后重建。
        stubBurndown(sprint, List.of(reopened, completed), List.of(
                event("e3", "t2", "TODO", "DONE", "DONE", today.atTime(9, 0)),
                event("e2", "t1", "DONE", "DOING", "IN_PROGRESS", today.atTime(8, 0)),
                event("e1", "t1", "TODO", "DONE", "DONE", yesterday.atTime(10, 0))));

        BurndownResponse resp = service.getSprintBurndown("p1", "s1");

        var originalCompletion = point(resp, yesterday);
        assertThat(originalCompletion.getActualRemaining()).isEqualTo(3);
        assertThat(originalCompletion.getCompletedPoints()).isEqualTo(5);
        assertThat(originalCompletion.getCompletedTasks()).isEqualTo(1);
        var reopenDay = point(resp, today);
        assertThat(reopenDay.getActualRemaining()).isEqualTo(5);
        assertThat(reopenDay.getCompletedPoints()).isEqualTo(3);
        assertThat(reopenDay.getCompletedTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("燃尽图：昨天 DONE、今天重开后再次 DONE，第二次完成单独计数")
    void burndownCountsSecondCompletionAfterReopenWithCustomCategories() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        Sprint sprint = sprint(today.minusDays(2));
        Task customDone = task("t1", 5, "ACCEPTED");
        stubBurndown(sprint, List.of(customDone), List.of(
                event("e1", "t1", "TODO", "SHIPPED", "DONE", yesterday.atTime(10, 0)),
                event("e2", "t1", "DONE", "REWORK", "IN_PROGRESS", today.atTime(9, 0)),
                event("e3", "t1", "IN_PROGRESS", "ACCEPTED", "DONE", today.atTime(17, 0))));

        BurndownResponse resp = service.getSprintBurndown("p1", "s1");

        var firstCompletion = point(resp, yesterday);
        assertThat(firstCompletion.getActualRemaining()).isZero();
        assertThat(firstCompletion.getCompletedPoints()).isEqualTo(5);
        assertThat(firstCompletion.getCompletedTasks()).isEqualTo(1);
        var secondCompletion = point(resp, today);
        assertThat(secondCompletion.getActualRemaining()).isZero();
        assertThat(secondCompletion.getCompletedPoints()).isEqualTo(5);
        assertThat(secondCompletion.getCompletedTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("燃尽图：同日完成后重开按日终状态处理，不记虚假完成")
    void burndownSameDayReopenUsesEndOfDayState() {
        LocalDate today = LocalDate.now();
        Sprint sprint = sprint(today.minusDays(1));
        Task reopened = task("t1", 5, "DOING");
        stubBurndown(sprint, List.of(reopened), List.of(
                event("e1", "t1", "TODO", "DONE", "DONE", today.atTime(9, 0)),
                event("e2", "t1", "DONE", "DOING", "IN_PROGRESS", today.atTime(11, 0))));

        BurndownResponse resp = service.getSprintBurndown("p1", "s1");

        var sameDay = point(resp, today);
        assertThat(sameDay.getActualRemaining()).isEqualTo(5);
        assertThat(sameDay.getCompletedPoints()).isZero();
        assertThat(sameDay.getCompletedTasks()).isZero();
    }

    @Test
    @DisplayName("燃尽图：最早事件之前采用其 fromCategory")
    void burndownUsesEarliestEventFromCategory() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        Sprint sprint = sprint(today.minusDays(2));
        Task reopened = task("t1", 5, "DOING");
        stubBurndown(sprint, List.of(reopened), List.of(
                event("e1", "t1", "DONE", "DOING", "IN_PROGRESS", yesterday.atTime(9, 0))));

        BurndownResponse resp = service.getSprintBurndown("p1", "s1");

        var beforeFirstEvent = point(resp, today.minusDays(2));
        assertThat(beforeFirstEvent.getActualRemaining()).isZero();
        assertThat(beforeFirstEvent.getCompletedTasks()).isZero();
        assertThat(point(resp, yesterday).getActualRemaining()).isEqualTo(5);
    }
}
