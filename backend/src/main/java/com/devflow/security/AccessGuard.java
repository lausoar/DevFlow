package com.devflow.security;

import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Enterprise;
import com.devflow.entity.Project;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.User;
import com.devflow.mapper.EnterpriseMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 资源级鉴权入口：所有"能不能碰这条数据"的判定收敛到这里。
 *
 * 约定：
 * - 资源不存在 / 不可见 → 404，与"不存在"同构，不泄露存在性；
 * - 可见但权限点不足 → 403；
 * - 跨企业访问 → 一律 403（企业边界不允许以"不存在"掩盖，便于排查越权）。
 *
 * 注意：新增写接口时必须显式调用本类，禁止"只判断登录"。
 */
@Component
@RequiredArgsConstructor
public class AccessGuard {

    private final ProjectAccessService projectAccessService;
    private final PermissionService permissionService;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final EnterpriseMapper enterpriseMapper;

    /** 当前登录用户 ID（未认证时由 Spring Security 拦截，这里只做兜底） */
    public String userId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(401, "登录已过期，请重新登录");
        }
        return String.valueOf(authentication.getPrincipal());
    }

    // ==================== 项目 ====================

    /** 项目必须存在且对当前用户可见，否则 404（不泄露存在性） */
    public Project requireView(String userId, String projectId) {
        Project project = StringUtils.hasText(projectId) ? projectMapper.selectById(projectId) : null;
        if (project == null || !projectAccessService.canView(userId, projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        return project;
    }

    /** 项目可见 + 拥有指定项目级权限点 */
    public Project requireProjectPermission(String userId, String projectId, String permissionCode) {
        Project project = requireView(userId, projectId);
        if (!permissionService.hasProjectPermission(userId, projectId, permissionCode)) {
            throw new BusinessException(403, "无权执行该操作");
        }
        return project;
    }

    /** 项目基本信息编辑（project:edit：OWNER/MANAGER） */
    public Project requireProjectEdit(String userId, String projectId) {
        return requireProjectPermission(userId, projectId,
                com.devflow.common.PermissionCodes.PROJECT_EDIT);
    }

    /** 项目设置（project:manage：OWNER） */
    public Project requireProjectManage(String userId, String projectId) {
        return requireProjectPermission(userId, projectId,
                com.devflow.common.PermissionCodes.PROJECT_MANAGE);
    }

    /** 项目成员管理（member:manage：OWNER/MANAGER） */
    public Project requireMemberManage(String userId, String projectId) {
        return requireProjectPermission(userId, projectId,
                com.devflow.common.PermissionCodes.MEMBER_MANAGE);
    }

    /**
     * 校验 project_member 行确实属于该项目。
     * 防止"A 项目的管理员拿 B 项目的 memberId 改角色/踢人"的水平越权。
     */
    public ProjectMember requireMemberOfProject(String projectId, String memberId) {
        ProjectMember member = StringUtils.hasText(memberId) ? projectMemberMapper.selectById(memberId) : null;
        if (member == null || !projectId.equals(member.getProjectId())) {
            throw new BusinessException(404, "成员不存在");
        }
        return member;
    }

    /** 目标用户必须属于项目所在企业（禁止把外企业用户塞进项目） */
    public void requireSameEnterpriseUser(Project project, String targetUserId) {
        User target = StringUtils.hasText(targetUserId) ? userMapper.selectById(targetUserId) : null;
        if (target == null || !StringUtils.hasText(project.getEnterpriseId())
                || !project.getEnterpriseId().equals(target.getEnterpriseId())) {
            throw new BusinessException(403, "仅可添加本企业成员");
        }
    }

    // ==================== 企业 ====================

    /** 当前用户必须属于该企业，否则 403 */
    public Enterprise requireEnterpriseMember(String userId, String enterpriseId) {
        Enterprise enterprise = StringUtils.hasText(enterpriseId) ? enterpriseMapper.selectById(enterpriseId) : null;
        if (enterprise == null) {
            throw new BusinessException(404, "企业不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !enterpriseId.equals(user.getEnterpriseId())) {
            throw new BusinessException(403, "无权访问其他企业数据");
        }
        return enterprise;
    }

    /** 企业归属 + 企业级权限点 */
    public Enterprise requireEnterprisePermission(String userId, String enterpriseId, String permissionCode) {
        Enterprise enterprise = requireEnterpriseMember(userId, enterpriseId);
        if (!permissionService.hasEnterprisePermission(userId, permissionCode)) {
            throw new BusinessException(403, "无权执行该操作");
        }
        return enterprise;
    }

    /** 是否企业超级管理员 */
    public boolean isEnterpriseSuperAdmin(String userId) {
        User user = StringUtils.hasText(userId) ? userMapper.selectById(userId) : null;
        return user != null && com.devflow.common.EnterpriseRoles.SUPER_ADMIN.equals(user.getRole());
    }

    /** 当前用户所属企业 ID（无企业返回 null） */
    public String enterpriseIdOf(String userId) {
        User user = StringUtils.hasText(userId) ? userMapper.selectById(userId) : null;
        return user != null ? user.getEnterpriseId() : null;
    }
}
