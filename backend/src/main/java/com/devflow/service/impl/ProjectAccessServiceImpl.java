package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.EnterpriseRoles;
import com.devflow.entity.Project;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.User;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.ProjectAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProjectAccessServiceImpl implements ProjectAccessService {

    private final ProjectMemberMapper projectMemberMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;

    @Override
    public List<String> visibleProjectIds(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        List<String> memberProjectIds = projectMemberMapper.selectList(
                        new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId))
                .stream().map(ProjectMember::getProjectId).distinct().toList();

        List<String> ids = new ArrayList<>();
        if (!memberProjectIds.isEmpty()) {
            // 只保留未删除的项目：project_member 行在项目逻辑删除后仍然存在，
            // 不过滤会让"已删除项目"的成员继续读写该项目下全部子资源（任务/缺陷/文档/频道）
            List<Project> alive = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                    .in(Project::getId, memberProjectIds));
            ids.addAll(alive.stream().map(Project::getId).distinct().toList());
        }

        // 企业超管：追加本企业全部项目（管理视野；selectList 已自动跳过逻辑删除行）
        User user = userMapper.selectById(userId);
        if (user != null && EnterpriseRoles.SUPER_ADMIN.equals(user.getRole())
                && StringUtils.hasText(user.getEnterpriseId())) {
            Set<String> existing = new HashSet<>(ids);
            projectMapper.selectList(new LambdaQueryWrapper<Project>()
                            .eq(Project::getEnterpriseId, user.getEnterpriseId()))
                    .stream().map(Project::getId)
                    .filter(id -> !existing.contains(id))
                    .forEach(ids::add);
        }
        return ids;
    }

    @Override
    public boolean canView(String userId, String projectId) {
        return StringUtils.hasText(projectId) && visibleProjectIds(userId).contains(projectId);
    }
}
