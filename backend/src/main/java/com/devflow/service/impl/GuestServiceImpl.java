package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.entity.ProjectFeedback;
import com.devflow.entity.ProjectGuest;
import com.devflow.entity.Requirement;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectFeedbackMapper;
import com.devflow.mapper.ProjectGuestMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.common.PermissionCodes;
import com.devflow.service.AuditService;
import com.devflow.service.GuestService;
import com.devflow.service.PermissionService;
import com.devflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游客分享项目实现
 * 安全约束：token 只存 SHA-256 哈希；游客接口仅限白名单权限集（看脱敏信息 + 提交）；
 * 提交按 token+IP 限流（10 次/小时）；游客 Bug 进入待确认队列，测试人员确认后才正式流转
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuestServiceImpl implements GuestService {

    private final ProjectGuestMapper projectGuestMapper;
    private final ProjectFeedbackMapper projectFeedbackMapper;
    private final ProjectMapper projectMapper;
    private final RequirementMapper requirementMapper;
    private final BugMapper bugMapper;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 单 token+IP 每小时提交上限 */
    private static final int RATE_LIMIT = 10;
    private static final long RATE_WINDOW_MS = 3600_000L;
    /** key = tokenHash:ip → 提交时间戳队列 */
    private final Map<String, Deque<Long>> rateMap = new ConcurrentHashMap<>();

    // ============================================
    // 管理端
    // ============================================

    @Override
    public Map<String, String> generateLink(String projectId, Integer expiresInDays, String operatorId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = "gf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = sha256(token);

        ProjectGuest guest = new ProjectGuest();
        guest.setProjectId(projectId);
        guest.setTokenHash(tokenHash);
        if (expiresInDays != null && expiresInDays > 0) {
            guest.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        }
        guest.setRevoked(0);
        guest.setCreatedBy(operatorId);
        projectGuestMapper.insert(guest);

        auditService.log(operatorId, project.getEnterpriseId(), projectId, "guest.link.create",
                "project_guest", guest.getId(), "生成游客链接" + (expiresInDays != null && expiresInDays > 0 ? "（" + expiresInDays + " 天有效）" : "（长期）"));

        Map<String, String> result = new HashMap<>();
        result.put("guestId", guest.getId());
        result.put("token", token);
        return result;
    }

    @Override
    public List<Map<String, Object>> listLinks(String projectId) {
        List<ProjectGuest> guests = projectGuestMapper.selectList(
                new LambdaQueryWrapper<ProjectGuest>()
                        .eq(ProjectGuest::getProjectId, projectId)
                        .orderByDesc(ProjectGuest::getCreatedAt));
        return guests.stream().map(g -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.getId());
            m.put("nickname", g.getNickname());
            m.put("email", g.getEmail());
            m.put("revoked", g.getRevoked());
            m.put("expiresAt", g.getExpiresAt());
            m.put("createdAt", g.getCreatedAt());
            return m;
        }).toList();
    }

    @Override
    public void revokeLink(String projectId, String guestId, String operatorId) {
        ProjectGuest guest = projectGuestMapper.selectById(guestId);
        if (guest == null || !guest.getProjectId().equals(projectId)) {
            throw new BusinessException(404, "游客链接不存在");
        }
        guest.setRevoked(1);
        projectGuestMapper.updateById(guest);
        auditService.log(operatorId, null, projectId, "guest.link.revoke",
                "project_guest", guestId, "撤销游客链接");
    }

    @Override
    public void confirmGuestBug(String bugId, String operatorId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null || !Integer.valueOf(1).equals(bug.getPending())) {
            throw new BusinessException(404, "待确认的游客 Bug 不存在");
        }
        bug.setPending(0);
        bugMapper.updateById(bug);
        Project project = projectMapper.selectById(bug.getProjectId());
        auditService.log(operatorId, project != null ? project.getEnterpriseId() : null, bug.getProjectId(),
                "guest.bug.confirm", "bug", bugId, "确认游客提交的 Bug: " + bug.getTitle());
    }

    // ============================================
    // 反馈（站内）
    // ============================================

    @Override
    public List<ProjectFeedback> listFeedback(String projectId) {
        return projectFeedbackMapper.selectList(
                new LambdaQueryWrapper<ProjectFeedback>()
                        .eq(ProjectFeedback::getProjectId, projectId)
                        .orderByDesc(ProjectFeedback::getCreatedAt));
    }

    @Override
    public ProjectFeedback updateFeedbackStatus(String feedbackId, String status, String operatorId) {
        if (!List.of("ACCEPTED", "REJECTED").contains(status)) {
            throw new BusinessException(400, "无效的反馈状态");
        }
        ProjectFeedback feedback = projectFeedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(404, "反馈不存在");
        }
        if (!permissionService.hasProjectPermission(operatorId, feedback.getProjectId(), PermissionCodes.BUG_EDIT)) {
            throw new BusinessException(403, "仅测试人员或项目管理员可处理反馈");
        }
        feedback.setStatus(status);
        projectFeedbackMapper.updateById(feedback);
        Project project = projectMapper.selectById(feedback.getProjectId());
        auditService.log(operatorId, project != null ? project.getEnterpriseId() : null, feedback.getProjectId(),
                "feedback.status", "feedback", feedbackId,
                "反馈「" + feedback.getTitle() + "」状态: " + status);
        return feedback;
    }

    @Override
    public Bug convertFeedbackToBug(String feedbackId, String operatorId) {
        ProjectFeedback feedback = projectFeedbackMapper.selectById(feedbackId);
        if (feedback == null) {
            throw new BusinessException(404, "反馈不存在");
        }
        if (!permissionService.hasProjectPermission(operatorId, feedback.getProjectId(), PermissionCodes.BUG_CREATE)) {
            throw new BusinessException(403, "仅测试人员或项目管理员可转化反馈");
        }
        if ("CONVERTED".equals(feedback.getStatus())) {
            throw new BusinessException(400, "该反馈已转化为 Bug");
        }

        Bug bug = new Bug();
        bug.setProjectId(feedback.getProjectId());
        bug.setTitle(feedback.getTitle());
        bug.setDescription(feedback.getContent());
        bug.setStatus("NEW");
        bug.setPriority("MEDIUM");
        bug.setSeverity("MAJOR");
        bug.setPending(0);
        bug.setReporterType("GUEST".equals(feedback.getSubmitterType()) ? "GUEST" : "USER");
        if ("GUEST".equals(feedback.getSubmitterType())) {
            bug.setGuestId(feedback.getGuestId());
            bug.setGuestNickname(feedback.getGuestNickname());
            // reporter_id 列 NOT NULL，游客转化场景以游客身份充当报告人
            bug.setReporterId(feedback.getGuestId());
        } else {
            bug.setReporterId(feedback.getSubmitterUserId());
        }
        bugMapper.insert(bug);

        feedback.setStatus("CONVERTED");
        feedback.setConvertedBugId(bug.getId());
        projectFeedbackMapper.updateById(feedback);

        Project project = projectMapper.selectById(feedback.getProjectId());
        auditService.log(operatorId, project != null ? project.getEnterpriseId() : null, feedback.getProjectId(),
                "feedback.convert", "feedback", feedbackId,
                "反馈「" + feedback.getTitle() + "」转化为 Bug");

        // 通知反馈提交者（站内用户）
        if ("USER".equals(feedback.getSubmitterType()) && StringUtils.hasText(feedback.getSubmitterUserId())) {
            notificationService.createNotification(feedback.getSubmitterUserId(), "BUG_UPDATE",
                    "建议已转化为 Bug", "你的建议「" + feedback.getTitle() + "」已转化为 Bug 跟进", "/projects/" + bug.getProjectId() + "?tab=bugs");
        }
        return bug;
    }

    // ============================================
    // 游客端（免登录）
    // ============================================

    @Override
    public Map<String, Object> guestProjectInfo(String token) {
        ProjectGuest guest = resolveGuest(token);
        Project project = projectMapper.selectById(guest.getProjectId());

        Map<String, Object> result = new HashMap<>();
        Map<String, Object> projectInfo = new HashMap<>();
        projectInfo.put("name", project.getName());
        projectInfo.put("description", project.getDescription());
        projectInfo.put("projectKey", project.getProjectKey());
        result.put("project", projectInfo);

        // 最新已通过需求（仅标题/状态/时间，脱敏）
        List<Map<String, Object>> requirements = requirementMapper.selectList(
                        new LambdaQueryWrapper<Requirement>()
                                .eq(Requirement::getProjectId, guest.getProjectId())
                                .eq(Requirement::getStatus, "OPEN")
                                .orderByDesc(Requirement::getCreatedAt)
                                .last("LIMIT 10"))
                .stream().map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("title", r.getTitle());
                    m.put("status", r.getStatus());
                    m.put("createdAt", r.getCreatedAt());
                    return m;
                }).toList();
        result.put("requirements", requirements);
        return result;
    }

    @Override
    public Bug submitGuestBug(String token, String title, String description, String environment,
                              String nickname, String email, String ip) {
        ProjectGuest guest = resolveGuest(token);
        checkRateLimit(guest.getTokenHash(), ip);
        validateSubmission(title, nickname);

        Bug bug = new Bug();
        bug.setProjectId(guest.getProjectId());
        bug.setTitle(title.trim());
        bug.setDescription(description);
        bug.setEnvironment(environment);
        bug.setStatus("NEW");
        bug.setPriority("MEDIUM");
        bug.setSeverity("MINOR");
        bug.setReporterId(guest.getId());
        bug.setReporterType("GUEST");
        bug.setGuestId(guest.getId());
        bug.setGuestNickname(nickname.trim());
        bug.setPending(1);
        bugMapper.insert(bug);

        rememberGuestContact(guest, nickname, email);
        notifyOwner(guest.getProjectId(), "游客提交了新 Bug 待确认",
                "游客「" + nickname.trim() + "」提交了 Bug: " + title.trim(), "/projects/" + guest.getProjectId());
        auditGuestSubmission(guest, "guest.bug.submit", bug.getId(), "游客「" + nickname.trim() + "」提交 Bug: " + title.trim());
        return bug;
    }

    @Override
    public ProjectFeedback submitGuestFeedback(String token, String type, String title, String content,
                                               String nickname, String email, String ip) {
        ProjectGuest guest = resolveGuest(token);
        checkRateLimit(guest.getTokenHash(), ip);
        validateSubmission(title, nickname);

        String feedbackType = List.of("SUGGESTION", "IMPROVEMENT", "OTHER").contains(type) ? type : "SUGGESTION";
        ProjectFeedback feedback = new ProjectFeedback();
        feedback.setProjectId(guest.getProjectId());
        feedback.setType(feedbackType);
        feedback.setTitle(title.trim());
        feedback.setContent(content);
        feedback.setSubmitterType("GUEST");
        feedback.setGuestId(guest.getId());
        feedback.setGuestNickname(nickname.trim());
        feedback.setStatus("OPEN");
        projectFeedbackMapper.insert(feedback);

        rememberGuestContact(guest, nickname, email);
        notifyOwner(guest.getProjectId(), "游客提交了新建议",
                "游客「" + nickname.trim() + "」提交了建议: " + title.trim(), "/projects/" + guest.getProjectId());
        auditGuestSubmission(guest, "guest.feedback.submit", feedback.getId(),
                "游客「" + nickname.trim() + "」提交建议: " + title.trim());
        return feedback;
    }

    @Override
    public Map<String, Object> guestSubmissions(String token) {
        ProjectGuest guest = resolveGuest(token);
        List<Map<String, Object>> bugs = bugMapper.selectList(
                        new LambdaQueryWrapper<Bug>()
                                .eq(Bug::getGuestId, guest.getId())
                                .orderByDesc(Bug::getCreatedAt)
                                .last("LIMIT 20"))
                .stream().map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("title", b.getTitle());
                    m.put("status", b.getStatus());
                    m.put("pending", b.getPending());
                    m.put("createdAt", b.getCreatedAt());
                    return m;
                }).toList();
        List<Map<String, Object>> feedbacks = projectFeedbackMapper.selectList(
                        new LambdaQueryWrapper<ProjectFeedback>()
                                .eq(ProjectFeedback::getGuestId, guest.getId())
                                .orderByDesc(ProjectFeedback::getCreatedAt)
                                .last("LIMIT 20"))
                .stream().map(f -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("title", f.getTitle());
                    m.put("status", f.getStatus());
                    m.put("createdAt", f.getCreatedAt());
                    return m;
                }).toList();
        return Map.of("bugs", bugs, "feedbacks", feedbacks);
    }

    // ============================================
    // 内部工具
    // ============================================

    /** 校验 token（哈希查找 + 未撤销 + 未过期），返回游客与项目 */
    private ProjectGuest resolveGuest(String token) {
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(404, "游客链接无效");
        }
        ProjectGuest guest = projectGuestMapper.selectOne(
                new LambdaQueryWrapper<ProjectGuest>().eq(ProjectGuest::getTokenHash, sha256(token)));
        if (guest == null || Integer.valueOf(1).equals(guest.getRevoked())) {
            throw new BusinessException(404, "游客链接无效或已被撤销");
        }
        if (guest.getExpiresAt() != null && guest.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(404, "游客链接已过期");
        }
        Project project = projectMapper.selectById(guest.getProjectId());
        if (project == null) {
            throw new BusinessException(404, "项目不存在或已删除");
        }
        return guest;
    }

    private void validateSubmission(String title, String nickname) {
        if (!StringUtils.hasText(title) || title.trim().length() > 200) {
            throw new BusinessException(400, "标题必填且不超过 200 字");
        }
        if (!StringUtils.hasText(nickname) || nickname.trim().length() > 50) {
            throw new BusinessException(400, "昵称必填且不超过 50 字");
        }
    }

    /** 首次提交时记录昵称/邮箱到游客行，便于识别 */
    private void rememberGuestContact(ProjectGuest guest, String nickname, String email) {
        boolean dirty = false;
        if (!StringUtils.hasText(guest.getNickname())) {
            guest.setNickname(nickname.trim());
            dirty = true;
        }
        if (StringUtils.hasText(email) && !StringUtils.hasText(guest.getEmail())) {
            guest.setEmail(email.trim());
            dirty = true;
        }
        if (dirty) {
            projectGuestMapper.updateById(guest);
        }
    }

    private void notifyOwner(String projectId, String title, String content, String url) {
        try {
            Project project = projectMapper.selectById(projectId);
            if (project != null && StringUtils.hasText(project.getOwnerId())) {
                notificationService.createNotification(project.getOwnerId(), "GUEST_SUBMIT", title, content, url);
            }
        } catch (Exception e) {
            log.warn("游客提交通知发送失败: {}", e.getMessage());
        }
    }

    private void auditGuestSubmission(ProjectGuest guest, String action, String targetId, String detail) {
        Project project = projectMapper.selectById(guest.getProjectId());
        auditService.log(guest.getId(), project != null ? project.getEnterpriseId() : null, guest.getProjectId(),
                action, "guest", targetId, detail);
    }

    /** token + IP 限流：10 次/小时 */
    private void checkRateLimit(String tokenHash, String ip) {
        String key = tokenHash + ":" + (ip == null ? "unknown" : ip);
        long now = System.currentTimeMillis();
        Deque<Long> queue = rateMap.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (queue) {
            while (!queue.isEmpty() && now - queue.peekFirst() > RATE_WINDOW_MS) {
                queue.pollFirst();
            }
            if (queue.size() >= RATE_LIMIT) {
                throw new BusinessException(429, "提交过于频繁，请一小时后再试");
            }
            queue.addLast(now);
        }
        if (rateMap.size() > 10000) {
            rateMap.clear();
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BusinessException(500, "token 处理失败");
        }
    }
}
