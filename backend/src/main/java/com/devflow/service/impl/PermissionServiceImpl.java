package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.PermissionCodes;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.SysRolePermission;
import com.devflow.entity.User;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final AuditService auditService;

    /** key = "SCOPE:role"，value = 该角色拥有的权限点集合 */
    private volatile Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        reload();
    }

    @Override
    public void reload() {
        List<SysRolePermission> all = sysRolePermissionMapper.selectList(null);
        Map<String, Set<String>> map = new ConcurrentHashMap<>();
        for (SysRolePermission rp : all) {
            String key = rp.getScope() + ":" + rp.getRole();
            map.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(rp.getPermissionCode());
        }
        this.rolePermissions = map;
        log.info("权限映射已加载：{} 个角色条目", map.size());
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
        Set<String> codes = rolePermissions.get(PermissionCodes.SCOPE_ENTERPRISE + ":" + user.getRole());
        boolean allowed = codes != null && codes.contains(permissionCode);
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
        Set<String> codes = rolePermissions.get(PermissionCodes.SCOPE_PROJECT + ":" + member.getRole());
        boolean allowed = codes != null && codes.contains(permissionCode);
        if (!allowed) {
            auditService.log(userId, null, projectId, "permission.denied",
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
        Set<String> codes = rolePermissions.get(PermissionCodes.SCOPE_ENTERPRISE + ":" + user.getRole());
        return codes != null ? Set.copyOf(codes) : Set.of();
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
        Set<String> codes = rolePermissions.get(PermissionCodes.SCOPE_PROJECT + ":" + member.getRole());
        return codes != null ? Set.copyOf(codes) : Set.of();
    }
}
