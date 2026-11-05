package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.entity.AutomationRule;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.ProjectAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自动化规则管理（MVP）。
 * 管理权限：企业超级管理员。
 */
@Tag(name = "自动化规则", description = "自动化规则 CRUD")
@RestController
@RequestMapping("/projects/{projectId}/automations")
@RequiredArgsConstructor
public class AutomationRuleController {

    private final AutomationRuleService automationRuleService;

    @Operation(summary = "获取项目的自动化规则")
    @GetMapping
    public R<List<AutomationRule>> list(@PathVariable String projectId, Authentication authentication) {
        return R.ok(automationRuleService.listByProject(projectId));
    }

    @Operation(summary = "创建自动化规则")
    @PostMapping
    public R<AutomationRule> create(@PathVariable String projectId,
                                    @RequestBody AutomationRule rule,
                                    Authentication authentication) {
        return R.ok(automationRuleService.create(projectId, rule,
                (String) authentication.getPrincipal()));
    }

    @Operation(summary = "更新自动化规则")
    @PutMapping("/{ruleId}")
    public R<AutomationRule> update(@PathVariable String projectId,
                                    @PathVariable String ruleId,
                                    @RequestBody AutomationRule updates,
                                    Authentication authentication) {
        return R.ok(automationRuleService.update(ruleId, updates,
                (String) authentication.getPrincipal()));
    }

    @Operation(summary = "删除自动化规则")
    @DeleteMapping("/{ruleId}")
    public R<Void> delete(@PathVariable String projectId,
                          @PathVariable String ruleId,
                          Authentication authentication) {
        automationRuleService.delete(ruleId, (String) authentication.getPrincipal());
        return R.ok();
    }
}
