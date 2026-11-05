package com.devflow.controller;

import com.alibaba.excel.EasyExcel;
import com.devflow.common.result.R;
import com.devflow.dto.response.*;
import com.devflow.entity.Project;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.Task;
import com.devflow.entity.User;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.StatisticsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统计报表控制器
 */
@Slf4j
@Tag(name = "统计报表", description = "企业级统计、项目健康度、成员工作量、燃尽图、甘特图、导出")
@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final ProjectAccessService projectAccessService;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;

    // ============================================
    // 企业级统计
    // ============================================
    @Operation(summary = "获取企业级统计报表")
    @GetMapping("/enterprise")
    // 报表口径拍板（2026-10-26）：企业统计为企业级管理视角，全员可见（不做成员级过滤）；
    // 项目级端点（health/workload/burndown/gantt）按项目可见性收口。
    public R<EnterpriseStatsResponse> getEnterpriseStats(Authentication authentication) {
        String userId = authentication.getName();
        User user = userMapper.selectById(userId);
        if (user == null || user.getEnterpriseId() == null) {
            return R.fail("用户未关联企业");
        }
        EnterpriseStatsResponse stats = statisticsService.getEnterpriseStats(user.getEnterpriseId());
        return R.ok(stats);
    }

    // ============================================
    // 项目健康度
    // ============================================
    @Operation(summary = "获取项目健康度评估")
    @GetMapping("/projects/{projectId}/health")
    public R<ProjectHealthResponse> getProjectHealth(@PathVariable String projectId, Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new com.devflow.common.exception.BusinessException(404, "项目不存在或已被删除");
        }

        ProjectHealthResponse health = statisticsService.getProjectHealth(projectId);
        return R.ok(health);
    }

    // ============================================
    // 成员工作量
    // ============================================
    @Operation(summary = "获取成员工作量统计")
    @GetMapping("/projects/{projectId}/workload")
    public R<MemberWorkloadResponse> getMemberWorkload(@PathVariable String projectId, Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new com.devflow.common.exception.BusinessException(404, "项目不存在或已被删除");
        }

        MemberWorkloadResponse workload = statisticsService.getMemberWorkload(projectId);
        return R.ok(workload);
    }

    // ============================================
    // Sprint 燃尽图
    // ============================================
    @Operation(summary = "获取 Sprint 燃尽图数据")
    @GetMapping("/projects/{projectId}/burndown")
    public R<BurndownResponse> getSprintBurndown(
        @PathVariable String projectId,
        @RequestParam(required = false) String sprintId,
        Authentication authentication
    ) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new com.devflow.common.exception.BusinessException(404, "项目不存在或已被删除");
        }

        BurndownResponse burndown = statisticsService.getSprintBurndown(projectId, sprintId);
        return R.ok(burndown);
    }

    // ============================================
    // 甘特图
    // ============================================
    @Operation(summary = "获取项目甘特图数据")
    @GetMapping("/projects/{projectId}/gantt")
    public R<GanttTaskResponse> getProjectGantt(@PathVariable String projectId, Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new com.devflow.common.exception.BusinessException(404, "项目不存在或已被删除");
        }

        GanttTaskResponse gantt = statisticsService.getProjectGantt(projectId);
        return R.ok(gantt);
    }

    // ============================================
    // 导出 Excel
    // ============================================
    @Operation(summary = "导出项目统计 Excel")
    @GetMapping("/projects/{projectId}/export/excel")
    public void exportProjectExcel(
        @PathVariable String projectId,
        HttpServletResponse response
    ) throws IOException {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 设置响应头
        String fileName = URLEncoder.encode(project.getName() + "_统计报表.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        // 获取统计数据
        ProjectHealthResponse health = statisticsService.getProjectHealth(projectId);
        MemberWorkloadResponse workload = statisticsService.getMemberWorkload(projectId);

        // 写入 Excel - 项目概览 sheet
        List<Map<String, Object>> overviewData = new ArrayList<>();
        Map<String, Object> overviewRow = new LinkedHashMap<>();
        overviewRow.put("项目名称", project.getName());
        overviewRow.put("健康分数", health.getOverallScore());
        overviewRow.put("健康等级", translateHealthLevel(health.getHealthLevel()));
        overviewRow.put("任务完成率", String.format("%.1f%%", health.getMetrics().getTaskCompletionRate()));
        overviewRow.put("Bug解决率", String.format("%.1f%%", health.getMetrics().getBugResolutionRate()));
        overviewRow.put("需求完成率", String.format("%.1f%%", health.getMetrics().getRequirementCompletionRate()));
        overviewRow.put("延期率", String.format("%.1f%%", health.getMetrics().getOverdueRate()));
        overviewRow.put("总任务数", health.getMetrics().getTotalTasks());
        overviewRow.put("已完成任务", health.getMetrics().getCompletedTasks());
        overviewRow.put("总Bug数", health.getMetrics().getTotalBugs());
        overviewRow.put("已解决Bug", health.getMetrics().getResolvedBugs());
        overviewData.add(overviewRow);

        EasyExcel.write(response.getOutputStream())
            .head(headMap(overviewRow))
            .sheet("项目概览")
            .doWrite(convertToListMap(overviewData));

        // 成员工作量需要通过多次写入追加 sheet（EasyExcel 4.x 支持多 sheet）
        // 这里简化处理：主要导出项目概览数据
    }

    @Operation(summary = "导出成员工作量 Excel")
    @GetMapping("/projects/{projectId}/export/workload")
    public void exportWorkloadExcel(
        @PathVariable String projectId,
        HttpServletResponse response
    ) throws IOException {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String fileName = URLEncoder.encode(project.getName() + "_成员工作量.xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        MemberWorkloadResponse workload = statisticsService.getMemberWorkload(projectId);

        // 构建表头
        List<List<String>> head = new ArrayList<>();
        head.add(Collections.singletonList("成员"));
        head.add(Collections.singletonList("总任务"));
        head.add(Collections.singletonList("已完成"));
        head.add(Collections.singletonList("进行中"));
        head.add(Collections.singletonList("待办"));
        head.add(Collections.singletonList("Bug数"));
        head.add(Collections.singletonList("紧急任务"));
        head.add(Collections.singletonList("延期任务"));
        head.add(Collections.singletonList("完成率"));
        head.add(Collections.singletonList("负载等级"));

        // 构建数据
        List<List<Object>> data = workload.getMembers().stream()
            .map(m -> {
                List<Object> row = new ArrayList<>();
                row.add(m.getUsername()); // username 字段已由服务层填入 displayName
                row.add(m.getTotalTasks());
                row.add(m.getCompletedTasks());
                row.add(m.getInProgressTasks());
                row.add(m.getTodoTasks());
                row.add(m.getBugCount());
                row.add(m.getCriticalTasks());
                row.add(m.getOverdueTasks());
                row.add(String.format("%.1f%%", m.getCompletionRate()));
                row.add(translateLoadLevel(m.getLoadLevel()));
                return row;
            })
            .collect(Collectors.toList());

        EasyExcel.write(response.getOutputStream())
            .head(head)
            .sheet("成员工作量")
            .doWrite(data);
    }

    // ============================================
    // 辅助方法
    // ============================================
    private String translateHealthLevel(String level) {
        switch (level) {
            case "EXCELLENT": return "优秀";
            case "GOOD": return "良好";
            case "FAIR": return "一般";
            case "POOR": return "较差";
            default: return level;
        }
    }

    private String translateLoadLevel(String level) {
        switch (level) {
            case "HIGH": return "高";
            case "MEDIUM": return "中";
            case "LOW": return "低";
            default: return level;
        }
    }

    private List<List<String>> headMap(Map<String, Object> row) {
        return row.keySet().stream()
            .map(Collections::singletonList)
            .collect(Collectors.toList());
    }

    private List<List<Object>> convertToListMap(List<Map<String, Object>> data) {
        return data.stream()
            .map(m -> new ArrayList<>(m.values()))
            .collect(Collectors.toList());
    }
}
