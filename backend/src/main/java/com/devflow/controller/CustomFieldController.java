package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.result.R;
import com.devflow.entity.CustomField;
import com.devflow.security.AccessGuard;
import com.devflow.service.CustomFieldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自定义字段。
 * 读：项目可见；写：project:edit（字段定义属于项目配置，不是日常编辑动作）。
 */
@Tag(name = "自定义字段", description = "项目级字段定义与工作项字段值")
@RestController
@RequiredArgsConstructor
public class CustomFieldController {

    private final CustomFieldService customFieldService;
    private final AccessGuard guard;

    @Operation(summary = "项目某对象类型的字段定义")
    @GetMapping("/projects/{projectId}/custom-fields")
    public R<List<CustomField>> list(@PathVariable String projectId,
                                     @RequestParam(defaultValue = "TASK") String issueType,
                                     Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(customFieldService.list(projectId, issueType));
    }

    @Operation(summary = "新建自定义字段")
    @PostMapping("/projects/{projectId}/custom-fields")
    public R<CustomField> create(@PathVariable String projectId,
                                 @RequestParam(defaultValue = "TASK") String issueType,
                                 @RequestBody CustomField field,
                                 Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.PROJECT_EDIT);
        return R.ok(customFieldService.create(projectId, issueType, field));
    }

    @Operation(summary = "更新自定义字段（类型不可改）")
    @PutMapping("/custom-fields/{id}")
    public R<CustomField> update(@PathVariable String id,
                                 @RequestBody CustomField patch,
                                 Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, customFieldService.projectOf(id), PermissionCodes.PROJECT_EDIT);
        return R.ok(customFieldService.update(id, patch));
    }

    @Operation(summary = "删除自定义字段（连带其值）")
    @DeleteMapping("/custom-fields/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, customFieldService.projectOf(id),
                PermissionCodes.PROJECT_EDIT);
        customFieldService.delete(id);
        return R.ok();
    }

    // ==================== 工作项字段值 ====================

    @Operation(summary = "读取工作项的自定义字段值")
    @GetMapping("/{issueType}/{issueId}/custom-field-values")
    public R<Map<String, Object>> values(@PathVariable String issueType,
                                         @PathVariable String issueId,
                                         Authentication authentication) {
        String projectId = issueProject(issueType, issueId);
        guard.requireView(guard.userId(authentication), projectId);
        return R.ok(customFieldService.valuesOf(issueId, issueType.toUpperCase()));
    }

    @Data
    public static class SaveValuesRequest {
        private Map<String, Object> values;
    }

    @Operation(summary = "保存工作项的自定义字段值（全量覆盖）")
    @PutMapping("/{issueType}/{issueId}/custom-field-values")
    public R<Void> save(@PathVariable String issueType,
                        @PathVariable String issueId,
                        @RequestBody SaveValuesRequest request,
                        Authentication authentication) {
        String userId = guard.userId(authentication);
        String type = issueType.toUpperCase();
        String projectId = issueProject(type, issueId);
        // 编辑字段值跟随"该工作项能否被编辑"的权限点
        String code = switch (type) {
            case "BUG" -> PermissionCodes.BUG_EDIT;
            case "REQUIREMENT" -> PermissionCodes.REQUIREMENT_MANAGE;
            default -> PermissionCodes.TASK_MANAGE;
        };
        guard.requireProjectPermission(userId, projectId, code);
        customFieldService.saveValues(issueId, type, projectId, request.getValues());
        return R.ok();
    }

    /** 工作项所属项目（工作项不存在时 404，不泄露存在性） */
    private String issueProject(String issueType, String issueId) {
        return customFieldService.projectOfIssue(issueType.toUpperCase(), issueId);
    }
}
