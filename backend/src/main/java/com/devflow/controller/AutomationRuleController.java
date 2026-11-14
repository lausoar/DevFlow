package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.entity.AutomationRule;
import com.devflow.security.AccessGuard;
import com.devflow.service.AutomationRuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自动化规则管理（MVP）。
 * 读：项目可见即读；写：项目级 testcase:manage（TESTER/OWNER/MANAGER）。
 * 路径中的 projectId 与规则实际归属不一致时按 404 处理，防止跨项目改写。
 */
@Tag(name = "自动化规则", description = "自动化规则 CRUD")
@RestController
@RequestMapping("/projects/{projectId}/automations")
@RequiredArgsConstructor
public class AutomationRuleController {

    private final AutomationRuleService automationRuleService;
    private final AccessGuard guard;

    @Operation(summary = "获取项目的自动化规则")
    @GetMapping
    public R<List<AutomationRule>> list(@PathVariable String projectId, Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(automationRuleService.listByProject(projectId));
    }

    @Operation(summary = "创建自动化规则")
    @PostMapping
    public R<AutomationRule> create(@PathVariable String projectId,
                                    @RequestBody AutomationRule rule,
                                    Authentication authentication) {
        return R.ok(automationRuleService.create(projectId, rule,
                guard.userId(authentication)));
    }

    @Operation(summary = "更新自动化规则")
    @PutMapping("/{ruleId}")
    public R<AutomationRule> update(@PathVariable String projectId,
                                    @PathVariable String ruleId,
                                    @RequestBody AutomationRule updates,
                                    Authentication authentication) {
        String userId = guard.userId(authentication);
        // 先校验路径项目可见，再让 service 校验规则真实归属（service 内按规则自身 projectId 再判一次权限）
        guard.requireView(userId, projectId);
        return R.ok(automationRuleService.update(ruleId, updates, userId));
    }

    @Operation(summary = "删除自动化规则")
    @DeleteMapping("/{ruleId}")
    public R<Void> delete(@PathVariable String projectId,
                          @PathVariable String ruleId,
                          Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireView(userId, projectId);
        automationRuleService.delete(ruleId, userId);
        return R.ok();
    }
}
