package com.devflow.service;

/**
 * 统一权限判定服务（RBAC 骨架，映射关系见 sys_role_permission 表）
 * 业务代码只依赖权限点做判定，不再手写角色名比较。
 *
 * 映射按企业隔离：企业首次调整矩阵时生成自己的快照（enterprise_id = 本企业），
 * 未调整过的企业回落到全局默认模板（enterprise_id = '*'）。
 * 设计文档：docs/PERMISSION_DESIGN.md
 */
public interface PermissionService {

    /** 判定用户在企业级权限点上是否放行（按 user.role 映射，取用户所属企业的矩阵） */
    boolean hasEnterprisePermission(String userId, String permissionCode);

    /** 判定用户在指定项目上是否拥有项目级权限点（按 project_member.role 映射，取项目所属企业的矩阵） */
    boolean hasProjectPermission(String userId, String projectId, String permissionCode);

    /** 用户在企业作用域拥有的权限点集合（/permissions/me 用，不做拒绝审计） */
    java.util.Set<String> enterprisePermissions(String userId);

    /** 用户在指定项目上拥有的权限点集合（/permissions/me 用，不做拒绝审计） */
    java.util.Set<String> projectPermissions(String userId, String projectId);

    /** 重新加载全部企业的角色-权限映射（启动时 / 全量刷新） */
    void reload();
}
