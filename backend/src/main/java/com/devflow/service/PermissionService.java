package com.devflow.service;

/**
 * 统一权限判定服务（RBAC 骨架，映射关系见 sys_role_permission 表）
 * 业务代码只依赖权限点做判定，不再手写角色名比较。
 * 设计文档：docs/PERMISSION_DESIGN.md
 */
public interface PermissionService {

    /** 判定用户在企业级权限点上是否放行（按 user.role 映射） */
    boolean hasEnterprisePermission(String userId, String permissionCode);

    /** 判定用户在指定项目上是否拥有项目级权限点（按 project_member.role 映射） */
    boolean hasProjectPermission(String userId, String projectId, String permissionCode);

    /** 用户在企业作用域拥有的权限点集合（/permissions/me 用，不做拒绝审计） */
    java.util.Set<String> enterprisePermissions(String userId);

    /** 用户在指定项目上拥有的权限点集合（/permissions/me 用，不做拒绝审计） */
    java.util.Set<String> projectPermissions(String userId, String projectId);

    /** 重新加载角色-权限映射（修改 sys_role_permission 后调用） */
    void reload();
}
