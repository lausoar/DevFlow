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
import java.util.List;

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
        List<String> ids = new ArrayList<>(projectMemberMapper.selectList(
                        new LambdaQueryWrapper<ProjectMember>().eq(ProjectMember::getUserId, userId))
                .stream().map(ProjectMember::getProjectId).distinct().toList());

        // 企业超管：追加本企业全部项目（管理视野）
        User user = userMapper.selectById(userId);
        if (user != null && EnterpriseRoles.SUPER_ADMIN.equals(user.getRole())
                && StringUtils.hasText(user.getEnterpriseId())) {
            projectMapper.selectList(new LambdaQueryWrapper<Project>()
                            .eq(Project::getEnterpriseId, user.getEnterpriseId()))
                    .stream().map(Project::getId)
                    .filter(id -> !ids.contains(id))
                    .forEach(ids::add);
        }
        return ids;
    }

    @Override
    public boolean canView(String userId, String projectId) {
        return StringUtils.hasText(projectId) && visibleProjectIds(userId).contains(projectId);
    }
}
