package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.PermissionCodes;
import com.devflow.entity.Project;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.SysRolePermission;
import com.devflow.entity.User;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.SysRolePermissionMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AuditService;
import com.devflow.service.PermissionService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一权限判定实现
 * 启动时把 sys_role_permission 全量加载进内存（行数很小），判定零额外查表；
 * 修改映射后调用 reload() 生效。
 *
 * 多租户：映射按 enterprise_id 分组缓存；某个企业没有自己的行时回落到全局模板（'*'）。
 * 这样某一企业超管调整矩阵只影响本企业，不会再"修改全平台权限"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    /** 全局默认模板的 enterprise_id */
    public static final String GLOBAL_TEMPLATE = "*";

    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final AuditService auditService;

    /**
     * 企业 ID → ("SCOPE:role" → 权限点集合)
     * 键为 {@link #GLOBAL_TEMPLATE} 时是全局默认模板。
     */
    private volatile Map<String, Map<String, Set<String>>> enterprisePermissions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reload();
    }

    @Override
    public void reload() {
        List<SysRolePermission> all = sysRolePermissionMapper.selectList(null);
        Map<String, Map<String, Set<String>>> byEnterprise = new ConcurrentHashMap<>();
        for (SysRolePermission rp : all) {
            String eid = StringUtils.hasText(rp.getEnterpriseId()) ? rp.getEnterpriseId() : GLOBAL_TEMPLATE;
            String key = rp.getScope() + ":" + rp.getRole();
            byEnterprise
                    .computeIfAbsent(eid, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet())
                    .add(rp.getPermissionCode());
        }
        this.enterprisePermissions = byEnterprise;
        log.info("权限映射已加载：{} 个企业的映射（含全局模板 {} 条）",
                byEnterprise.size(),
                byEnterprise.getOrDefault(GLOBAL_TEMPLATE, Map.of()).size());
    }

    @Override
    public boolean hasEnterprisePermission(String userId, String permissionCode) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getRole())) {
            return false;
        }
        boolean allowed = codesFor(user.getEnterpriseId(), PermissionCodes.SCOPE_ENTERPRISE, user.getRole())
                .contains(permissionCode);
        if (!allowed) {
            auditService.log(userId, user.getEnterpriseId(), null, "permission.denied",
                    "permission", permissionCode, "企业权限判定未通过: " + permissionCode);
        }
        return allowed;
    }

    @Override
    public boolean hasProjectPermission(String userId, String projectId, String permissionCode) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(projectId)) {
            return false;
        }
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, projectId)
               .eq(ProjectMember::getUserId, userId);
        ProjectMember member = projectMemberMapper.selectOne(wrapper);
        if (member == null || !StringUtils.hasText(member.getRole())) {
            return false;
        }
        Project project = projectMapper.selectById(projectId);
        String enterpriseId = project != null ? project.getEnterpriseId() : null;
        boolean allowed = codesFor(enterpriseId, PermissionCodes.SCOPE_PROJECT, member.getRole())
                .contains(permissionCode);
        if (!allowed) {
            auditService.log(userId, enterpriseId, projectId, "permission.denied",
                    "permission", permissionCode, "项目权限判定未通过: " + permissionCode);
        }
        return allowed;
    }

    @Override
    public java.util.Set<String> enterprisePermissions(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Set.of();
        }
        User user = userMapper.selectById(userId);
        if (user == null || !StringUtils.hasText(user.getRole())) {
            return Set.of();
        }
        return Set.copyOf(codesFor(user.getEnterpriseId(),
                PermissionCodes.SCOPE_ENTERPRISE, user.getRole()));
    }

    @Override
    public java.util.Set<String> projectPermissions(String userId, String projectId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(projectId)) {
            return Set.of();
        }
        LambdaQueryWrapper<ProjectMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectMember::getProjectId, projectId)
               .eq(ProjectMember::getUserId, userId);
        ProjectMember member = projectMemberMapper.selectOne(wrapper);
        if (member == null || !StringUtils.hasText(member.getRole())) {
            return Set.of();
        }
        Project project = projectMapper.selectById(projectId);
        return Set.copyOf(codesFor(project != null ? project.getEnterpriseId() : null,
                PermissionCodes.SCOPE_PROJECT, member.getRole()));
    }

    /**
     * 取某企业在某作用域/角色下的权限点集合。
     * 企业一旦有自己的映射快照（哪怕某角色被清空），就完全以快照为准，
     * 不能回落到全局模板——否则"收回某角色全部权限"会被模板静默还原。
     */
    private Set<String> codesFor(String enterpriseId, String scope, String role) {
        Map<String, Map<String, Set<String>>> snapshot = this.enterprisePermissions;
        String key = scope + ":" + role;
        if (StringUtils.hasText(enterpriseId)) {
            Map<String, Set<String>> own = snapshot.get(enterpriseId);
            if (own != null) {
                return own.getOrDefault(key, Set.of());
            }
        }
        Map<String, Set<String>> global = snapshot.get(GLOBAL_TEMPLATE);
        if (global == null) {
            return Set.of();
        }
        return global.getOrDefault(key, Set.of());
    }
}
