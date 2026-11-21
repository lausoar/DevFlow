package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.dto.request.CreateDepartmentRequest;
import com.devflow.dto.response.EnterpriseDetailResponse;
import com.devflow.entity.Department;
import com.devflow.service.EnterpriseService;
import com.devflow.common.PermissionCodes;
import com.devflow.security.AccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理控制器
 */
@Tag(name = "部门管理", description = "部门树、创建、删除")
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final EnterpriseService enterpriseService;
    private final AccessGuard guard;
    private final com.devflow.service.ContactVisibility contactVisibility;

    /** 获取企业部门树 */
    @Operation(summary = "获取企业部门树结构")
    @GetMapping("/tree")
    public R<List<EnterpriseDetailResponse.DepartmentTree>> getTree(
            @RequestParam String enterpriseId,
            Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterpriseMember(userId, enterpriseId);
        // 部门树里的成员是完整 User 实体：没有成员管理权限的人必须脱敏
        List<EnterpriseDetailResponse.DepartmentTree> tree = enterpriseService.getDepartmentTree(enterpriseId);
        if (!contactVisibility.canSeeContactInfo(userId)) {
            maskTree(tree);
        }
        return R.ok(tree);
    }

    /** 创建部门 */
    @Operation(summary = "创建新部门")
    @PostMapping
    public R<Department> create(@RequestParam String enterpriseId,
                                @Valid @RequestBody CreateDepartmentRequest request,
                                Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, enterpriseId, PermissionCodes.MEMBER_DEPARTMENT);
        return R.ok(enterpriseService.createDepartment(enterpriseId, request, userId));
    }

    /** 更新部门（重命名/调整上级） */
    @Operation(summary = "更新部门")
    @PutMapping("/{id}")
    public R<Department> update(@PathVariable String id,
                                @RequestBody com.devflow.dto.request.UpdateDepartmentRequest request,
                                Authentication authentication) {
        String userId = guard.userId(authentication);
        return R.ok(enterpriseService.updateDepartment(id, request, userId));
    }

    /** 删除部门 */
    @Operation(summary = "删除部门")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        enterpriseService.deleteDepartment(id, userId);
        return R.ok();
    }

    /** 递归脱敏部门树中的成员邮箱与手机号（与企业详情页同一规则） */
    private void maskTree(List<EnterpriseDetailResponse.DepartmentTree> departments) {
        if (departments == null) {
            return;
        }
        for (EnterpriseDetailResponse.DepartmentTree dept : departments) {
            if (dept.getMembers() != null) {
                dept.getMembers().forEach(m -> {
                    if (m.getEmail() != null) {
                        m.setEmail(com.devflow.util.Masking.email(m.getEmail()));
                    }
                    if (m.getPhone() != null) {
                        m.setPhone(com.devflow.util.Masking.phone(m.getPhone()));
                    }
                });
            }
            maskTree(dept.getChildren());
        }
    }
}
