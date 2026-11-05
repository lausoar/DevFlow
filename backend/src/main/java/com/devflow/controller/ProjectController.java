package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
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
import com.devflow.service.AuditService;
import com.devflow.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;
import java.util.Set;

/**
 * 项目控制器
 * 已添加企业数据隔离：list 接口自动按当前用户所属企业过滤
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

        String userId = (String) authentication.getPrincipal();

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

        return R.ok(result);
    }

    /** 获取项目详情 */
    @Operation(summary = "获取项目详情")
    @GetMapping("/{id}")
    public R<Project> getById(@PathVariable String id, Authentication authentication) {
        Project project = projectMapper.selectById(id);
        if (project != null && !projectAccessService.canView((String) authentication.getPrincipal(), id)) {
            // 与"不存在"同响应，不泄露项目存在性
            project = null;
        }
        return R.ok(project);
    }

    /** 创建项目（自动设置企业 ID 实现数据隔离） */
    @Operation(summary = "创建项目")
    @PostMapping
    public R<Project> create(@RequestBody Project project, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        project.setOwnerId(userId);
        project.setStatus("ACTIVE");
        project.setDeleted(0);

        // 企业数据隔离：自动设置创建者所属企业
        if (!StringUtils.hasText(project.getEnterpriseId())) {
            User user = userMapper.selectById(userId);
            if (user != null && StringUtils.hasText(user.getEnterpriseId())) {
                project.setEnterpriseId(user.getEnterpriseId());
            }
        }
        // enterprise_id 非空约束：无企业时给出业务提示，避免落库报 500
        if (!StringUtils.hasText(project.getEnterpriseId())) {
            throw new BusinessException(400, "请先创建或加入企业，再创建项目");
        }

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

        return R.ok(project);
    }

    /** 更新项目 */
    @Operation(summary = "更新项目")
    @PutMapping("/{id}")
    public R<Project> update(@PathVariable String id, @RequestBody Project project) {
        project.setId(id);
        projectMapper.updateById(project);
        return R.ok(project);
    }

    /** 删除项目 */
    @Operation(summary = "删除项目（逻辑删除）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        projectMapper.deleteById(id);
        return R.ok();
    }

    // ============================================
    // 项目成员管理
    // ============================================

    /** 获取项目成员列表（含用户信息） */
    @Operation(summary = "获取项目成员列表")
    @GetMapping("/{id}/members")
    public R<List<ProjectMember>> getMembers(@PathVariable String id, Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), id)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        return R.ok(projectMemberMapper.selectMembersWithUser(id));
    }

    /** 添加项目成员（仅项目负责人/管理员） */
    @Operation(summary = "添加项目成员（仅 OWNER/MANAGER）")
    @PostMapping("/{id}/members")
    public R<ProjectMember> addMember(@PathVariable String id,
                                       @RequestBody Map<String, String> body,
                                       Authentication authentication) {
        String userId = body.get("userId");
        String role = body.getOrDefault("role", "MEMBER");

        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        // 成员管理仅限 OWNER/MANAGER
        requireProjectManager(id, (String) authentication.getPrincipal());

        // 检查是否已存在
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, id)
               .eq(ProjectMember::getUserId, userId);
        if (projectMemberMapper.selectCount(wrapper) > 0) {
            return R.fail("该用户已是项目成员");
        }

        ProjectMember member = new ProjectMember();
        member.setProjectId(id);
        member.setUserId(userId);
        member.setRole(role);
        projectMemberMapper.insert(member);
        User addedUser = userMapper.selectById(userId);
        auditService.log((String) authentication.getPrincipal(), project.getEnterpriseId(), id, "project.member.add",
                "user", userId, "添加项目成员: " + (addedUser != null ? addedUser.displayName() : userId) + "，角色: " + role);
        return R.ok(member);
    }

    /** 更新成员角色（仅项目负责人/管理员，不可授予 OWNER） */
    @Operation(summary = "更新成员角色（仅 OWNER/MANAGER）")
    @PutMapping("/{id}/members/{memberId}")
    public R<Void> updateMemberRole(@PathVariable String id,
                                     @PathVariable String memberId,
                                     @RequestBody Map<String, String> body,
                                     Authentication authentication) {
        requireProjectManager(id, (String) authentication.getPrincipal());
        String role = body.get("role");
        if (!Set.of("MANAGER", "MEMBER", "TESTER").contains(role)) {
            throw new BusinessException(400, "无效的项目角色");
        }
        ProjectMember member = new ProjectMember();
        member.setId(memberId);
        member.setRole(role);
        projectMemberMapper.updateById(member);
        Project proj = projectMapper.selectById(id);
        auditService.log((String) authentication.getPrincipal(), proj != null ? proj.getEnterpriseId() : null, id,
                "project.member.role", "project_member", memberId, "调整项目成员角色: " + role);
        return R.ok();
    }

    /** 移除项目成员（仅项目负责人/管理员） */
    @Operation(summary = "移除项目成员（仅 OWNER/MANAGER）")
    @DeleteMapping("/{id}/members/{memberId}")
    public R<Void> removeMember(@PathVariable String id,
                                 @PathVariable String memberId,
                                 Authentication authentication) {
        requireProjectManager(id, (String) authentication.getPrincipal());
        Project proj = projectMapper.selectById(id);
        projectMemberMapper.deleteById(memberId);
        auditService.log((String) authentication.getPrincipal(), proj != null ? proj.getEnterpriseId() : null, id,
                "project.member.remove", "project_member", memberId, "移除项目成员");
        return R.ok();
    }

    /** 校验操作者在项目上拥有成员管理权限点（映射 OWNER/MANAGER），否则拒绝 */
    private void requireProjectManager(String projectId, String userId) {
        if (!permissionService.hasProjectPermission(userId, projectId, PermissionCodes.MEMBER_MANAGE)) {
            throw new BusinessException(403, "仅项目负责人或项目管理员可操作成员");
        }
    }

    // ============================================
    // 项目邀请链接（仅限同企业成员加入）
    // ============================================

    private static final String INVITE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom INVITE_RANDOM = new SecureRandom();

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
        User user = userMapper.selectById((String) authentication.getPrincipal());
        requireSameEnterprise(project, user);

        if (!StringUtils.hasText(project.getInviteCode())) {
            project.setInviteCode(generateUniqueInviteCode());
            projectMapper.updateById(project);
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

        User user = userMapper.selectById((String) authentication.getPrincipal());
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
