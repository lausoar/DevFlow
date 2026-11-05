package com.devflow.common;

/**
 * 权限点常量（与 sys_permission 表种子一一对应）
 * 业务代码只依赖权限点做判定，禁止再手写角色名比较；
 * 角色→权限点的映射关系由 sys_role_permission 表决定（全局固定一套）。
 * 设计文档：docs/PERMISSION_DESIGN.md
 */
public final class PermissionCodes {

    // ===== 企业级 =====
    /** 企业设置/邀请码/解散企业 */
    public static final String ENTERPRISE_MANAGE = "enterprise:manage";
    /** 邀请成员 */
    public static final String MEMBER_INVITE = "member:invite";
    /** 移除成员 */
    public static final String MEMBER_REMOVE = "member:remove";
    /** 调整成员角色 */
    public static final String MEMBER_ROLE = "member:role";
    /** 调整成员部门 */
    public static final String MEMBER_DEPARTMENT = "member:department";
    /** 查看审计日志（预留） */
    public static final String AUDIT_READ = "audit:read";

    // ===== 项目级 =====
    /** 项目设置/归档/删除 */
    public static final String PROJECT_MANAGE = "project:manage";
    /** 项目成员管理（加人/改角色/移除） */
    public static final String MEMBER_MANAGE = "member:manage";
    /** 新建 Bug */
    public static final String BUG_CREATE = "bug:create";
    /** 编辑 Bug */
    public static final String BUG_EDIT = "bug:edit";
    /** 删除 Bug */
    public static final String BUG_DELETE = "bug:delete";

    /** 测试用例管理（新建/编辑/删除/执行/转缺陷） */
    public static final String TESTCASE_MANAGE = "testcase:manage";

    /**
     * 变更 Bug 状态——特殊：不走角色映射，走资源关系（负责人/创建人），
     * 由调用方基于 bug.assigneeId/reporterId 直接判定（见 BugController）。
     */
    public static final String BUG_STATUS_CHANGE = "bug:status:change";

    /** 作用域 */
    public static final String SCOPE_ENTERPRISE = "ENTERPRISE";
    public static final String SCOPE_PROJECT = "PROJECT";

    private PermissionCodes() {
    }
}
