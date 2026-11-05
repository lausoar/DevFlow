package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.Bug;
import com.devflow.entity.ProjectFeedback;
import com.devflow.entity.ProjectMember;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.service.GuestService;
import com.devflow.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 游客分享项目控制器
 * /projects/{id}/guest-links 管理端（需登录 + member:manage 权限点）
 * /guest/** 游客端（免登录，SecurityConfig 白名单）
 */
@Tag(name = "游客分享", description = "游客链接管理与游客提交")
@RestController
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;
    private final PermissionService permissionService;
    private final ProjectMemberMapper projectMemberMapper;

    // ============================================
    // 管理端（需登录）
    // ============================================

    public static class GenerateLinkRequest {
        private Integer expiresInDays;

        public Integer getExpiresInDays() { return expiresInDays; }
    }

    /** 生成游客链接（仅 OWNER/MANAGER），token 明文仅返回一次 */
    @Operation(summary = "生成游客链接")
    @PostMapping("/projects/{id}/guest-links")
    public R<Map<String, String>> generateLink(@PathVariable String id,
                                               @RequestBody(required = false) GenerateLinkRequest body,
                                               Authentication authentication) {
        requireProjectManager(id, authentication);
        Integer days = body != null ? body.getExpiresInDays() : null;
        return R.ok(guestService.generateLink(id, days, (String) authentication.getPrincipal()));
    }

    /** 游客链接列表（脱敏） */
    @Operation(summary = "游客链接列表")
    @GetMapping("/projects/{id}/guest-links")
    public R<List<Map<String, Object>>> listLinks(@PathVariable String id, Authentication authentication) {
        requireProjectManager(id, authentication);
        return R.ok(guestService.listLinks(id));
    }

    /** 撤销游客链接 */
    @Operation(summary = "撤销游客链接")
    @DeleteMapping("/projects/{id}/guest-links/{guestId}")
    public R<Void> revokeLink(@PathVariable String id, @PathVariable String guestId,
                              Authentication authentication) {
        requireProjectManager(id, authentication);
        guestService.revokeLink(id, guestId, (String) authentication.getPrincipal());
        return R.ok();
    }

    // ============================================
    // 反馈（站内成员）
    // ============================================

    /** 项目反馈列表（任意项目成员可看） */
    @Operation(summary = "项目反馈列表")
    @GetMapping("/projects/{id}/feedback")
    public R<List<ProjectFeedback>> listFeedback(@PathVariable String id, Authentication authentication) {
        requireProjectMember(id, authentication);
        return R.ok(guestService.listFeedback(id));
    }

    /** 处理反馈（接受/拒绝，权限在 Service 内按 bug:edit 判定） */
    @Operation(summary = "处理反馈（仅测试人员及以上）")
    @PutMapping("/feedback/{feedbackId}/status")
    public R<ProjectFeedback> updateFeedbackStatus(@PathVariable String feedbackId,
                                                   @RequestBody Map<String, String> body,
                                                   Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(guestService.updateFeedbackStatus(feedbackId, body.get("status"), userId));
    }

    /** 建议转化为 Bug（权限在 Service 内按 bug:create 判定） */
    @Operation(summary = "建议转化为 Bug")
    @PostMapping("/feedback/{feedbackId}/convert")
    public R<Bug> convertFeedback(@PathVariable String feedbackId, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        return R.ok(guestService.convertFeedbackToBug(feedbackId, userId));
    }

    // ============================================
    // 游客端（免登录，/guest/** 在 SecurityConfig 白名单）
    // ============================================

    public static class GuestBugRequest {
        private String title;
        private String description;
        private String environment;
        private String nickname;
        private String email;

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getEnvironment() { return environment; }
        public String getNickname() { return nickname; }
        public String getEmail() { return email; }
    }

    public static class GuestFeedbackRequest {
        private String type;
        private String title;
        private String content;
        private String nickname;
        private String email;

        public String getType() { return type; }
        public String getTitle() { return title; }
        public String getContent() { return content; }
        public String getNickname() { return nickname; }
        public String getEmail() { return email; }
    }

    /** 游客查看项目脱敏信息（名称/描述/最新已通过需求） */
    @Operation(summary = "游客查看项目信息")
    @GetMapping("/guest/{token}/project")
    public R<Map<String, Object>> guestProject(@PathVariable String token) {
        return R.ok(guestService.guestProjectInfo(token));
    }

    /** 游客提交 Bug（进待确认队列） */
    @Operation(summary = "游客提交 Bug")
    @PostMapping("/guest/{token}/bugs")
    public R<Bug> guestSubmitBug(@PathVariable String token,
                                 @RequestBody GuestBugRequest body,
                                 HttpServletRequest request) {
        return R.ok(guestService.submitGuestBug(token, body.getTitle(), body.getDescription(),
                body.getEnvironment(), body.getNickname(), body.getEmail(), clientIp(request)));
    }

    /** 游客提交建议 */
    @Operation(summary = "游客提交建议")
    @PostMapping("/guest/{token}/feedback")
    public R<ProjectFeedback> guestSubmitFeedback(@PathVariable String token,
                                                  @RequestBody GuestFeedbackRequest body,
                                                  HttpServletRequest request) {
        return R.ok(guestService.submitGuestFeedback(token, body.getType(), body.getTitle(),
                body.getContent(), body.getNickname(), body.getEmail(), clientIp(request)));
    }

    /** 游客查看自己（该链接）的提交与状态 */
    @Operation(summary = "游客查看提交记录")
    @GetMapping("/guest/{token}/submissions")
    public R<Map<String, Object>> guestSubmissions(@PathVariable String token) {
        return R.ok(guestService.guestSubmissions(token));
    }

    // ============================================
    // 内部
    // ============================================

    private void requireProjectManager(String projectId, Authentication authentication) {
        if (!permissionService.hasProjectPermission((String) authentication.getPrincipal(), projectId, PermissionCodes.MEMBER_MANAGE)) {
            throw new BusinessException(403, "仅项目负责人或项目管理员可管理游客链接");
        }
    }

    private void requireProjectMember(String projectId, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, projectId)
               .eq(ProjectMember::getUserId, userId);
        if (projectMemberMapper.selectCount(wrapper) == 0) {
            throw new BusinessException(403, "仅项目成员可查看反馈");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
