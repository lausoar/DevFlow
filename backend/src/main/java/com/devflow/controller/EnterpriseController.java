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
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 企业管理控制器
 */
@Tag(name = "企业管理", description = "企业创建、成员管理、邀请码")
@RestController
@RequestMapping("/enterprises")
@RequiredArgsConstructor
public class EnterpriseController {

    private final EnterpriseService enterpriseService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    /** 获取当前用户的企业 */
    @Operation(summary = "获取当前用户的企业信息")
    @GetMapping("/current")
    public R<Enterprise> getCurrent(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(enterpriseService.getUserEnterprise(userId));
    }

    /** 创建企业 */
    @Operation(summary = "创建新企业")
    @PostMapping
    public R<Enterprise> create(@Valid @RequestBody CreateEnterpriseRequest request,
                                Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(enterpriseService.createEnterprise(request, userId));
    }

    /** 更新企业信息 */
    @Operation(summary = "更新企业信息（名称/Logo/邀请码）")
    @PutMapping("/{id}")
    public R<Enterprise> update(@PathVariable String id,
                                @Valid @RequestBody UpdateEnterpriseRequest request,
                                Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(enterpriseService.updateEnterprise(id, request, userId));
    }

    /** 获取企业详情 */
    @Operation(summary = "获取企业详情（含成员和部门树）")
    @GetMapping("/{id}")
    public R<EnterpriseDetailResponse> getDetail(@PathVariable String id) {
        return R.ok(enterpriseService.getEnterpriseDetail(id));
    }

    /** 获取企业成员列表 */
    @Operation(summary = "获取企业成员列表")
    @GetMapping("/{id}/members")
    public R<List<User>> getMembers(@PathVariable String id) {
        return R.ok(enterpriseService.getMembers(id));
    }

    /** 邀请成员 */
    @Operation(summary = "邀请新成员加入企业")
    @PostMapping("/{id}/members/invite")
    public R<com.devflow.dto.response.InviteMemberResponse> inviteMember(@PathVariable String id,
                                @Valid @RequestBody InviteMemberRequest request,
                                Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(enterpriseService.inviteMember(id, request, userId));
    }

    /** 超管重置成员密码：返回随机临时密码（明文仅此一次） */
    @Operation(summary = "重置成员密码（仅超级管理员）")
    @PostMapping("/{id}/members/{memberId}/reset-password")
    public R<java.util.Map<String, String>> resetMemberPassword(@PathVariable String id,
                                          @PathVariable String memberId,
                                          Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        String password = enterpriseService.resetMemberPassword(id, memberId, userId);
        return R.ok(java.util.Map.of("password", password));
    }

    /** 移除成员 */
    @Operation(summary = "移除企业成员")
    @DeleteMapping("/{id}/members/{memberId}")
    public R<Void> removeMember(@PathVariable String id,
                                @PathVariable String memberId,
                                Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        enterpriseService.removeMember(id, memberId, userId);
        return R.ok();
    }

    /** 解散企业（仅企业管理员） */
    @Operation(summary = "修改成员角色")
    @PutMapping("/{id}/members/{memberId}/role")
    public R<User> updateMemberRole(@PathVariable String id,
                                    @PathVariable String memberId,
                                    @Valid @RequestBody UpdateMemberRoleRequest request,
                                    Authentication authentication) {
        String operatorId = (String) authentication.getPrincipal();
        return R.ok(enterpriseService.updateMemberRole(id, memberId, request.getRole(), operatorId));
    }

    @Operation(summary = "调整成员部门（departmentId 为空表示移出部门）")
    @PutMapping("/{id}/members/{memberId}/department")
    public R<User> updateMemberDepartment(@PathVariable String id,
                                          @PathVariable String memberId,
                                          @RequestBody UpdateMemberDepartmentRequest request,
                                          Authentication authentication) {
        String operatorId = (String) authentication.getPrincipal();
        return R.ok(enterpriseService.updateMemberDepartment(id, memberId, request.getDepartmentId(), operatorId));
    }

    @Operation(summary = "解散企业（仅企业管理员）")
    @PostMapping("/{id}/dissolve")
    public R<Void> dissolve(@PathVariable String id, Authentication authentication) {
        enterpriseService.dissolveEnterprise(id, (String) authentication.getPrincipal());
        return R.ok();
    }

    /** 成员自助退出企业（最后一位超管不可退出） */
    @Operation(summary = "退出企业")
    @PostMapping("/{id}/leave")
    public R<Void> leave(@PathVariable String id, Authentication authentication) {
        enterpriseService.leaveEnterprise(id, (String) authentication.getPrincipal());
        return R.ok();
    }

    /** 审计日志（audit:read 权限点，当前为管理员及以上） */
    @Operation(summary = "分页查询企业审计日志")
    @GetMapping("/{id}/audit-logs")
    public R<com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.devflow.entity.AuditLog>> auditLogs(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        if (!permissionService.hasEnterprisePermission(userId, PermissionCodes.AUDIT_READ)) {
            throw new BusinessException(403, "无权查看审计日志");
        }
        return R.ok(auditService.pageByEnterprise(id, page, pageSize));
    }

    /** 通过邀请码加入企业 */
    @Operation(summary = "通过邀请码加入企业")
    @PostMapping("/join")
    public R<Enterprise> joinByInviteCode(@RequestParam String inviteCode,
                                          Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(enterpriseService.joinByInviteCode(inviteCode, userId));
    }
}
