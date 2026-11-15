package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.dto.request.CreateProjectRequest;
import com.devflow.dto.request.UpdateProjectRequest;
import com.devflow.entity.ChatChannel;
import com.devflow.entity.Enterprise;
import com.devflow.entity.Project;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.User;
import com.devflow.mapper.ChatChannelMapper;
import com.devflow.mapper.EnterpriseMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.AuditService;
import com.devflow.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目控制器
 *
 * 鉴权口径：
 * - 读：项目可见性（成员 ∪ 企业超管管理视野），不可见一律 404 不泄露存在性；
 * - 写：在可见性之上叠加项目级权限点（project:edit / project:manage / member:manage）；
 * - 请求体一律使用白名单 DTO，禁止把实体直接交给 updateById（防 mass assignment）。
 */
@Tag(name = "项目管理", description = "项目的增删改查、成员管理")
@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final com.devflow.service.ProjectAccessService projectAccessService;
    private final UserMapper userMapper;
    private final ChatChannelMapper chatChannelMapper;
    private final EnterpriseMapper enterpriseMapper;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final AccessGuard guard;

    /** 可通过通用更新接口修改的状态值 */
    private static final Set<String> ALLOWED_PROJECT_STATUS = Set.of("ACTIVE", "ARCHIVED", "PLANNING");
    /** 可授予的项目角色（OWNER 只能由创建/转移产生，不允许通过成员接口授予） */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of("MANAGER", "MEMBER", "TESTER");

    // ============================================
    // 项目 CRUD
    // ============================================

    /** 获取项目列表（自动按企业数据隔离） */
    @Operation(summary = "获取项目列表")
    @GetMapping
    public R<Page<Project>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {

        String userId = guard.userId(authentication);

        Page<Project> pageParam = new Page<>(page, pageSize);

        // 可见性 = 项目成员（企业超管额外可见本企业全部项目），口径统一走 ProjectAccessService
        List<String> visibleIds = projectAccessService.visibleProjectIds(userId);
        if (visibleIds.isEmpty()) {
            return R.ok(pageParam);
        }

        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Project::getId, visibleIds);

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Project::getName, keyword)
                   .or().like(Project::getProjectKey, keyword));
        }
        wrapper.orderByDesc(Project::getUpdatedAt);

        Page<Project> result = projectMapper.selectPage(pageParam, wrapper);

        // 批量填充成员数（卡片展示用）：一条分组查询，避免 N+1
        List<Project> records = result.getRecords();
        if (!records.isEmpty()) {
            List<String> projectIds = records.stream().map(Project::getId).toList();
            List<Map<String, Object>> rows = projectMemberMapper.selectMaps(
                    new QueryWrapper<ProjectMember>()
                            .select("project_id", "COUNT(*) AS cnt")
                            .in("project_id", projectIds)
                            .groupBy("project_id"));
            Map<String, Integer> countMap = new HashMap<>();
            for (Map<String, Object> row : rows) {
                Object pid = row.get("project_id");
                Object cnt = row.get("cnt");
                if (pid != null && cnt != null) {
                    countMap.put(pid.toString(), Integer.valueOf(cnt.toString()));
                }
            }
            records.forEach(p -> p.setMemberCount(countMap.getOrDefault(p.getId(), 0)));
        }
        // 列表不返回 Webhook 密钥
        records.forEach(p -> p.setGitWebhookSecret(null));

        return R.ok(result);
    }

    /** 获取项目详情（Webhook 密钥仅对有项目编辑权的成员可见） */
    @Operation(summary = "获取项目详情")
    @GetMapping("/{id}")
    public R<Project> getById(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        Project project = projectMapper.selectById(id);
        if (project == null || !projectAccessService.canView(userId, id)) {
            // 与"不存在"同响应，不泄露项目存在性
            return R.ok(null);
        }
        // 用权限点集合判定（不触发 permission.denied 审计，避免普通成员每次看项目都刷审计表）
        if (!permissionService.projectPermissions(userId, id).contains(PermissionCodes.PROJECT_EDIT)) {
            project.setGitWebhookSecret(null);
        }
        return R.ok(project);
    }

    /** 创建项目：企业归属一律取当前用户所属企业，忽略请求体中的 enterpriseId */
    @Operation(summary = "创建项目")
    @PostMapping
    @Transactional
    public R<Project> create(@Valid @RequestBody CreateProjectRequest request, Authentication authentication) {
        String userId = guard.userId(authentication);
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getEnterpriseId())) {
            throw new BusinessException(400, "请先创建或加入企业，再创建项目");
        }

        Project project = new Project();
        project.setName(request.getName());
        project.setProjectKey(request.getProjectKey());
        project.setDescription(request.getDescription());
        project.setIcon(request.getIcon());
        project.setGitRepoUrl(request.getGitRepoUrl());
        // Webhook 密钥必须在创建时生成：详情接口只对"有 project:edit 的成员"返回它，
        // 而它又是拼 webhook 地址的唯一来源。之前只有迁移脚本给存量项目补过，
        // 新建项目的该列一直是 NULL —— 前端拿到的地址是 secret=null，
        // 平台侧配好 webhook 也永远 401，Git 集成对新项目完全不可用。
        project.setGitWebhookSecret(randomWebhookSecret());
        project.setOwnerId(userId);
        project.setStatus("ACTIVE");
        project.setDeleted(0);
        project.setEnterpriseId(user.getEnterpriseId());
        projectMapper.insert(project);

        // 自动将创建者添加为项目 OWNER
        ProjectMember ownerMember = new ProjectMember();
        ownerMember.setProjectId(project.getId());
        ownerMember.setUserId(userId);
        ownerMember.setRole("OWNER");
        projectMemberMapper.insert(ownerMember);

        // 自动创建项目群聊频道
        ChatChannel projectChannel = new ChatChannel();
        projectChannel.setName(project.getName() + " - 项目群");
        projectChannel.setType("PROJECT");
        projectChannel.setProjectId(project.getId());
        chatChannelMapper.insert(projectChannel);

        auditService.log(userId, project.getEnterpriseId(), project.getId(),
                "project.create", "project", project.getId(), "创建项目: " + project.getName());
        return R.ok(project);
    }

    /** 更新项目：仅 project:edit，字段白名单（企业/负责人/邀请码/密钥/删除标记不可改写） */
    @Operation(summary = "更新项目")
    @PutMapping("/{id}")
    public R<Project> update(@PathVariable String id,
                             @Valid @RequestBody UpdateProjectRequest request,
                             Authentication authentication) {
        String userId = guard.userId(authentication);
        Project existing = guard.requireProjectEdit(userId, id);

        Project patch = new Project();
        patch.setId(id);
        patch.setName(request.getName());
        patch.setProjectKey(request.getProjectKey());
        patch.setDescription(request.getDescription());
        patch.setIcon(request.getIcon());
        patch.setGitRepoUrl(request.getGitRepoUrl());
        if (request.getStatus() != null) {
            if (!ALLOWED_PROJECT_STATUS.contains(request.getStatus())) {
                throw new BusinessException(400, "无效的项目状态");
            }
            patch.setStatus(request.getStatus());
        }
        projectMapper.updateById(patch);

        auditService.log(userId, existing.getEnterpriseId(), id,
                "project.update", "project", id, "更新项目信息: " + existing.getName());
        return R.ok(projectMapper.selectById(id));
    }

    /** 删除项目（逻辑删除）：仅 project:manage（项目所有者） */
    @Operation(summary = "删除项目（逻辑删除）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        Project project = guard.requireProjectManage(userId, id);
        projectMapper.deleteById(id);
        auditService.log(userId, project.getEnterpriseId(), id,
                "project.delete", "project", id, "删除项目: " + project.getName());
        return R.ok();
    }

    // ============================================
    // 项目成员管理
    // ============================================

    /** 获取项目成员列表（含用户信息） */
    @Operation(summary = "获取项目成员列表")
    @GetMapping("/{id}/members")
    public R<List<ProjectMember>> getMembers(@PathVariable String id, Authentication authentication) {
        guard.requireView(guard.userId(authentication), id);
        return R.ok(projectMemberMapper.selectMembersWithUser(id));
    }

    /** 添加项目成员（仅 OWNER/MANAGER；目标用户须同企业，角色须白名单） */
    @Operation(summary = "添加项目成员（仅 OWNER/MANAGER）")
    @PostMapping("/{id}/members")
    public R<ProjectMember> addMember(@PathVariable String id,
                                       @RequestBody Map<String, String> body,
                                       Authentication authentication) {
        String operatorId = guard.userId(authentication);
        Project project = guard.requireMemberManage(operatorId, id);

        String userId = body.get("userId");
        if (!StringUtils.hasText(userId)) {
            throw new BusinessException(400, "请指定要添加的成员");
        }
        String role = body.getOrDefault("role", "MEMBER");
        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new BusinessException(400, "无效的项目角色");
        }
        guard.requireSameEnterpriseUser(project, userId);

        // 检查是否已存在
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, id)
               .eq(ProjectMember::getUserId, userId);
        if (projectMemberMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(409, "该用户已是项目成员");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(id);
        member.setUserId(userId);
        member.setRole(role);
        projectMemberMapper.insert(member);
        User addedUser = userMapper.selectById(userId);
        auditService.log(operatorId, project.getEnterpriseId(), id, "project.member.add",
                "user", userId, "添加项目成员: " + (addedUser != null ? addedUser.displayName() : userId) + "，角色: " + role);
        return R.ok(member);
    }

    /** 更新成员角色（仅 OWNER/MANAGER，不可授予 OWNER；memberId 必须属于该项目） */
    @Operation(summary = "更新成员角色（仅 OWNER/MANAGER）")
    @PutMapping("/{id}/members/{memberId}")
    public R<Void> updateMemberRole(@PathVariable String id,
                                     @PathVariable String memberId,
                                     @RequestBody Map<String, String> body,
                                     Authentication authentication) {
        String operatorId = guard.userId(authentication);
        Project project = guard.requireMemberManage(operatorId, id);
        ProjectMember member = guard.requireMemberOfProject(id, memberId);

        String role = body.get("role");
        if (!ASSIGNABLE_ROLES.contains(role)) {
            throw new BusinessException(400, "无效的项目角色");
        }
        // 不允许把项目所有者降级（避免项目失去 OWNER）
        if ("OWNER".equals(member.getRole())) {
            throw new BusinessException(403, "不能修改项目负责人的角色");
        }

        ProjectMember patch = new ProjectMember();
        patch.setId(memberId);
        patch.setRole(role);
        projectMemberMapper.updateById(patch);
        auditService.log(operatorId, project.getEnterpriseId(), id,
                "project.member.role", "project_member", memberId, "调整项目成员角色: " + role);
        return R.ok();
    }

    /** 移除项目成员（仅 OWNER/MANAGER；memberId 必须属于该项目，且不能移除 OWNER） */
    @Operation(summary = "移除项目成员（仅 OWNER/MANAGER）")
    @DeleteMapping("/{id}/members/{memberId}")
    public R<Void> removeMember(@PathVariable String id,
                                 @PathVariable String memberId,
                                 Authentication authentication) {
        String operatorId = guard.userId(authentication);
        Project project = guard.requireMemberManage(operatorId, id);
        ProjectMember member = guard.requireMemberOfProject(id, memberId);
        if ("OWNER".equals(member.getRole())) {
            throw new BusinessException(403, "不能移除项目负责人");
        }

        projectMemberMapper.deleteById(memberId);
        auditService.log(operatorId, project.getEnterpriseId(), id,
                "project.member.remove", "project_member", memberId, "移除项目成员");
        return R.ok();
    }

    // ============================================
    // 项目邀请链接（仅限同企业成员加入）
    // ============================================

    private static final String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom INVITE_RANDOM = new SecureRandom();

    /** Webhook 密钥：32 位小写十六进制，与列宽 varchar(32) 一致 */
    private static String randomWebhookSecret() {
        byte[] bytes = new byte[16];
        INVITE_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /**
     * 获取项目邀请码（首次调用时懒生成）
     * 仅本项目所属企业的成员可获取
     */
    @Operation(summary = "获取项目邀请码（同企业成员可用于分享链接）")
    @GetMapping("/{id}/invite-code")
    public R<String> getInviteCode(@PathVariable String id, Authentication authentication) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        User user = userMapper.selectById(guard.userId(authentication));
        requireSameEnterprise(project, user);

        if (!StringUtils.hasText(project.getInviteCode())) {
            // 条件更新：只有邀请码仍为空时才写入。
            // 原实现是"读到空 → 生成 → updateById"，两个并发请求会各生成一个码，
            // 后写覆盖前写，先返回给用户的那个邀请码随即失效。
            String candidate = generateUniqueInviteCode();
            projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                    .eq(Project::getId, id)
                    .and(w -> w.isNull(Project::getInviteCode).or().eq(Project::getInviteCode, ""))
                    .set(Project::getInviteCode, candidate));
            // 无论是否抢到，都以库里已有的为准（保证对外只存在一个有效码）
            project = projectMapper.selectById(id);
            if (!StringUtils.hasText(project.getInviteCode())) {
                throw new BusinessException(409, "邀请码生成冲突，请重试");
            }
        }
        return R.ok(project.getInviteCode());
    }

    /**
     * 凭邀请码加入项目
     * 非项目所属企业的成员拒绝加入；已是成员时幂等返回项目信息（便于直接进入项目）
     */
    @Operation(summary = "通过邀请码加入项目（仅限本企业成员）")
    @PostMapping("/join")
    public R<Project> joinByInviteCode(@RequestParam String inviteCode,
                                        Authentication authentication) {
        Project project = projectMapper.selectOne(
                new LambdaQueryWrapper<Project>().eq(Project::getInviteCode, inviteCode));
        if (project == null) {
            throw new BusinessException(404, "邀请链接无效或项目已删除");
        }

        User user = userMapper.selectById(guard.userId(authentication));
        if (user == null || !StringUtils.hasText(user.getEnterpriseId())) {
            throw new BusinessException(403, "请先加入企业，再通过链接加入项目");
        }
        if (!user.getEnterpriseId().equals(project.getEnterpriseId())) {
            Enterprise enterprise = enterpriseMapper.selectById(project.getEnterpriseId());
            String enterpriseName = (enterprise != null) ? enterprise.getName() : "目标";
            throw new BusinessException(403, "该项目仅限「" + enterpriseName + "」的成员加入");
        }

        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, project.getId())
               .eq(ProjectMember::getUserId, user.getId());
        if (projectMemberMapper.selectCount(wrapper) == 0) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(project.getId());
            member.setUserId(user.getId());
            member.setRole("MEMBER");
            projectMemberMapper.insert(member);
        }
        return R.ok(project);
    }

    /** 校验操作者与项目同企业，不同企业直接拒绝 */
    private void requireSameEnterprise(Project project, User user) {
        if (user == null || !StringUtils.hasText(user.getEnterpriseId())
                || !user.getEnterpriseId().equals(project.getEnterpriseId())) {
            throw new BusinessException(403, "仅限项目所属企业成员操作");
        }
    }

    /** 生成全局唯一的 8 位邀请码（去除易混淆字符） */
    private String generateUniqueInviteCode() {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder(8);
            for (int j = 0; j < 8; j++) {
                sb.append(INVITE_CHARS.charAt(INVITE_RANDOM.nextInt(INVITE_CHARS.length())));
            }
            String code = sb.toString();
            wrapper.clear();
            wrapper.eq(Project::getInviteCode, code);
            if (projectMapper.selectCount(wrapper) == 0) {
                return code;
            }
        }
        throw new BusinessException(500, "邀请码生成失败，请重试");
    }
}
