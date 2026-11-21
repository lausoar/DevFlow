package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.entity.Sprint;
import com.devflow.entity.Task;
import com.devflow.entity.User;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.SprintMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.query.QueryExpressionParser;
import com.devflow.security.AccessGuard;
import com.devflow.service.AuditService;
import com.devflow.service.ProjectAccessService;
import com.devflow.util.CsvUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 服务端工作项导出。
 *
 * 为什么不在前端拼 CSV：前端只能导出"已经加载到浏览器"的那一页，行数与列表
 * 不一致；更重要的是导出无法留审计，也无法统一做公式注入防护。这里把导出
 * 收敛到服务端：
 * - 数据按项目可见性口径查询（不依赖前端过滤）；
 * - 每次导出一条 activity_log（谁、何时、导出了多少行），写失败则导出失败；
 * - 统一走 CsvUtils 转义（含公式前缀中和）；
 * - 支持当前筛选与保存视图，避免"看的是一部分、下载的是另一部分"。
 */
@Tag(name = "导出", description = "工作项 CSV 导出（带审计）")
@RestController
@RequiredArgsConstructor
public class ExportController {

    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final ProjectMapper projectMapper;
    private final SprintMapper sprintMapper;
    private final UserMapper userMapper;
    private final ProjectAccessService projectAccessService;
    private final AuditService auditService;
    private final AccessGuard guard;

    @Operation(summary = "导出项目任务 CSV")
    @GetMapping("/projects/{projectId}/export/tasks.csv")
    public void exportTasks(@PathVariable String projectId,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String sprintId,
                            @RequestParam(required = false) String expression,
                            Authentication authentication,
                            HttpServletResponse response) throws IOException {
        String userId = guard.userId(authentication);
        Project project = requireVisibleProject(userId, projectId);

        List<Task> tasks;
        if (StringUtils.hasText(expression)) {
            // 保存的视图是结构化表达式：导出必须与当前视图命中同一集合
            QueryWrapper<Task> wrapper = (QueryWrapper<Task>) new QueryExpressionParser("TASK")
                    .toWrapper(expression, userId, List.of(projectId));
            wrapper.orderByDesc("created_at");
            tasks = taskMapper.selectList(wrapper);
        } else {
            LambdaQueryWrapper<Task> filter = new LambdaQueryWrapper<Task>()
                    .eq(Task::getProjectId, projectId)
                    .eq(StringUtils.hasText(status), Task::getStatus, status)
                    .eq(StringUtils.hasText(sprintId), Task::getSprintId, sprintId)
                    .orderByDesc(Task::getCreatedAt);
            if (StringUtils.hasText(keyword)) {
                filter.and(w -> w.like(Task::getTitle, keyword)
                        .or().like(Task::getDescription, keyword));
            }
            tasks = taskMapper.selectList(filter);
        }

        Map<String, String> names = displayNames(tasks.stream().map(Task::getAssigneeId).toList());
        Map<String, String> sprintNames = sprintNames(projectId);
        List<List<String>> rows = tasks.stream().map(t -> List.of(
                Objects.toString(t.getTitle(), ""),
                Objects.toString(t.getStatus(), ""),
                Objects.toString(t.getPriority(), ""),
                nameOf(names, t.getAssigneeId()),
                t.getSprintId() == null ? "" : sprintNames.getOrDefault(t.getSprintId(), ""),
                t.getStoryPoints() == null ? "" : String.valueOf(t.getStoryPoints()),
                t.getDueDate() == null ? "" : String.valueOf(t.getDueDate()),
                t.getCreatedAt() == null ? "" : String.valueOf(t.getCreatedAt())
        )).toList();

        auditService.logRequired(userId, project.getEnterpriseId(), projectId,
                "export.tasks", "project", projectId, "导出任务 CSV：" + tasks.size() + " 行");
        writeCsv(response, project, "任务",
                List.of("标题", "状态", "优先级", "负责人", "所属迭代", "故事点", "截止日期", "创建时间"), rows);
    }

    @Operation(summary = "导出项目缺陷 CSV")
    @GetMapping("/projects/{projectId}/export/bugs.csv")
    public void exportBugs(@PathVariable String projectId,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String status,
                           @RequestParam(required = false) String severity,
                           @RequestParam(required = false) String priority,
                           @RequestParam(required = false) String expression,
                           Authentication authentication,
                           HttpServletResponse response) throws IOException {
        String userId = guard.userId(authentication);
        Project project = requireVisibleProject(userId, projectId);

        List<Bug> bugs;
        if (StringUtils.hasText(expression)) {
            QueryWrapper<Bug> wrapper = (QueryWrapper<Bug>) new QueryExpressionParser("BUG")
                    .toWrapper(expression, userId, List.of(projectId));
            wrapper.eq("pending", 0).orderByDesc("created_at");
            bugs = bugMapper.selectList(wrapper);
        } else {
            LambdaQueryWrapper<Bug> filter = new LambdaQueryWrapper<Bug>()
                    .eq(Bug::getProjectId, projectId)
                    // 与列表同口径：待分诊的外部提交不进导出
                    .eq(Bug::getPending, 0)
                    .eq(StringUtils.hasText(status), Bug::getStatus, status)
                    .eq(StringUtils.hasText(severity), Bug::getSeverity, severity)
                    .eq(StringUtils.hasText(priority), Bug::getPriority, priority)
                    .orderByDesc(Bug::getCreatedAt);
            if (StringUtils.hasText(keyword)) {
                filter.and(w -> w.like(Bug::getTitle, keyword)
                        .or().like(Bug::getDescription, keyword));
            }
            bugs = bugMapper.selectList(filter);
        }

        Map<String, String> names = displayNames(bugs.stream().map(Bug::getAssigneeId).toList());
        Map<String, String> reporters = displayNames(bugs.stream()
                .filter(b -> !"GUEST".equals(b.getReporterType()))
                .map(Bug::getReporterId).toList());
        List<List<String>> rows = bugs.stream().map(b -> List.of(
                Objects.toString(b.getTitle(), ""),
                Objects.toString(b.getStatus(), ""),
                Objects.toString(b.getSeverity(), ""),
                Objects.toString(b.getPriority(), ""),
                nameOf(names, b.getAssigneeId()),
                "GUEST".equals(b.getReporterType())
                        ? "游客「" + Objects.toString(b.getGuestNickname(), "") + "」"
                        : nameOf(reporters, b.getReporterId()),
                b.getCreatedAt() == null ? "" : String.valueOf(b.getCreatedAt())
        )).toList();

        auditService.logRequired(userId, project.getEnterpriseId(), projectId,
                "export.bugs", "project", projectId, "导出缺陷 CSV：" + bugs.size() + " 行");
        writeCsv(response, project, "缺陷",
                List.of("标题", "状态", "严重程度", "优先级", "负责人", "报告人", "创建时间"), rows);
    }

    private Project requireVisibleProject(String userId, String projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null || !projectAccessService.canView(userId, projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        return project;
    }

    private void writeCsv(HttpServletResponse response, Project project, String label,
                          List<String> headers, List<List<String>> rows) throws IOException {
        String fileName = project.getName() + "-" + label + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"));

        StringBuilder csv = new StringBuilder();
        csv.append('﻿'); // BOM：Excel 直接打开不乱码
        csv.append(headers.stream().map(CsvUtils::escape).collect(Collectors.joining(","))).append("\r\n");
        for (List<String> row : rows) {
            csv.append(row.stream().map(CsvUtils::escape).collect(Collectors.joining(","))).append("\r\n");
        }
        response.getWriter().write(csv.toString());
    }

    private Map<String, String> sprintNames(String projectId) {
        return sprintMapper.selectList(new LambdaQueryWrapper<Sprint>()
                        .eq(Sprint::getProjectId, projectId))
                .stream().collect(Collectors.toMap(Sprint::getId, Sprint::getName, (a, b) -> a));
    }

    /** Map.of() 是只读实现，getOrDefault(null) 会抛 NPE：未指派时必须单独处理 */
    private String nameOf(Map<String, String> names, String userId) {
        return userId == null ? "" : names.getOrDefault(userId, "");
    }

    private Map<String, String> displayNames(List<String> userIds) {
        List<String> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(ids);
        return users.stream().collect(Collectors.toMap(
                User::getId,
                u -> Objects.toString(u.displayName(), ""),
                (a, b) -> a));
    }
}
