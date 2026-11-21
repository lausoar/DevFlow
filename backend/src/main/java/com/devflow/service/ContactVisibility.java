package com.devflow.service;

import com.devflow.common.PermissionCodes;
import com.devflow.entity.ProjectMember;
import com.devflow.util.Masking;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 联系人信息可见性：邮箱/手机号对谁可见。
 *
 * 规则收敛到一处（原先只写在 EnterpriseController 私有方法里），
 * 项目成员、频道成员、部门树等接口复用同一判定，避免"企业页脱敏了、
 * 项目成员列表又把邮箱原样吐出来"这种点状遗漏。
 *
 * 口径：拥有成员管理/邀请/企业设置权限的人看全量，其余人一律脱敏。
 */
@Service
@RequiredArgsConstructor
public class ContactVisibility {

    private final PermissionService permissionService;

    public boolean canSeeContactInfo(String userId) {
        Set<String> codes = permissionService.enterprisePermissions(userId);
        return codes.contains(PermissionCodes.MEMBER_MANAGE)
                || codes.contains(PermissionCodes.MEMBER_INVITE)
                || codes.contains(PermissionCodes.ENTERPRISE_MANAGE);
    }

    /** 无权限时原地脱敏项目成员邮箱（字段就这一个敏感项） */
    public List<ProjectMember> maskProjectMembers(String userId, List<ProjectMember> members) {
        if (members == null || canSeeContactInfo(userId)) {
            return members;
        }
        for (ProjectMember member : members) {
            member.setEmail(Masking.email(member.getEmail()));
        }
        return members;
    }

    /**
     * 项目成员列表：项目 OWNER/MANAGER 需要看到真实邮箱才能邀请、移除和协调成员，
     * 因此除了企业级联系人权限，也认可本项目的 member:manage 权限点。
     */
    public List<ProjectMember> maskProjectMembers(String userId, String projectId,
                                                   List<ProjectMember> members) {
        if (permissionService.hasProjectPermission(userId, projectId, PermissionCodes.MEMBER_MANAGE)) {
            return members;
        }
        return maskProjectMembers(userId, members);
    }
}
