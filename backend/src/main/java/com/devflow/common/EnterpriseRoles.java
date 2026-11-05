package com.devflow.common;

import java.util.Map;

/**
 * 企业角色等级定义（4 级，2026-10 从 7 级收敛：
 * DIRECTOR/SUPERVISOR 归入 MANAGER，LEADER 归入 MEMBER，存量数据由 schema.sql 幂等迁移）
 * 等级越高权限越大；管理操作只能作用于等级严格低于自己的成员，
 * 赋予/变更的角色也必须严格低于操作者自身的等级（防止越权提权）。
 * 角色拥有的具体权限点见 sys_role_permission 表（docs/PERMISSION_DESIGN.md）。
 */
public final class EnterpriseRoles {

    /** 超级管理员（企业所有者）：企业设置、邀请码、解散企业、全部成员管理 */
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    /** 管理员：全部成员管理、审计日志（预留） */
    public static final String ADMIN = "ADMIN";
    /** 经理：邀请/移除成员、调整成员角色与部门 */
    public static final String MANAGER = "MANAGER";
    /** 成员（默认，最低） */
    public static final String MEMBER = "MEMBER";

    private EnterpriseRoles() {
    }

    private static final Map<String, Integer> LEVELS = Map.of(
            SUPER_ADMIN, 100,
            ADMIN, 80,
            MANAGER, 60,
            MEMBER, 10
    );

    /** 角色等级（未知角色或 null 按最低处理） */
    public static int level(String role) {
        return role == null ? 10 : LEVELS.getOrDefault(role, 10);
    }

    /** 全部企业角色（按等级降序），权限矩阵展示用 */
    public static java.util.List<String> allRoles() {
        return LEVELS.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    /** 是否合法角色 */
    public static boolean isValid(String role) {
        return role != null && LEVELS.containsKey(role);
    }

    /** 是否可管理他人（拥有任意管理权限的最低门槛：经理及以上） */
    public static boolean canManage(String role) {
        return level(role) >= level(MANAGER);
    }

    /** 操作者是否可管理目标成员（目标等级须严格低于操作者） */
    public static boolean canOperate(String operatorRole, String targetRole) {
        return level(operatorRole) > level(targetRole);
    }

    /** 操作者是否可赋予目标角色（新角色等级须严格低于操作者） */
    public static boolean canGrant(String operatorRole, String targetRole) {
        return isValid(targetRole) && level(targetRole) < level(operatorRole);
    }
}
