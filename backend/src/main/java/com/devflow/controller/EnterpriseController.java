package com.devflow.controller;

import com.devflow.common.result.R;
import com.devflow.dto.request.CreateEnterpriseRequest;
import com.devflow.dto.request.InviteMemberRequest;
import com.devflow.dto.request.UpdateEnterpriseRequest;
import com.devflow.dto.response.EnterpriseDetailResponse;
import com.devflow.entity.Enterprise;
import com.devflow.entity.User;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.security.AccessGuard;
import com.devflow.service.AuditService;
import com.devflow.service.EnterpriseService;
import com.devflow.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.devflow.dto.request.UpdateMemberDepartmentRequest;
import com.devflow.dto.request.UpdateMemberRoleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 企业管理控制器
 *
 * 鉴权口径：除"创建/加入企业"外，全部要求操作者属于路径中的企业；
 * 敏感字段（wecomWebhook / inviteCode）只对有相应管理权限的成员下发。
 */
@Tag(name = "企业管理", description = "企业创建、成员管理、邀请码")
@RestController
@RequestMapping("/enterprises")
@RequiredArgsConstructor
public class EnterpriseController {

    private final EnterpriseService enterpriseService;
    private final AuditService auditService;
    private final PermissionService permissionService;
    private final AccessGuard guard;

    /** 获取当前用户的企业 */
    @Operation(summary = "获取当前用户的企业信息")
    @GetMapping("/current")
    public R<Enterprise> getCurrent(Authentication authentication) {
        String userId = guard.userId(authentication);
        Enterprise enterprise = enterpriseService.getUserEnterprise(userId);
        return R.ok(maskSensitive(enterprise, userId));
    }

    /** 创建企业 */
    @Operation(summary = "创建新企业")
    @PostMapping
    public R<Enterprise> create(@Valid @RequestBody CreateEnterpriseRequest request,
                                Authentication authentication) {
        String userId = guard.userId(authentication);
        return R.ok(enterpriseService.createEnterprise(request, userId));
    }

    /** 更新企业信息 */
    @Operation(summary = "更新企业信息（名称/Logo/邀请码）")
    @PutMapping("/{id}")
    public R<Enterprise> update(@PathVariable String id,
                                @Valid @RequestBody UpdateEnterpriseRequest request,
                                Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, id, PermissionCodes.ENTERPRISE_MANAGE);
        return R.ok(enterpriseService.updateEnterprise(id, request, userId));
    }

    /** 获取企业详情（仅本企业成员） */
    @Operation(summary = "获取企业详情（含成员和部门树）")
    @GetMapping("/{id}")
    public R<EnterpriseDetailResponse> getDetail(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterpriseMember(userId, id);
        EnterpriseDetailResponse detail = enterpriseService.getEnterpriseDetail(id);
        if (detail != null) {
            if (detail.getEnterprise() != null) {
                detail.setEnterprise(maskSensitive(detail.getEnterprise(), userId));
            }
            // 详情里内嵌的成员列表与实际成员接口保持同一口径，避免绕开脱敏
            if (!canSeeContactInfo(userId) && detail.getMembers() != null) {
                detail.getMembers().forEach(m -> {
                    m.setEmail(com.devflow.util.Masking.email(m.getEmail()));
                    m.setPhone(com.devflow.util.Masking.phone(m.getPhone()));
                });
                maskDepartmentMembers(detail.getDepartments(), true);
            }
        }
        return R.ok(detail);
    }

    /**
     * 获取企业成员列表（仅本企业成员）。
     * 无成员管理权限的调用者看到的是脱敏后的邮箱与手机号 ——
     * 组织架构对全员可见是合理的，但联系方式不是。
     */
    @Operation(summary = "获取企业成员列表")
    @GetMapping("/{id}/members")
    public R<List<User>> getMembers(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterpriseMember(userId, id);
        List<User> members = enterpriseService.getMembers(id);
        if (!canSeeContactInfo(userId)) {
            members.forEach(m -> {
                m.setEmail(com.devflow.util.Masking.email(m.getEmail()));
                m.setPhone(com.devflow.util.Masking.phone(m.getPhone()));
            });
        }
        return R.ok(members);
    }

    /** 部门树里的成员同样脱敏（递归） */
    private void maskDepartmentMembers(List<EnterpriseDetailResponse.DepartmentTree> departments, boolean mask) {
        if (departments == null) {
            return;
        }
        for (EnterpriseDetailResponse.DepartmentTree dept : departments) {
            if (dept.getMembers() != null) {
                dept.getMembers().forEach(m -> {
                    m.setEmail(com.devflow.util.Masking.email(m.getEmail()));
                    m.setPhone(com.devflow.util.Masking.phone(m.getPhone()));
                });
            }
            maskDepartmentMembers(dept.getChildren(), mask);
        }
    }

    /** 能否看到成员完整联系方式：拥有成员管理或企业设置权限 */
    private boolean canSeeContactInfo(String userId) {
        var codes = permissionService.enterprisePermissions(userId);
        return codes.contains(PermissionCodes.MEMBER_MANAGE)
                || codes.contains(PermissionCodes.MEMBER_INVITE)
                || codes.contains(PermissionCodes.ENTERPRISE_MANAGE);
    }

    /** 邀请成员 */
    @Operation(summary = "邀请新成员加入企业")
    @PostMapping("/{id}/members/invite")
    public R<com.devflow.dto.response.InviteMemberResponse> inviteMember(@PathVariable String id,
                                @Valid @RequestBody InviteMemberRequest request,
                                Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, id, PermissionCodes.MEMBER_INVITE);
        return R.ok(enterpriseService.inviteMember(id, request, userId));
    }

    /** 超管重置成员密码：返回随机临时密码（明文仅此一次） */
    @Operation(summary = "重置成员密码（仅超级管理员）")
    @PostMapping("/{id}/members/{memberId}/reset-password")
    public R<java.util.Map<String, String>> resetMemberPassword(@PathVariable String id,
                                          @PathVariable String memberId,
                                          Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterpriseMember(userId, id);
        if (!guard.isEnterpriseSuperAdmin(userId)) {
            throw new BusinessException(403, "仅超级管理员可重置密码");
        }
        String password = enterpriseService.resetMemberPassword(id, memberId, userId);
        // 临时密码只应被当事人看到一次，禁止缓存
        return R.ok(java.util.Map.of("password", password));
    }

    /** 移除成员 */
    @Operation(summary = "移除企业成员")
    @DeleteMapping("/{id}/members/{memberId}")
    public R<Void> removeMember(@PathVariable String id,
                                @PathVariable String memberId,
                                Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, id, PermissionCodes.MEMBER_REMOVE);
        enterpriseService.removeMember(id, memberId, userId);
        return R.ok();
    }

    /** 修改成员角色 */
    @Operation(summary = "修改成员角色")
    @PutMapping("/{id}/members/{memberId}/role")
    public R<User> updateMemberRole(@PathVariable String id,
                                    @PathVariable String memberId,
                                    @Valid @RequestBody UpdateMemberRoleRequest request,
                                    Authentication authentication) {
        String operatorId = guard.userId(authentication);
        guard.requireEnterprisePermission(operatorId, id, PermissionCodes.MEMBER_ROLE);
        return R.ok(enterpriseService.updateMemberRole(id, memberId, request.getRole(), operatorId));
    }

    @Operation(summary = "调整成员部门（departmentId 为空表示移出部门）")
    @PutMapping("/{id}/members/{memberId}/department")
    public R<User> updateMemberDepartment(@PathVariable String id,
                                          @PathVariable String memberId,
                                          @RequestBody UpdateMemberDepartmentRequest request,
                                          Authentication authentication) {
        String operatorId = guard.userId(authentication);
        guard.requireEnterprisePermission(operatorId, id, PermissionCodes.MEMBER_DEPARTMENT);
        return R.ok(enterpriseService.updateMemberDepartment(id, memberId, request.getDepartmentId(), operatorId));
    }

    @Operation(summary = "解散企业（仅企业管理员）")
    @PostMapping("/{id}/dissolve")
    public R<Void> dissolve(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, id, PermissionCodes.ENTERPRISE_MANAGE);
        enterpriseService.dissolveEnterprise(id, userId);
        return R.ok();
    }

    /** 成员自助退出企业（最后一位超管不可退出） */
    @Operation(summary = "退出企业")
    @PostMapping("/{id}/leave")
    public R<Void> leave(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterpriseMember(userId, id);
        enterpriseService.leaveEnterprise(id, userId);
        return R.ok();
    }

    /** 审计日志（audit:read 权限点，当前为管理员及以上；只允许查询本企业） */
    @Operation(summary = "分页查询企业审计日志")
    @GetMapping("/{id}/audit-logs")
    public R<com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.devflow.entity.AuditLog>> auditLogs(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            Authentication authentication) {
        String userId = guard.userId(authentication);
        // 关键：先校验路径企业在调用者企业范围内，再校验权限点，避免跨租户读日志
        guard.requireEnterprisePermission(userId, id, PermissionCodes.AUDIT_READ);
        return R.ok(auditService.pageByEnterprise(id, page, pageSize));
    }

    /** 审计日志导出（CSV，含哈希链字段，便于离线取证） */
    @Operation(summary = "导出企业审计日志（CSV）")
    @GetMapping("/{id}/audit-logs/export")
    public void exportAuditLogs(@PathVariable String id,
                                @RequestParam(defaultValue = "5000") Integer limit,
                                Authentication authentication,
                                jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, id, PermissionCodes.AUDIT_READ);

        var logs = auditService.listForExport(id, limit);
        String fileName = java.net.URLEncoder.encode("审计日志-" + id + ".csv",
                java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
        // BOM：Excel 打开中文不乱码。
        // 注意只能用 writer（先 getOutputStream 再 getWriter 会抛 IllegalStateException）
        response.setCharacterEncoding("UTF-8");
        var writer = new java.io.PrintWriter(response.getWriter());
        writer.write('﻿');
        writer.println("时间,操作人,操作,目标类型,目标ID,IP,用户代理,详情,记录哈希");
        for (var log : logs) {
            writer.println(String.join(",",
                    csv(log.getCreatedAt() == null ? "" : log.getCreatedAt().toString()),
                    csv(log.getUserId()), csv(log.getAction()),
                    csv(log.getTargetType()), csv(log.getTargetId()),
                    csv(log.getIp()), csv(log.getUserAgent()),
                    csv(log.getDetail()), csv(log.getRecordHash())));
        }
        writer.flush();
        auditService.log(userId, id, null, "audit.export", "audit_log", id,
                "导出审计日志 " + logs.size() + " 条");
    }

    /** 校验审计哈希链（发现"被删除/被修改"的证据） */
    @Operation(summary = "校验审计日志哈希链完整性")
    @GetMapping("/{id}/audit-logs/verify")
    public R<AuditService.ChainVerifyResult> verifyAuditChain(@PathVariable String id,
                                                              Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireEnterprisePermission(userId, id, PermissionCodes.AUDIT_READ);
        return R.ok(auditService.verifyChain(id));
    }

    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹并转义 */
    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹并转义 */
    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹并转义 */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\r", " ").replace("\n", " ");
        if (v.contains(",") || v.contains("\"") || v.contains(";")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    /** 通过邀请码加入企业 */
    @Operation(summary = "通过邀请码加入企业")
    @PostMapping("/join")
    public R<Enterprise> joinByInviteCode(@RequestParam String inviteCode,
                                          Authentication authentication) {
        String userId = guard.userId(authentication);
        return R.ok(enterpriseService.joinByInviteCode(inviteCode, userId));
    }

    /**
     * 敏感字段脱敏：企业微信 Webhook（内含机器人 key）与邀请码
     * 只对有企业级管理权限的成员可见，其他成员拿到的是 null。
     */
    private Enterprise maskSensitive(Enterprise enterprise, String userId) {
        if (enterprise == null) {
            return null;
        }
        var codes = permissionService.enterprisePermissions(userId);
        boolean manage = codes.contains(PermissionCodes.ENTERPRISE_MANAGE)
                || codes.contains(PermissionCodes.MEMBER_INVITE);
        if (!manage) {
            enterprise.setWecomWebhook(null);
            enterprise.setInviteCode(null);
        }
        return enterprise;
    }
}
