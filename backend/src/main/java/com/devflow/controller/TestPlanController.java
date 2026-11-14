package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.result.R;
import com.devflow.entity.TestCycle;
import com.devflow.entity.TestPlan;
import com.devflow.security.AccessGuard;
import com.devflow.service.TestPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 测试计划与轮次。
 * 读 = 项目可见；写 = testcase:manage（TESTER/OWNER/MANAGER）。
 */
@Tag(name = "测试计划", description = "测试计划、测试轮次、轮次用例圈选")
@RestController
@RequiredArgsConstructor
public class TestPlanController {

    private final TestPlanService testPlanService;
    private final AccessGuard guard;

    // ==================== 计划 ====================

    @Operation(summary = "项目测试计划列表")
    @GetMapping("/projects/{projectId}/test-plans")
    public R<List<TestPlan>> list(@PathVariable String projectId, Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(testPlanService.listPlans(projectId));
    }

    @Operation(summary = "测试计划详情")
    @GetMapping("/test-plans/{id}")
    public R<TestPlan> get(@PathVariable String id, Authentication authentication) {
        TestPlan plan = testPlanService.getPlan(id);
        guard.requireView(guard.userId(authentication), plan.getProjectId());
        return R.ok(plan);
    }

    @Operation(summary = "创建测试计划")
    @PostMapping("/projects/{projectId}/test-plans")
    public R<TestPlan> create(@PathVariable String projectId,
                              @RequestBody TestPlan plan,
                              Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.TESTCASE_MANAGE);
        return R.ok(testPlanService.createPlan(projectId, plan, userId));
    }

    @Operation(summary = "更新测试计划")
    @PutMapping("/test-plans/{id}")
    public R<TestPlan> update(@PathVariable String id,
                              @RequestBody TestPlan patch,
                              Authentication authentication) {
        String userId = guard.userId(authentication);
        TestPlan plan = testPlanService.getPlan(id);
        guard.requireProjectPermission(userId, plan.getProjectId(), PermissionCodes.TESTCASE_MANAGE);
        return R.ok(testPlanService.updatePlan(id, patch, userId));
    }

    @Operation(summary = "删除测试计划（连带轮次）")
    @DeleteMapping("/test-plans/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        TestPlan plan = testPlanService.getPlan(id);
        guard.requireProjectPermission(userId, plan.getProjectId(), PermissionCodes.TESTCASE_MANAGE);
        testPlanService.deletePlan(id, userId);
        return R.ok();
    }

    // ==================== 轮次 ====================

    @Operation(summary = "计划下的测试轮次（含进度）")
    @GetMapping("/test-plans/{id}/cycles")
    public R<List<Map<String, Object>>> cycles(@PathVariable String id, Authentication authentication) {
        TestPlan plan = testPlanService.getPlan(id);
        guard.requireView(guard.userId(authentication), plan.getProjectId());
        return R.ok(testPlanService.listCycles(id));
    }

    @Operation(summary = "创建测试轮次")
    @PostMapping("/test-plans/{id}/cycles")
    public R<TestCycle> createCycle(@PathVariable String id,
                                    @RequestBody TestCycle cycle,
                                    Authentication authentication) {
        String userId = guard.userId(authentication);
        TestPlan plan = testPlanService.getPlan(id);
        guard.requireProjectPermission(userId, plan.getProjectId(), PermissionCodes.TESTCASE_MANAGE);
        return R.ok(testPlanService.createCycle(id, cycle, userId));
    }

    @Operation(summary = "更新测试轮次")
    @PutMapping("/test-cycles/{id}")
    public R<TestCycle> updateCycle(@PathVariable String id,
                                    @RequestBody TestCycle patch,
                                    Authentication authentication) {
        String userId = guard.userId(authentication);
        Map<String, Object> detail = testPlanService.cycleDetail(id);
        TestCycle cycle = (TestCycle) detail.get("cycle");
        guard.requireProjectPermission(userId, cycle.getProjectId(), PermissionCodes.TESTCASE_MANAGE);
        return R.ok(testPlanService.updateCycle(id, patch, userId));
    }

    @Operation(summary = "删除测试轮次")
    @DeleteMapping("/test-cycles/{id}")
    public R<Void> deleteCycle(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        Map<String, Object> detail = testPlanService.cycleDetail(id);
        TestCycle cycle = (TestCycle) detail.get("cycle");
        guard.requireProjectPermission(userId, cycle.getProjectId(), PermissionCodes.TESTCASE_MANAGE);
        testPlanService.deleteCycle(id, userId);
        return R.ok();
    }

    @Operation(summary = "轮次详情（用例清单 + 最新执行结果 + 进度）")
    @GetMapping("/test-cycles/{id}")
    public R<Map<String, Object>> cycleDetail(@PathVariable String id, Authentication authentication) {
        Map<String, Object> detail = testPlanService.cycleDetail(id);
        TestCycle cycle = (TestCycle) detail.get("cycle");
        guard.requireView(guard.userId(authentication), cycle.getProjectId());
        return R.ok(detail);
    }

    /** 往轮次里加用例（批量） */
    @Data
    public static class AddCasesRequest {
        private List<String> caseIds;
    }

    @Operation(summary = "轮次批量加入用例")
    @PostMapping("/test-cycles/{id}/cases")
    public R<Map<String, Integer>> addCases(@PathVariable String id,
                                            @RequestBody AddCasesRequest request,
                                            Authentication authentication) {
        String userId = guard.userId(authentication);
        Map<String, Object> detail = testPlanService.cycleDetail(id);
        TestCycle cycle = (TestCycle) detail.get("cycle");
        guard.requireProjectPermission(userId, cycle.getProjectId(), PermissionCodes.TESTCASE_MANAGE);
        int added = testPlanService.addCases(id, request.getCaseIds(), userId);
        return R.ok(Map.of("added", added));
    }

    @Operation(summary = "轮次移除用例")
    @DeleteMapping("/test-cycles/{id}/cases/{caseId}")
    public R<Void> removeCase(@PathVariable String id,
                              @PathVariable String caseId,
                              Authentication authentication) {
        String userId = guard.userId(authentication);
        TestCycle cycle = (TestCycle) testPlanService.cycleDetail(id).get("cycle");
        guard.requireProjectPermission(userId, cycle.getProjectId(), PermissionCodes.TESTCASE_MANAGE);
        testPlanService.removeCase(id, caseId, userId);
        return R.ok();
    }
}
