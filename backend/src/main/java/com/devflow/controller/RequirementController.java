package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.service.ProjectAccessService;
import com.devflow.dto.request.CreateRequirementRequest;
import com.devflow.dto.request.UpdateRequirementRequest;
import com.devflow.dto.response.RequirementResponse;
import com.devflow.dto.response.RequirementStatsResponse;
import com.devflow.entity.Requirement;
import com.devflow.entity.RequirementChangeLog;
import com.devflow.entity.User;
import com.devflow.mapper.RequirementChangeLogMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.RequirementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 需求管理控制器
 */
@Tag(name = "需求管理", description = "需求的增删改查、审批流、关联任务")
@RestController
@RequestMapping("/projects/{projectId}/requirements")
@RequiredArgsConstructor
public class RequirementController {

    private final RequirementService requirementService;
    private final ProjectAccessService projectAccessService;
    private final RequirementMapper requirementMapper;
    private final RequirementChangeLogMapper changeLogMapper;
    private final UserMapper userMapper;

    /**
     * 获取需求列表（分页）
     */
    @Operation(summary = "获取需求列表")
    @GetMapping
    public R<Page<RequirementResponse>> list(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        Page<RequirementResponse> result = requirementService.listRequirements(
                projectId, status, priority, keyword, page, pageSize);
        return R.ok(result);
    }

    /**
     * 获取需求详情
     */
    @Operation(summary = "获取需求详情")
    @GetMapping("/{id}")
    public R<RequirementResponse> getById(@PathVariable String id, Authentication authentication) {
        RequirementResponse result = requirementService.getRequirementDetail(id);
        if (result != null && !projectAccessService.canView((String) authentication.getPrincipal(), result.getProjectId())) {
            result = null;
        }
        return R.ok(result);
    }

    /**
     * 需求变更记录（字段级，含修改人展示名）
     */
    @Operation(summary = "获取需求变更记录")
    @GetMapping("/{id}/changes")
    public R<List<RequirementChangeLog>> changes(@PathVariable String projectId,
                                                 @PathVariable String id,
                                                 Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        Requirement requirement = requirementMapper.selectById(id);
        if (requirement == null || !projectId.equals(requirement.getProjectId())) {
            throw new BusinessException(404, "需求不存在");
        }
        List<RequirementChangeLog> logs = changeLogMapper.selectList(
                new LambdaQueryWrapper<RequirementChangeLog>()
                        .eq(RequirementChangeLog::getRequirementId, id)
                        .orderByDesc(RequirementChangeLog::getCreatedAt)
                        .last("LIMIT 100"));
        for (RequirementChangeLog log : logs) {
            User editor = userMapper.selectById(log.getEditorId());
            log.setEditorName(editor != null ? editor.displayName() : null);
        }
        return R.ok(logs);
    }

    /**
     * 创建需求
     */
    @Operation(summary = "创建需求")
    @PostMapping
    public R<Requirement> create(
            @PathVariable String projectId,
            @Valid @RequestBody CreateRequirementRequest request,
            Authentication authentication) {

        String requesterId = (String) authentication.getPrincipal();
        Requirement result = requirementService.createRequirement(projectId, request, requesterId);
        return R.ok(result);
    }

    /**
     * 更新需求
     */
    @Operation(summary = "更新需求")
    @PutMapping("/{id}")
    public R<Requirement> update(
            @PathVariable String id,
            @RequestBody UpdateRequirementRequest request,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        Requirement result = requirementService.updateRequirement(id, request, userId);
        return R.ok(result);
    }

    /**
     * 删除需求
     */
    @Operation(summary = "删除需求")
    @DeleteMapping("/{id}")
    public R<Void> delete(
            @PathVariable String id,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        requirementService.deleteRequirement(id, userId);
        return R.ok();
    }

    /**
     * 关联任务
     */
    @Operation(summary = "关联任务到需求")
    @PostMapping("/{id}/tasks/{taskId}")
    public R<Void> linkTask(
            @PathVariable String id,
            @PathVariable String taskId,
            Authentication authentication) {
        requireRequirementVisible(id, authentication);
        requirementService.linkTask(id, taskId);
        return R.ok();
    }

    /**
     * 取消关联任务
     */
    @Operation(summary = "取消关联任务")
    @DeleteMapping("/{id}/tasks/{taskId}")
    public R<Void> unlinkTask(
            @PathVariable String id,
            @PathVariable String taskId,
            Authentication authentication) {
        requireRequirementVisible(id, authentication);
        requirementService.unlinkTask(id, taskId);
        return R.ok();
    }

    /**
     * 获取需求统计
     */
    @Operation(summary = "获取项目需求统计")
    @GetMapping("/stats")
    public R<RequirementStatsResponse> stats(@PathVariable String projectId) {
        RequirementStatsResponse result = requirementService.getStats(projectId);
        return R.ok(result);
    }

    /** 需求所在项目必须对当前用户可见（项目成员口径） */
    private void requireRequirementVisible(String requirementId, Authentication authentication) {
        RequirementResponse detail = requirementService.getRequirementDetail(requirementId);
        if (detail == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), detail.getProjectId())) {
            throw new BusinessException(404, "需求不存在或已被删除");
        }
    }
}