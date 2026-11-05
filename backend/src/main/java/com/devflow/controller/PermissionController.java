package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.EnterpriseRoles;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.SysPermission;
import com.devflow.entity.SysRolePermission;
import com.devflow.entity.User;
import com.devflow.mapper.SysPermissionMapper;
import com.devflow.mapper.SysRolePermissionMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AuditService;
import com.devflow.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限管理控制器
 * /me 供前端按钮可见性判定；matrix 为全局角色-权限映射（超管可调，影响所有企业）
 */
@Tag(name = "权限管理", description = "权限点查询与角色-权限映射管理")
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final UserMapper userMapper;
    private final AuditService auditService;

    /** 项目角色清单（矩阵列固定，与 project_member.role 对应） */
    private static final List<String> PROJECT_ROLES = List.of("OWNER", "MANAGER", "TESTER", "MEMBER");

    /** 调整矩阵请求体 */
    @Data
    public static class UpdateMatrixRequest {
        private String scope;
        private String role;
        private String permissionCode;
        private Boolean enabled;
    }

    /** 当前用户的权限点集合（前端按钮可见性判定用） */
    @Operation(summary = "当前用户权限点集合")
    @GetMapping("/me")
    public R<Map<String, Set<String>>> me(@RequestParam(required = false) String projectId,
                                          Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        Set<String> enterprise = permissionService.enterprisePermissions(userId);
        Set<String> project = projectId != null
                ? permissionService.projectPermissions(userId, projectId)
                : Set.of();
        return R.ok(Map.of("enterprise", enterprise, "project", project));
    }

    /** 角色-权限矩阵（权限点清单 + 各作用域角色 + 当前映射行） */
    @Operation(summary = "获取角色-权限矩阵")
    @GetMapping("/matrix")
    public R<Map<String, Object>> matrix() {
        List<SysPermission> permissions = sysPermissionMapper.selectList(
                new LambdaQueryWrapper<SysPermission>()
                        .orderByAsc(SysPermission::getScope)
                        .orderByAsc(SysPermission::getSort));
        List<SysRolePermission> mapping = sysRolePermissionMapper.selectList(null);
        Map<String, Object> result = Map.of(
                "permissions", permissions,
                "enterpriseRoles", EnterpriseRoles.allRoles(),
                "projectRoles", PROJECT_ROLES,
                "mapping", mapping);
        return R.ok(result);
    }

    /** 调整角色-权限映射（仅超级管理员；全局生效，改完即时 reload） */
    @Operation(summary = "调整角色-权限映射（仅超级管理员）")
    @PutMapping("/matrix")
    public R<Void> updateMatrix(@RequestBody UpdateMatrixRequest req,
                                Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        if (!permissionService.hasEnterprisePermission(userId, PermissionCodes.ENTERPRISE_MANAGE)) {
            throw new BusinessException(403, "仅超级管理员可修改权限配置");
        }

        String scope = req.getScope();
        String role = req.getRole();
        String code = req.getPermissionCode();
        boolean enabled = Boolean.TRUE.equals(req.getEnabled());

        if (!PermissionCodes.SCOPE_ENTERPRISE.equals(scope) && !PermissionCodes.SCOPE_PROJECT.equals(scope)) {
            throw new BusinessException(400, "无效的作用域");
        }
        if (PermissionCodes.SCOPE_ENTERPRISE.equals(scope) && !EnterpriseRoles.isValid(role)) {
            throw new BusinessException(400, "无效的企业角色");
        }
        if (PermissionCodes.SCOPE_PROJECT.equals(scope) && !PROJECT_ROLES.contains(role)) {
            throw new BusinessException(400, "无效的项目角色");
        }
        SysPermission permission = sysPermissionMapper.selectById(code);
        if (permission == null) {
            throw new BusinessException(400, "无效的权限点");
        }
        if (!scope.equals(permission.getScope())) {
            throw new BusinessException(400, "权限点与作用域不匹配");
        }

        LambdaQueryWrapper<SysRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRolePermission::getScope, scope)
               .eq(SysRolePermission::getRole, role)
               .eq(SysRolePermission::getPermissionCode, code);
        if (enabled) {
            if (sysRolePermissionMapper.selectCount(wrapper) == 0) {
                SysRolePermission row = new SysRolePermission();
                row.setScope(scope);
                row.setRole(role);
                row.setPermissionCode(code);
                sysRolePermissionMapper.insert(row);
            }
        } else {
            sysRolePermissionMapper.delete(wrapper);
        }
        permissionService.reload();

        User operator = userMapper.selectById(userId);
        auditService.log(userId, operator != null ? operator.getEnterpriseId() : null, null,
                "permission.matrix.update", "permission", code,
                (enabled ? "授予 " : "收回 ") + scope + ":" + role + " → " + code);
        return R.ok();
    }
}
