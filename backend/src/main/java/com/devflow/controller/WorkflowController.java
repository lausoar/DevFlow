package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.Workflow;
import com.devflow.entity.WorkflowStatus;
import com.devflow.entity.WorkflowTransition;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.WorkflowMapper;
import com.devflow.mapper.WorkflowStatusMapper;
import com.devflow.mapper.WorkflowTransitionMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.WorkflowService;
import com.devflow.service.impl.WorkflowServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 工作流配置与读取。
 *
 * 读：项目可见即可（前端看板要按配置渲染列）
 * 写：测试/研发负责人及以上（testcase:manage 同口径）；首次改写会先把全局模板复制成本项目快照。
 */
@Tag(name = "工作流", description = "状态集合、流转规则、看板列配置")
@RestController
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowServiceImpl workflowServiceImpl;
    private final WorkflowMapper workflowMapper;
    private final WorkflowStatusMapper statusMapper;
    private final WorkflowTransitionMapper transitionMapper;
    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final RequirementMapper requirementMapper;
    private final ProjectMapper projectMapper;
    private final AccessGuard guard;

    /** 读取项目某问题类型的生效工作流（含每列可流转到的下一状态） */
    @Operation(summary = "获取项目工作流（看板列 + 流转规则）")
    @GetMapping("/projects/{projectId}/workflow")
    public R<Map<String, Object>> get(@PathVariable String projectId,
                                      @RequestParam(defaultValue = "TASK") String issueType,
                                      Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireView(userId, projectId);
        Workflow wf = workflowService.resolve(projectId, issueType);
        return R.ok(Map.of(
                "workflow", wf,
                "statuses", workflowServiceImpl.describe(projectId, issueType)));
    }

    /** 改写工作流：状态集合整体替换（先快照，再写） */
    @Data
    public static class UpdateWorkflowRequest {
        private List<StatusItem> statuses;
    }

    @Data
    public static class StatusItem {
        private String code;
        private String name;
        /** TODO / IN_PROGRESS / DONE */
        private String category;
        private String color;
        private Integer sort;
        private Boolean isInitial;
        private Boolean isTerminal;
        /** 允许流转到的状态码列表 */
        private List<String> next;
    }

    @Operation(summary = "更新项目工作流（整体替换状态与流转）")
    @PutMapping("/projects/{projectId}/workflow")
    @Transactional
    public R<Map<String, Object>> update(@PathVariable String projectId,
                                         @RequestParam(defaultValue = "TASK") String issueType,
                                         @RequestBody UpdateWorkflowRequest request,
                                         Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.TESTCASE_MANAGE);

        List<StatusItem> items = request.getStatuses();
        if (items == null || items.isEmpty()) {
            throw new BusinessException(400, "至少保留一个状态");
        }
        long initialCount = items.stream().filter(i -> Boolean.TRUE.equals(i.getIsInitial())).count();
        if (initialCount != 1) {
            throw new BusinessException(400, "必须且只能有一个初始状态");
        }
        Set<String> survivingCodes = new LinkedHashSet<>();
        for (StatusItem i : items) {
            if (i.getCode() == null || i.getCode().isBlank()) {
                throw new BusinessException(400, "状态码不能为空");
            }
            if (!List.of(WorkflowStatus.CATEGORY_TODO, WorkflowStatus.CATEGORY_IN_PROGRESS,
                    WorkflowStatus.CATEGORY_DONE).contains(i.getCategory())) {
                throw new BusinessException(400, "状态「" + i.getCode() + "」缺少合法分类（TODO/IN_PROGRESS/DONE）");
            }
            survivingCodes.add(i.getCode().trim());
        }

        // 必须先校验再快照/删除：任何仍在业务数据中的状态都不能随整体替换消失
        // 项目行锁把状态流转与工作流整体替换串行化，避免校验后状态集合被并发改写
        lockProject(projectId);
        assertInUseStatusesSurvive(projectId, issueType, survivingCodes);

        // 首次自定义：把全局模板复制成本项目快照，保证未改动的行为不变
        Workflow wf = workflowService.ensureProjectWorkflow(projectId, issueType, userId);

        // 状态是业务表里已经存着的值，不能直接删掉还有数据的状态
        transitionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowTransition>()
                .eq(WorkflowTransition::getWorkflowId, wf.getId()));
        statusMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowStatus>()
                .eq(WorkflowStatus::getWorkflowId, wf.getId()));

        int sort = 0;
        for (StatusItem item : items) {
            WorkflowStatus st = new WorkflowStatus();
            st.setWorkflowId(wf.getId());
            st.setCode(item.getCode().trim());
            st.setName(item.getName() != null ? item.getName() : item.getCode());
            st.setCategory(item.getCategory());
            st.setColor(item.getColor());
            st.setSort(item.getSort() != null ? item.getSort() : sort);
            st.setIsInitial(Boolean.TRUE.equals(item.getIsInitial()) ? 1 : 0);
            st.setIsTerminal(Boolean.TRUE.equals(item.getIsTerminal()) ? 1 : 0);
            statusMapper.insert(st);
            sort++;
        }
        for (StatusItem item : items) {
            List<String> next = item.getNext();
            if (next == null || next.isEmpty()) {
                continue;
            }
            for (String to : next) {
                if (to.equals(item.getCode())) {
                    continue;
                }
                WorkflowTransition tr = new WorkflowTransition();
                tr.setWorkflowId(wf.getId());
                tr.setFromStatus(item.getCode().trim());
                tr.setToStatus(to);
                transitionMapper.insert(tr);
            }
        }
        workflowServiceImpl.reload();

        return R.ok(Map.of(
                "workflow", wf,
                "statuses", workflowServiceImpl.describe(projectId, issueType)));
    }

    /** 恢复成全局模板（删除项目快照） */
    @Operation(summary = "恢复默认工作流")
    @DeleteMapping("/projects/{projectId}/workflow")
    @Transactional
    public R<Void> reset(@PathVariable String projectId,
                         @RequestParam(defaultValue = "TASK") String issueType,
                         Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.TESTCASE_MANAGE);

        lockProject(projectId);
        List<Workflow> own = workflowMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getProjectId, projectId)
                        .eq(Workflow::getIssueType, issueType));
        if (!own.isEmpty()) {
            // null 项目 ID 与运行时全局回退口径一致，不能拿当前项目自定义状态做校验
            Workflow fallback = workflowService.resolve(null, issueType);
            Set<String> fallbackCodes = workflowService.statuses(fallback.getId()).stream()
                    .map(WorkflowStatus::getCode)
                    .collect(Collectors.toSet());
            assertInUseStatusesSurvive(projectId, issueType, fallbackCodes);
        }
        for (Workflow wf : own) {
            transitionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowTransition>()
                    .eq(WorkflowTransition::getWorkflowId, wf.getId()));
            statusMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowStatus>()
                    .eq(WorkflowStatus::getWorkflowId, wf.getId()));
            workflowMapper.deleteById(wf.getId());
        }
        workflowServiceImpl.reload();
        return R.ok();
    }

    private void lockProject(String projectId) {
        if (projectMapper.selectForUpdate(projectId) == null) {
            throw new BusinessException(404, "项目不存在或已被删除");
        }
    }

    private void assertInUseStatusesSurvive(String projectId, String issueType, Set<String> survivingCodes) {
        List<String> inUse = switch (issueType) {
            case WorkflowService.TYPE_TASK -> taskMapper.selectUsedStatusCodes(projectId);
            case WorkflowService.TYPE_BUG -> bugMapper.selectUsedStatusCodes(projectId);
            case WorkflowService.TYPE_REQUIREMENT -> requirementMapper.selectUsedStatusCodes(projectId);
            default -> throw new BusinessException(400, "不支持的问题类型: " + issueType);
        };
        Set<String> missing = new TreeSet<>(inUse);
        missing.removeAll(survivingCodes);
        if (!missing.isEmpty()) {
            throw new BusinessException(409,
                    "仍有业务数据使用状态: " + String.join(", ", missing) + "，请先迁移数据或保留这些状态");
        }
    }
}
