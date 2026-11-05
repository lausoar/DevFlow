package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.dto.request.CreateDepartmentRequest;
import com.devflow.dto.response.EnterpriseDetailResponse;
import com.devflow.entity.Department;
import com.devflow.service.EnterpriseService;
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

    /** 获取企业部门树 */
    @Operation(summary = "获取企业部门树结构")
    @GetMapping("/tree")
    public R<List<EnterpriseDetailResponse.DepartmentTree>> getTree(
            @RequestParam String enterpriseId) {
        return R.ok(enterpriseService.getDepartmentTree(enterpriseId));
    }

    /** 创建部门 */
    @Operation(summary = "创建新部门")
    @PostMapping
    public R<Department> create(@RequestParam String enterpriseId,
                                @Valid @RequestBody CreateDepartmentRequest request,
                                Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(enterpriseService.createDepartment(enterpriseId, request, userId));
    }

    /** 更新部门（重命名/调整上级） */
    @Operation(summary = "更新部门")
    @PutMapping("/{id}")
    public R<Department> update(@PathVariable String id,
                                @RequestBody com.devflow.dto.request.UpdateDepartmentRequest request,
                                Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
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
}
