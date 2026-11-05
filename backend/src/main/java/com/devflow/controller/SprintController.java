package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.Sprint;
import com.devflow.entity.Task;
import com.devflow.mapper.SprintMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.ProjectAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Sprint 控制器
 */
@Tag(name = "Sprint 管理", description = "Sprint 的增删改查、激活、完成")
@RestController
@RequiredArgsConstructor
public class SprintController {

    private final SprintMapper sprintMapper;
    private final TaskMapper taskMapper;
    private final ProjectAccessService projectAccessService;

    /** 按 ID 取迭代并校验项目可见性，不可见时与"不存在"同响应（不泄露存在性） */
    private Sprint requireVisible(String sprintId, Authentication authentication) {
        Sprint sprint = sprintMapper.selectById(sprintId);
        if (sprint == null || !projectAccessService.canView(
                (String) authentication.getPrincipal(), sprint.getProjectId())) {
            throw new BusinessException(404, "迭代不存在");
        }
        return sprint;
    }

    /** 获取项目的 Sprint 列表 */
    @Operation(summary = "获取项目的 Sprint 列表")
    @GetMapping("/projects/{projectId}/sprints")
    public R<List<Sprint>> listByProject(@PathVariable String projectId, Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        LambdaQueryWrapper<Sprint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Sprint::getProjectId, projectId)
               .orderByDesc(Sprint::getCreatedAt);
        return R.ok(sprintMapper.selectList(wrapper));
    }

    /** 获取 Sprint 详情 */
    @Operation(summary = "获取 Sprint 详情")
    @GetMapping("/sprints/{id}")
    public R<Sprint> getById(@PathVariable String id, Authentication authentication) {
        return R.ok(requireVisible(id, authentication));
    }

    /** 创建 Sprint */
    @Operation(summary = "创建 Sprint")
    @PostMapping("/projects/{projectId}/sprints")
    public R<Sprint> create(@PathVariable String projectId,
                            @RequestBody Sprint sprint,
                            Authentication authentication) {
        if (!projectAccessService.canView((String) authentication.getPrincipal(), projectId)) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
        sprint.setId(null);
        sprint.setProjectId(projectId);
        sprint.setStatus("PLANNING");
        sprint.setDeleted(0);
        sprintMapper.insert(sprint);
        return R.ok(sprint);
    }

    /** 更新 Sprint */
    @Operation(summary = "更新 Sprint")
    @PutMapping("/sprints/{id}")
    public R<Sprint> update(@PathVariable String id,
                            @RequestBody Sprint sprint,
                            Authentication authentication) {
        Sprint existing = requireVisible(id, authentication);
        // 迭代归属与状态不可通过通用更新接口改动（状态走 activate/complete）
        sprint.setId(id);
        sprint.setProjectId(existing.getProjectId());
        sprint.setStatus(existing.getStatus());
        sprint.setDeleted(existing.getDeleted());
        sprintMapper.updateById(sprint);
        return R.ok(sprintMapper.selectById(id));
    }

    /** 激活 Sprint */
    @Operation(summary = "激活 Sprint")
    @PostMapping("/sprints/{id}/activate")
    public R<Sprint> activate(@PathVariable String id, Authentication authentication) {
        Sprint sprint = requireVisible(id, authentication);
        sprint.setStatus("ACTIVE");
        sprintMapper.updateById(sprint);
        return R.ok(sprint);
    }

    /** 完成 Sprint：未完成任务自动移出迭代（sprintId 置空），返回移动数量供前端提示 */
    @Operation(summary = "完成 Sprint")
    @PostMapping("/sprints/{id}/complete")
    public R<Map<String, Object>> complete(@PathVariable String id, Authentication authentication) {
        Sprint sprint = requireVisible(id, authentication);
        sprint.setStatus("COMPLETED");
        sprintMapper.updateById(sprint);

        List<Task> unfinished = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getSprintId, id)
                .ne(Task::getStatus, "DONE"));
        if (!unfinished.isEmpty()) {
            taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                    .eq(Task::getSprintId, id)
                    .ne(Task::getStatus, "DONE")
                    .set(Task::getSprintId, null));
        }
        return R.ok(Map.of("sprint", sprint, "movedOut", unfinished.size()));
    }

    /** 删除 Sprint：未完成任务移出迭代后逻辑删除迭代 */
    @Operation(summary = "删除 Sprint（逻辑删除）")
    @DeleteMapping("/sprints/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        requireVisible(id, authentication);
        taskMapper.update(null, new LambdaUpdateWrapper<Task>()
                .eq(Task::getSprintId, id)
                .set(Task::getSprintId, null));
        sprintMapper.deleteById(id);
        return R.ok();
    }
}
