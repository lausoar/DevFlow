package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.exception.BusinessException;
import com.devflow.dto.request.CreateRequirementRequest;
import com.devflow.dto.request.UpdateRequirementRequest;
import com.devflow.dto.response.RequirementResponse;
import com.devflow.dto.response.RequirementStatsResponse;
import com.devflow.entity.*;
import com.devflow.mapper.*;
import com.devflow.service.RequirementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 需求服务实现
 */
@Service
@RequiredArgsConstructor
public class RequirementServiceImpl implements RequirementService {

    private final RequirementMapper requirementMapper;
    private final RequirementApprovalMapper approvalMapper;
    private final RequirementTaskMapper requirementTaskMapper;
    private final com.devflow.mapper.RequirementChangeLogMapper changeLogMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public Requirement createRequirement(String projectId, CreateRequirementRequest request, String requesterId) {
        // 验证项目存在
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }

        Requirement requirement = new Requirement();
        requirement.setTitle(request.getTitle());
        requirement.setDescription(request.getDescription());
        requirement.setProjectId(projectId);
        requirement.setRequesterId(requesterId);
        // 审批功能已移除：需求创建即生效
        requirement.setStatus("OPEN");
        requirement.setPriority(request.getPriority() != null ? request.getPriority() : "MEDIUM");
        requirement.setSource(request.getSource());
        requirement.setCategory(request.getCategory());
        requirement.setEstimatedHours(request.getEstimatedHours());
        requirement.setParentId(request.getParentId());
        requirement.setDeleted(0);

        requirementMapper.insert(requirement);
        return requirement;
    }

    @Override
    public RequirementResponse getRequirementDetail(String requirementId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException(404, "需求不存在");
        }

        return buildRequirementResponse(requirement);
    }

    @Override
    public Page<RequirementResponse> listRequirements(String projectId, String status, String priority,
                                                        String keyword, int page, int size) {
        // 构建查询条件
        LambdaQueryWrapper<Requirement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Requirement::getProjectId, projectId);

        if (status != null && !status.isEmpty()) {
            wrapper.eq(Requirement::getStatus, status);
        }
        if (priority != null && !priority.isEmpty()) {
            wrapper.eq(Requirement::getPriority, priority);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Requirement::getTitle, keyword)
                    .or().like(Requirement::getDescription, keyword));
        }

        wrapper.orderByDesc(Requirement::getCreatedAt);

        // 分页查询
        Page<Requirement> pageParam = new Page<>(page, size);
        Page<Requirement> resultPage = requirementMapper.selectPage(pageParam, wrapper);

        // 转换为响应对象
        Page<RequirementResponse> responsePage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<RequirementResponse> records = resultPage.getRecords().stream()
                .map(this::buildRequirementResponse)
                .collect(Collectors.toList());
        responsePage.setRecords(records);

        return responsePage;
    }

    @Override
    @Transactional
    public Requirement updateRequirement(String requirementId, UpdateRequirementRequest request, String userId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException(404, "需求不存在");
        }

        // 权限口径与 Bug 模块一致：创建人、企业 SUPER_ADMIN/ADMIN、项目 OWNER/MANAGER 可修改
        if (!canModify(requirement, userId)) {
            throw new BusinessException(403, "仅需求创建人、企业管理员或项目负责人可修改");
        }

        // 审批功能已移除，需求创建即生效，可随时编辑
        // 状态流转白名单
        if (request.getStatus() != null
                && !List.of("OPEN", "COMPLETED", "CLOSED").contains(request.getStatus())) {
            throw new BusinessException(400, "非法的需求状态");
        }

        // 记录字段级变更（值相等不记；文本截断 500 字符防日志膨胀）
        List<RequirementChangeLog> changes = new ArrayList<>();
        if (request.getTitle() != null) {
            addChange(changes, requirement, "title", requirement.getTitle(), request.getTitle(), userId);
            requirement.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            addChange(changes, requirement, "description", requirement.getDescription(), request.getDescription(), userId);
            requirement.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            addChange(changes, requirement, "priority", requirement.getPriority(), request.getPriority(), userId);
            requirement.setPriority(request.getPriority());
        }
        if (request.getSource() != null) {
            addChange(changes, requirement, "source", requirement.getSource(), request.getSource(), userId);
            requirement.setSource(request.getSource());
        }
        if (request.getCategory() != null) {
            addChange(changes, requirement, "category", requirement.getCategory(), request.getCategory(), userId);
            requirement.setCategory(request.getCategory());
        }
        if (request.getEstimatedHours() != null) {
            addChange(changes, requirement, "estimatedHours",
                    requirement.getEstimatedHours() == null ? null : String.valueOf(requirement.getEstimatedHours()),
                    String.valueOf(request.getEstimatedHours()), userId);
            requirement.setEstimatedHours(request.getEstimatedHours());
        }
        if (request.getStatus() != null) {
            addChange(changes, requirement, "status", requirement.getStatus(), request.getStatus(), userId);
            requirement.setStatus(request.getStatus());
        }

        requirementMapper.updateById(requirement);
        for (RequirementChangeLog change : changes) {
            try {
                changeLogMapper.insert(change);
            } catch (Exception ignored) {
                // 变更记录失败不影响主流程
            }
        }
        return requirement;
    }

    /** 值有变化才记一条变更（文本截断 500 字符） */
    private void addChange(List<RequirementChangeLog> changes, Requirement requirement,
                           String field, String oldValue, String newValue, String userId) {
        if (oldValue == null && newValue == null) return;
        if (oldValue != null && oldValue.equals(newValue)) return;
        RequirementChangeLog change = new RequirementChangeLog();
        change.setRequirementId(requirement.getId());
        change.setProjectId(requirement.getProjectId());
        change.setField(field);
        change.setOldValue(truncate(oldValue));
        change.setNewValue(truncate(newValue));
        change.setEditorId(userId);
        changes.add(change);
    }

    private String truncate(String value) {
        if (value == null) return null;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    @Override
    @Transactional
    public void deleteRequirement(String requirementId, String userId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException(404, "需求不存在");
        }

        // 权限口径与修改一致：创建人、企业 SUPER_ADMIN/ADMIN、项目 OWNER/MANAGER 可删除
        if (!canModify(requirement, userId)) {
            throw new BusinessException(403, "仅需求创建人、企业管理员或项目负责人可删除");
        }

        requirementMapper.deleteById(requirementId);

        // 删除关联的审批记录
        approvalMapper.delete(new LambdaQueryWrapper<RequirementApproval>()
                .eq(RequirementApproval::getRequirementId, requirementId));

        // 删除关联的任务关系
        requirementTaskMapper.delete(new LambdaQueryWrapper<RequirementTask>()
                .eq(RequirementTask::getRequirementId, requirementId));
    }

    @Override
    @Transactional
    public void linkTask(String requirementId, String taskId) {
        Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException(404, "需求不存在");
        }

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }

        // 检查是否已关联
        Long count = requirementTaskMapper.selectCount(
                new LambdaQueryWrapper<RequirementTask>()
                        .eq(RequirementTask::getRequirementId, requirementId)
                        .eq(RequirementTask::getTaskId, taskId)
        );

        if (count > 0) {
            throw new BusinessException(400, "任务已关联到此需求");
        }

        RequirementTask requirementTask = new RequirementTask();
        requirementTask.setRequirementId(requirementId);
        requirementTask.setTaskId(taskId);
        requirementTaskMapper.insert(requirementTask);
    }

    @Override
    @Transactional
    public void unlinkTask(String requirementId, String taskId) {
        requirementTaskMapper.delete(
                new LambdaQueryWrapper<RequirementTask>()
                        .eq(RequirementTask::getRequirementId, requirementId)
                        .eq(RequirementTask::getTaskId, taskId)
        );
    }

    @Override
    public RequirementStatsResponse getStats(String projectId) {
        List<Requirement> requirements = requirementMapper.selectList(
                new LambdaQueryWrapper<Requirement>()
                        .eq(Requirement::getProjectId, projectId)
        );

        int total = requirements.size();
        int open = (int) requirements.stream().filter(r -> "OPEN".equals(r.getStatus())).count();
        int completed = (int) requirements.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
        int closed = (int) requirements.stream().filter(r -> "CLOSED".equals(r.getStatus())).count();

        // 按优先级分布
        Map<String, Integer> priorityDist = new LinkedHashMap<>();
        priorityDist.put("CRITICAL", (int) requirements.stream().filter(r -> "CRITICAL".equals(r.getPriority())).count());
        priorityDist.put("HIGH", (int) requirements.stream().filter(r -> "HIGH".equals(r.getPriority())).count());
        priorityDist.put("MEDIUM", (int) requirements.stream().filter(r -> "MEDIUM".equals(r.getPriority())).count());
        priorityDist.put("LOW", (int) requirements.stream().filter(r -> "LOW".equals(r.getPriority())).count());

        // 按来源分布
        Map<String, Integer> sourceDist = new LinkedHashMap<>();
        sourceDist.put("CUSTOMER", (int) requirements.stream().filter(r -> "CUSTOMER".equals(r.getSource())).count());
        sourceDist.put("INTERNAL", (int) requirements.stream().filter(r -> "INTERNAL".equals(r.getSource())).count());
        sourceDist.put("MARKET", (int) requirements.stream().filter(r -> "MARKET".equals(r.getSource())).count());
        sourceDist.put("TECHNICAL", (int) requirements.stream().filter(r -> "TECHNICAL".equals(r.getSource())).count());

        // 按分类分布
        Map<String, Integer> categoryDist = new LinkedHashMap<>();
        categoryDist.put("FEATURE", (int) requirements.stream().filter(r -> "FEATURE".equals(r.getCategory())).count());
        categoryDist.put("IMPROVEMENT", (int) requirements.stream().filter(r -> "IMPROVEMENT".equals(r.getCategory())).count());
        categoryDist.put("BUGFIX", (int) requirements.stream().filter(r -> "BUGFIX".equals(r.getCategory())).count());
        categoryDist.put("OTHER", (int) requirements.stream().filter(r -> "OTHER".equals(r.getCategory())).count());

        return RequirementStatsResponse.builder()
                .total(total)
                .open(open)
                .completed(completed)
                .closed(closed)
                .priorityDistribution(priorityDist)
                .sourceDistribution(sourceDist)
                .categoryDistribution(categoryDist)
                .build();
    }

    /**
     * 修改/删除权限：创建人、企业 SUPER_ADMIN/ADMIN、项目 OWNER/MANAGER
     */
    private boolean canModify(Requirement requirement, String userId) {
        if (requirement.getRequesterId().equals(userId)) {
            return true;
        }
        User user = userMapper.selectById(userId);
        if (user != null && ("SUPER_ADMIN".equals(user.getRole()) || "ADMIN".equals(user.getRole()))) {
            return true;
        }
        Long count = projectMemberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMember>()
                        .eq(ProjectMember::getProjectId, requirement.getProjectId())
                        .eq(ProjectMember::getUserId, userId)
                        .in(ProjectMember::getRole, "OWNER", "MANAGER")
        );
        return count != null && count > 0;
    }

    /**
     * 构建需求响应对象
     */
    private RequirementResponse buildRequirementResponse(Requirement requirement) {
        // 获取项目信息
        Project project = projectMapper.selectById(requirement.getProjectId());

        // 获取提出人信息
        User requester = userMapper.selectById(requirement.getRequesterId());

        // 获取审批人信息
        User approver = null;
        if (requirement.getApproverId() != null) {
            approver = userMapper.selectById(requirement.getApproverId());
        }

        // 获取关联的任务
        List<RequirementTask> requirementTasks = requirementTaskMapper.selectList(
                new LambdaQueryWrapper<RequirementTask>()
                        .eq(RequirementTask::getRequirementId, requirement.getId())
        );

        List<RequirementResponse.LinkedTask> linkedTasks = new ArrayList<>();
        for (RequirementTask rt : requirementTasks) {
            Task task = taskMapper.selectById(rt.getTaskId());
            if (task != null) {
                User assignee = null;
                if (task.getAssigneeId() != null) {
                    assignee = userMapper.selectById(task.getAssigneeId());
                }
                linkedTasks.add(RequirementResponse.LinkedTask.builder()
                        .taskId(task.getId())
                        .taskTitle(task.getTitle())
                        .taskStatus(task.getStatus())
                        .assigneeName(assignee != null ? assignee.displayName() : null)
                        .build());
            }
        }

        // 获取审批记录
        return RequirementResponse.builder()
                .id(requirement.getId())
                .title(requirement.getTitle())
                .description(requirement.getDescription())
                .projectId(requirement.getProjectId())
                .projectName(project != null ? project.getName() : null)
                .requesterId(requirement.getRequesterId())
                .requesterName(requester != null ? requester.displayName() : null)
                .requesterAvatar(requester != null ? requester.getAvatar() : null)
                .approverId(requirement.getApproverId())
                .approverName(approver != null ? approver.displayName() : null)
                .status(requirement.getStatus())
                .priority(requirement.getPriority())
                .source(requirement.getSource())
                .category(requirement.getCategory())
                .estimatedHours(requirement.getEstimatedHours())
                .approvedAt(requirement.getApprovedAt())
                .rejectedAt(requirement.getRejectedAt())
                .rejectReason(requirement.getRejectReason())
                .parentId(requirement.getParentId())
                .sort(requirement.getSort())
                .createdAt(requirement.getCreatedAt())
                .updatedAt(requirement.getUpdatedAt())
                .linkedTasks(linkedTasks)
                .build();
    }
}
