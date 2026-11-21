package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.PermissionCodes;
import com.devflow.common.result.R;
import com.devflow.entity.Bug;
import com.devflow.entity.TestCase;
import com.devflow.entity.TestExecution;
import com.devflow.mapper.BugMapper;
import com.devflow.service.BugService;
import com.devflow.mapper.TestCaseMapper;
import com.devflow.mapper.TestExecutionMapper;
import java.util.List;
import java.util.Map;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.AuditService;
import com.devflow.service.PermissionService;
import com.devflow.service.NotificationService;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.WorkflowService;
import com.devflow.util.CsvUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 测试用例管理：用例库 CRUD、执行记录、失败转缺陷、项目统计。
 * 读 = 项目成员；写 = testcase:manage 权限点（TESTER/OWNER/MANAGER）。
 */
@Tag(name = "测试用例", description = "测试用例库、执行记录、转缺陷")
@RestController
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseMapper testCaseMapper;
    private final TestExecutionMapper testExecutionMapper;
    private final com.devflow.mapper.TestCycleCaseMapper cycleCaseMapper;
    private final com.devflow.mapper.TestCaseVersionMapper testCaseVersionMapper;
    private final WorkflowService workflowService;
    private final BugService bugService;
    private final BugMapper bugMapper;
    private final com.devflow.mapper.ProjectMapper projectMapper;
    private final ProjectAccessService projectAccessService;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final com.devflow.service.ImPushService imPushService;
    private final AutomationRuleService automationRuleService;
    private final AuditService auditService;

    // ==================== 用例 CRUD ====================

    /** 用例列表 + 项目测试统计（最新执行结果随用例返回） */
    @Operation(summary = "获取测试用例列表（含统计）")
    @GetMapping("/projects/{projectId}/test-cases")
    public R<Map<String, Object>> list(@PathVariable String projectId,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String cycleId,
                                       Authentication authentication) {
        requireProjectVisible(projectId, authentication);

        // 指定轮次时只返回该轮次圈定的用例，否则返回项目全部用例
        java.util.Set<String> cycleCaseIds = null;
        boolean byCycle = cycleId != null && !cycleId.isBlank();
        if (byCycle) {
            cycleCaseIds = cycleCaseMapper.selectList(
                            new LambdaQueryWrapper<com.devflow.entity.TestCycleCase>()
                                    .eq(com.devflow.entity.TestCycleCase::getCycleId, cycleId))
                    .stream().map(com.devflow.entity.TestCycleCase::getCaseId)
                    .collect(Collectors.toSet());
        }

        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<TestCase>()
                .eq(TestCase::getProjectId, projectId)
                .orderByDesc(TestCase::getUpdatedAt);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(TestCase::getTitle, keyword.trim());
        }
        if (byCycle) {
            if (cycleCaseIds.isEmpty()) {
                return R.ok(Map.of("cases", List.of(), "stats", emptyCaseStats()));
            }
            wrapper.in(TestCase::getId, cycleCaseIds);
        }
        List<TestCase> cases = testCaseMapper.selectList(wrapper);

        // 执行记录：指定轮次时只看该轮次，否则看项目全部（取每个用例最新一次）
        List<TestExecution> allExecs;
        if (byCycle) {
            allExecs = testExecutionMapper.selectList(new LambdaQueryWrapper<TestExecution>()
                    .eq(TestExecution::getProjectId, projectId)
                    .eq(TestExecution::getCycleId, cycleId)
                    .orderByDesc(TestExecution::getCreatedAt));
        } else {
            allExecs = testExecutionMapper.selectByProject(projectId);
        }
        Map<String, TestExecution> latest = new LinkedHashMap<>();
        for (TestExecution e : allExecs) { // 已按时间倒序
            latest.putIfAbsent(e.getCaseId(), e);
        }

        // 组装用例视图
        List<Map<String, Object>> caseViews = new ArrayList<>();
        Map<String, Integer> resultCount = new HashMap<>();
        for (TestCase c : cases) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("title", c.getTitle());
            m.put("precondition", c.getPrecondition());
            m.put("steps", c.getSteps());
            m.put("expectedResult", c.getExpectedResult());
            m.put("priority", c.getPriority());
            m.put("requirementId", c.getRequirementId());
            m.put("version", c.getVersion());
            m.put("reviewStatus", c.getReviewStatus());
            m.put("createdAt", c.getCreatedAt());
            m.put("updatedAt", c.getUpdatedAt());

            TestExecution latestExec = latest.get(c.getId());
            String latestResult = latestExec != null ? latestExec.getResult() : "UNEXECUTED";
            m.put("latestResult", latestResult);
            m.put("executionCount", allExecs.stream().filter(e -> e.getCaseId().equals(c.getId())).count());
            m.put("latestExecutedAt", latestExec != null ? latestExec.getCreatedAt() : null);
            m.put("latestExecutorName", latestExec != null ? latestExec.getExecutorName() : null);
            caseViews.add(m);

            resultCount.merge(latestResult, 1, Integer::sum);
        }

        int total = cases.size();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("passed", resultCount.getOrDefault("PASS", 0));
        stats.put("failed", resultCount.getOrDefault("FAIL", 0));
        stats.put("blocked", resultCount.getOrDefault("BLOCKED", 0));
        stats.put("skipped", resultCount.getOrDefault("SKIP", 0));
        stats.put("unexecuted", resultCount.getOrDefault("UNEXECUTED", 0));
        stats.put("passRate", total == 0 ? 0
                : Math.round(resultCount.getOrDefault("PASS", 0) * 1000.0 / total) / 10.0);

        Map<String, Object> result = new HashMap<>();
        result.put("cases", caseViews);
        result.put("stats", stats);
        return R.ok(result);
    }

    @Operation(summary = "创建测试用例")
    @PostMapping("/projects/{projectId}/test-cases")
    public R<TestCase> create(@PathVariable String projectId,
                              @RequestBody TestCase testCase,
                              Authentication authentication) {
        requireManagePermission(projectId, authentication);
        String userId = (String) authentication.getPrincipal();
        testCase.setId(null);
        testCase.setProjectId(projectId);
        testCase.setCreatorId(userId);
        testCase.setDeleted(0);
        testCase.setVersion(1);
        if (testCase.getReviewStatus() == null) {
            testCase.setReviewStatus("DRAFT");
        }
        normalizeSteps(testCase);
        testCaseMapper.insert(testCase);
        return R.ok(testCase);
    }

    @Operation(summary = "获取用例详情（含执行历史）")
    @GetMapping("/test-cases/{id}")
    public R<Map<String, Object>> detail(@PathVariable String id, Authentication authentication) {
        TestCase testCase = requireCaseVisible(id, authentication);
        Map<String, Object> result = new HashMap<>();
        result.put("case", testCase);
        result.put("executions", testExecutionMapper.selectByCase(id));
        return R.ok(result);
    }

    @Operation(summary = "更新测试用例")
    @PutMapping("/test-cases/{id}")
    @org.springframework.transaction.annotation.Transactional
    public R<TestCase> update(@PathVariable String id,
                              @RequestBody TestCase testCase,
                              Authentication authentication) {
        TestCase existing = requireCaseManageable(id, authentication);
        testCase.setId(id);
        testCase.setProjectId(existing.getProjectId());
        testCase.setCreatorId(existing.getCreatorId());

        // 编辑即快照上一版：执行记录带 case_version，失败盘点才能追溯"当时按哪版步骤执行"
        int prevVersion = existing.getVersion() == null ? 1 : existing.getVersion();
        com.devflow.entity.TestCaseVersion snapshot = new com.devflow.entity.TestCaseVersion();
        snapshot.setCaseId(id);
        snapshot.setVersion(prevVersion);
        snapshot.setTitle(existing.getTitle());
        snapshot.setPrecondition(existing.getPrecondition());
        snapshot.setSteps(existing.getSteps());
        snapshot.setExpectedResult(existing.getExpectedResult());
        snapshot.setEditorId((String) authentication.getPrincipal());
        testCaseVersionMapper.insert(snapshot);

        testCase.setVersion(prevVersion + 1);
        if (testCase.getReviewStatus() == null) {
            testCase.setReviewStatus(existing.getReviewStatus());
        }
        normalizeSteps(testCase);
        testCaseMapper.updateById(testCase);
        return R.ok(testCaseMapper.selectById(id));
    }

    /** 用例版本历史（快照列表） */
    @Operation(summary = "获取用例版本历史")
    @GetMapping("/test-cases/{id}/versions")
    public R<List<com.devflow.entity.TestCaseVersion>> versions(@PathVariable String id,
                                                               Authentication authentication) {
        requireCaseVisible(id, authentication);
        return R.ok(testCaseVersionMapper.selectList(
                new LambdaQueryWrapper<com.devflow.entity.TestCaseVersion>()
                        .eq(com.devflow.entity.TestCaseVersion::getCaseId, id)
                        .orderByDesc(com.devflow.entity.TestCaseVersion::getVersion)));
    }

    /** 用例评审：DRAFT → PENDING → APPROVED / REJECTED */
    @Operation(summary = "提交用例评审 / 评审结论")
    @PutMapping("/test-cases/{id}/review")
    public R<TestCase> review(@PathVariable String id,
                              @RequestBody Map<String, String> body,
                              Authentication authentication) {
        requireCaseManageable(id, authentication);
        String target = body.get("reviewStatus");
        // List.of(...).contains(null) 会抛 NPE：缺字段是客户端错误，必须先判空
        if (target == null || !List.of("DRAFT", "PENDING", "APPROVED", "REJECTED").contains(target)) {
            throw new BusinessException(400, "无效的评审状态");
        }
        TestCase patch = new TestCase();
        patch.setId(id);
        patch.setReviewStatus(target);
        testCaseMapper.updateById(patch);
        return R.ok(testCaseMapper.selectById(id));
    }

    @Operation(summary = "删除测试用例（逻辑删除）")
    @DeleteMapping("/test-cases/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        requireCaseManageable(id, authentication);
        testCaseMapper.deleteById(id);
        return R.ok();
    }

    /** 导出用例：复用列表的同一份结果，避免"列表显示与导出内容不一致" */
    @Operation(summary = "导出项目测试用例 CSV")
    @GetMapping("/projects/{projectId}/export/test-cases.csv")
    public void exportCases(@PathVariable String projectId,
                            Authentication authentication,
                            HttpServletResponse response) throws IOException {
        Map<String, Object> listed = list(projectId, null, null, authentication).getData();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cases = (List<Map<String, Object>>) listed.get("cases");
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> item : cases) {
            rows.add(List.of(
                    text(item.get("title")),
                    priorityLabel(text(item.get("priority"))),
                    text(item.get("precondition")),
                    stepsSummary(text(item.get("steps"))),
                    text(item.get("expectedResult")),
                    resultLabel(text(item.get("latestResult"))),
                    text(item.get("executionCount")),
                    text(item.get("latestExecutorName"))
            ));
        }
        auditService.logRequired((String) authentication.getPrincipal(), null, projectId,
                "export.test_cases", "project", projectId, "导出测试用例 CSV：" + rows.size() + " 行");
        writeCsv(response, "测试用例.csv",
                List.of("标题", "优先级", "前置条件", "步骤", "预期结果", "最新结果", "执行次数", "最近执行人"),
                rows);
    }

    private String stepsSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return "无";
        }
        try {
            List<Map<String, Object>> steps = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(raw, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            if (steps.isEmpty()) {
                return "无";
            }
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> step = steps.get(i);
                String expected = text(step.get("expected"));
                parts.add((i + 1) + ". " + text(step.get("action"))
                        + (expected.isBlank() ? "" : "（预期：" + expected + "）"));
            }
            return String.join("\n", parts);
        } catch (Exception e) {
            return raw;
        }
    }

    private String priorityLabel(String value) {
        return switch (value) {
            case "CRITICAL" -> "紧急";
            case "HIGH" -> "高";
            case "MEDIUM" -> "中";
            case "LOW" -> "低";
            default -> value;
        };
    }

    private String resultLabel(String value) {
        return switch (value) {
            case "PASS" -> "通过";
            case "FAIL" -> "失败";
            case "BLOCKED" -> "阻塞";
            case "SKIP" -> "跳过";
            case "UNEXECUTED" -> "未执行";
            default -> value;
        };
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void writeCsv(HttpServletResponse response, String fileName,
                          List<String> headers, List<List<String>> rows) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(headers.stream().map(CsvUtils::escape).collect(Collectors.joining(","))).append("\r\n");
        for (List<String> row : rows) {
            csv.append(row.stream().map(CsvUtils::escape).collect(Collectors.joining(","))).append("\r\n");
        }
        response.getWriter().write(csv.toString());
    }

    // ==================== 执行 ====================

    @Operation(summary = "记录执行结果")
    @PostMapping("/test-cases/{id}/executions")
    public R<TestExecution> execute(@PathVariable String id,
                                    @RequestBody Map<String, String> body,
                                    Authentication authentication) {
        TestCase testCase = requireCaseManageable(id, authentication);
        String result = body.getOrDefault("result", "").toUpperCase();
        if (!List.of("PASS", "FAIL", "BLOCKED", "SKIP").contains(result)) {
            throw new BusinessException(400, "无效的执行结果");
        }
        TestExecution exec = new TestExecution();
        exec.setCaseId(id);
        exec.setProjectId(testCase.getProjectId());
        // 记录本次执行对应的用例版本与轮次，便于按轮次统计与失败追溯
        exec.setCaseVersion(testCase.getVersion());
        String cycleId = body.get("cycleId");
        exec.setCycleId(cycleId != null && !cycleId.isBlank() ? cycleId : null);
        exec.setResult(result);
        exec.setNote(body.get("note") != null ? body.get("note").trim() : null);
        exec.setExecutorId((String) authentication.getPrincipal());
        testExecutionMapper.insert(exec);

        // 失败时通知用例创建人（非自己）
        if ("FAIL".equals(result) && !testCase.getCreatorId().equals(exec.getExecutorId())) {
            try {
                notificationService.createNotification(
                        testCase.getCreatorId(), "SYSTEM",
                        "测试用例执行失败",
                        "用例「" + testCase.getTitle() + "」被标记为失败，可转缺陷跟进",
                        "/projects/" + testCase.getProjectId() + "?tab=testing");
            } catch (Exception ignored) {
            }
        }

        // 自动化规则：只有真实失败才触发 CASE_FAIL
        if ("FAIL".equals(result)) {
            try {
                automationRuleService.fire("CASE_FAIL", testCase.getProjectId(),
                        id, testCase.getTitle(), (String) authentication.getPrincipal());
            } catch (Exception ignored) {
            }
        }

        // 企业微信群机器人：用例执行失败推送（仅失败才推，避免刷屏）
        if ("FAIL".equals(result)) {
            try {
                com.devflow.entity.Project project = projectMapper.selectById(testCase.getProjectId());
                if (project != null && project.getEnterpriseId() != null) {
                    String md = "**🧪 用例执行失败**\n"
                            + "> **" + testCase.getTitle() + "**\n"
                            + "> 项目：" + project.getName() + "\n"
                            + (exec.getNote() != null ? "> 备注：" + exec.getNote() + "\n" : "")
                            + "> 可转缺陷跟进";
                    imPushService.pushMarkdown(project.getEnterpriseId(), md);
                }
            } catch (Exception ignored) {
            }
        }
        return R.ok(exec);
    }

    @Operation(summary = "获取用例执行历史")
    @GetMapping("/test-cases/{id}/executions")
    public R<List<TestExecution>> executions(@PathVariable String id, Authentication authentication) {
        requireCaseVisible(id, authentication);
        return R.ok(testExecutionMapper.selectByCase(id));
    }

    /** 失败转缺陷：用例的步骤/预期自动拼装为 Bug 描述，回填关联 */
    @Operation(summary = "用例转缺陷")
    @PostMapping("/test-cases/{id}/to-bug")
    @org.springframework.transaction.annotation.Transactional
    public R<Bug> toBug(@PathVariable String id,
                        @RequestBody Map<String, String> body,
                        Authentication authentication) {
        TestCase testCase = requireCaseManageable(id, authentication);
        String userId = (String) authentication.getPrincipal();

        // 权限点：转缺陷还需 bug:create（TESTER/OWNER/MANAGER 均已映射）
        if (!permissionService.hasProjectPermission(userId, testCase.getProjectId(), PermissionCodes.BUG_CREATE)) {
            throw new BusinessException(403, "无权创建缺陷");
        }

        TestExecution failedExecution = testExecutionMapper.selectByCase(id).stream()
                .filter(e -> "FAIL".equals(e.getResult()))
                .findFirst().orElse(null);
        if (failedExecution == null) {
            throw new BusinessException(400, "没有可转缺陷的失败执行记录");
        }
        if (failedExecution.getBugId() != null && !failedExecution.getBugId().isBlank()) {
            return R.ok(requireLinkedBug(bugService.getById(failedExecution.getBugId())));
        }

        // 先生成 Bug 主键，再用它原子占用最新一次失败执行。并发失败者只读已有关联，
        // 不会重复创建 Bug、通知或触发自动化；后续创建异常会由事务回滚这次占用。
        String bugId = UUID.randomUUID().toString().replace("-", "");
        if (testExecutionMapper.claimBug(failedExecution.getId(), bugId) == 0) {
            TestExecution claimed = testExecutionMapper.selectByIdForUpdate(failedExecution.getId());
            if (claimed != null && claimed.getBugId() != null && !claimed.getBugId().isBlank()) {
                return R.ok(requireLinkedBug(bugMapper.selectByIdForUpdate(claimed.getBugId())));
            }
            throw new BusinessException(409, "失败执行记录已被更新，请刷新后重试");
        }

        String note = body.getOrDefault("note", "").trim();
        StringBuilder desc = new StringBuilder();
        if (note != null && !note.isBlank()) {
            desc.append("失败说明：").append(note).append("\n\n");
        }
        desc.append("【前置条件】").append(
                testCase.getPrecondition() == null || testCase.getPrecondition().isBlank()
                        ? "无" : testCase.getPrecondition()).append("\n");

        if (testCase.getSteps() != null && !testCase.getSteps().isBlank()) {
            try {
                List<Map<String, Object>> steps = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(testCase.getSteps(),
                                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                desc.append("\n【复现步骤】\n");
                int i = 1;
                for (Map<String, Object> step : steps) {
                    desc.append(i++).append(". ").append(step.getOrDefault("action", ""))
                            .append("（预期：").append(step.getOrDefault("expected", "")).append("）\n");
                }
            } catch (Exception ignored) {
            }
        }
        desc.append("\n【预期结果】").append(
                testCase.getExpectedResult() == null || testCase.getExpectedResult().isBlank()
                        ? "按步骤执行符合预期" : testCase.getExpectedResult());
        desc.append("\n\n（由测试用例「").append(testCase.getTitle())
                .append("」v").append(testCase.getVersion() == null ? 1 : testCase.getVersion())
                .append(" 一键转缺陷生成）");

        Bug bug = new Bug();
        bug.setId(bugId);
        bug.setProjectId(testCase.getProjectId());
        bug.setTitle("【缺陷】" + testCase.getTitle());
        bug.setDescription(desc.toString());
        bug.setStatus(workflowService.initialStatus(testCase.getProjectId(), WorkflowService.TYPE_BUG));
        bug.setPriority("MEDIUM");
        bug.setSeverity("MAJOR");
        bug.setReporterId(userId);
        Bug created = bugService.create(testCase.getProjectId(), bug, userId);

        // 通知用例创建人（仅实际创建成功的占用者）
        if (!testCase.getCreatorId().equals(userId)) {
            try {
                notificationService.createNotification(
                        testCase.getCreatorId(), "SYSTEM",
                        "测试用例已转缺陷",
                        "用例「" + testCase.getTitle() + "」已转为缺陷跟进",
                        "/projects/" + testCase.getProjectId() + "?tab=bugs");
            } catch (Exception ignored) {
            }
        }
        return R.ok(created);
    }

    private Bug requireLinkedBug(Bug linked) {
        if (linked == null) {
            throw new BusinessException(404, "关联缺陷不存在或已被删除");
        }
        return linked;
    }

    // ==================== 内部 ====================

    private void normalizeSteps(TestCase testCase) {
        // steps 允许为空；非空时做基本 JSON 合法性校验（由 Jackson 解析验证）
        String steps = testCase.getSteps();
        if (steps != null && !steps.isBlank()) {
            try {
                new com.fasterxml.jackson.databind.ObjectMapper().readValue(steps,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                throw new BusinessException(400, "步骤格式不正确");
            }
        } else {
            testCase.setSteps(null);
        }
        if (testCase.getPriority() == null || testCase.getPriority().isBlank()) {
            testCase.setPriority("MEDIUM");
        }
    }

    /** 轮次内没有任何用例时的空统计 */
    private Map<String, Object> emptyCaseStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 0);
        stats.put("passed", 0);
        stats.put("failed", 0);
        stats.put("blocked", 0);
        stats.put("skipped", 0);
        stats.put("unexecuted", 0);
        stats.put("passRate", 0);
        return stats;
    }

    private void requireProjectVisible(String projectId, Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
    }

    private void requireManagePermission(String projectId, Authentication authentication) {
        requireProjectVisible(projectId, authentication);
        if (!permissionService.hasProjectPermission((String) authentication.getPrincipal(),
                projectId, PermissionCodes.TESTCASE_MANAGE)) {
            throw new BusinessException(403, "仅测试人员或项目管理员可管理测试用例");
        }
    }

    private TestCase requireCaseVisible(String caseId, Authentication authentication) {
        TestCase testCase = testCaseMapper.selectById(caseId);
        if (testCase == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), testCase.getProjectId())) {
            throw new BusinessException(404, "用例不存在或已被删除");
        }
        return testCase;
    }

    private TestCase requireCaseManageable(String caseId, Authentication authentication) {
        TestCase testCase = requireCaseVisible(caseId, authentication);
        if (!permissionService.hasProjectPermission((String) authentication.getPrincipal(),
                testCase.getProjectId(), PermissionCodes.TESTCASE_MANAGE)) {
            throw new BusinessException(403, "仅测试人员或项目管理员可管理测试用例");
        }
        return testCase;
    }
}
