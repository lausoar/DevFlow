package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Objective;
import com.devflow.entity.Project;
import com.devflow.entity.Requirement;
import com.devflow.entity.Task;
import com.devflow.mapper.ObjectiveAggregateMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.ObjectiveService;
import com.devflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 目标服务实现。
 *
 * 口径（重要，避免重复计数）：
 * - 投入 = 项目下任务的工时（worklog），只从"任务→项目"这一条路径汇总；
 * - 产出 = 进入完成分类的状态事件数（V9 事件表），重开不会重复计数；
 * - 需求单独作为一条轴（直接挂在目标上的需求），不与工时相加。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ObjectiveServiceImpl implements ObjectiveService {

    private static final Set<String> STATUSES = Set.of(
            Objective.STATUS_ACTIVE, Objective.STATUS_ACHIEVED, Objective.STATUS_ARCHIVED);

    private final ObjectiveAggregateMapper objectiveMapper;
    private final ProjectMapper projectMapper;
    private final RequirementMapper requirementMapper;
    private final TaskMapper taskMapper;
    private final WorkflowService workflowService;

    @Override
    public List<Objective> listByEnterprise(String enterpriseId) {
        return objectiveMapper.selectList(new LambdaQueryWrapper<Objective>()
                .eq(Objective::getEnterpriseId, enterpriseId)
                .orderByAsc(Objective::getStatus)
                .orderByDesc(Objective::getPeriod)
                .orderByDesc(Objective::getCreatedAt));
    }

    @Override
    public Objective get(String objectiveId) {
        Objective objective = objectiveMapper.selectById(objectiveId);
        if (objective == null) {
            throw new BusinessException(404, "目标不存在或已被删除");
        }
        return objective;
    }

    @Override
    public Objective requireInEnterprise(String objectiveId, String enterpriseId) {
        Objective objective = get(objectiveId);
        if (!objective.getEnterpriseId().equals(enterpriseId)) {
            throw new BusinessException(403, "目标不属于当前企业");
        }
        return objective;
    }

    @Override
    @Transactional
    public Objective create(String enterpriseId, Objective objective, String operatorId) {
        if (!StringUtils.hasText(objective.getName())) {
            throw new BusinessException(400, "目标名称不能为空");
        }
        if (objective.getStartDate() != null && objective.getEndDate() != null
                && objective.getEndDate().isBefore(objective.getStartDate())) {
            throw new BusinessException(400, "结束日期不能早于开始日期");
        }
        Objective created = new Objective();
        created.setEnterpriseId(enterpriseId);
        created.setProgramId(objective.getProgramId());
        created.setName(objective.getName().trim());
        created.setDescription(objective.getDescription());
        created.setOwnerId(StringUtils.hasText(objective.getOwnerId()) ? objective.getOwnerId() : operatorId);
        created.setPeriod(objective.getPeriod());
        created.setStartDate(objective.getStartDate());
        created.setEndDate(objective.getEndDate());
        created.setStatus(Objective.STATUS_ACTIVE);
        created.setDeleted(0);
        objectiveMapper.insert(created);
        return created;
    }

    @Override
    public Objective update(String objectiveId, Objective patch, String operatorId) {
        Objective existing = get(objectiveId);
        Objective update = new Objective();
        update.setId(objectiveId);
        if (StringUtils.hasText(patch.getName())) {
            update.setName(patch.getName().trim());
        }
        if (patch.getDescription() != null) {
            update.setDescription(patch.getDescription());
        }
        if (StringUtils.hasText(patch.getOwnerId())) {
            update.setOwnerId(patch.getOwnerId());
        }
        if (patch.getPeriod() != null) {
            update.setPeriod(patch.getPeriod());
        }
        if (patch.getStartDate() != null) {
            update.setStartDate(patch.getStartDate());
        }
        if (patch.getEndDate() != null) {
            update.setEndDate(patch.getEndDate());
        }
        if (patch.getProgramId() != null) {
            update.setProgramId(StringUtils.hasText(patch.getProgramId()) ? patch.getProgramId() : null);
        }
        if (patch.getStatus() != null) {
            if (!STATUSES.contains(patch.getStatus())) {
                throw new BusinessException(400, "无效的目标状态");
            }
            update.setStatus(patch.getStatus());
        }
        objectiveMapper.updateById(update);
        return get(existing.getId());
    }

    @Override
    @Transactional
    public void delete(String objectiveId, String operatorId) {
        get(objectiveId);
        // 先解除关联，避免留下指向已删除目标的孤儿外键
        detachAll(objectiveId);
        objectiveMapper.deleteById(objectiveId);
    }

    @Override
    public Map<String, Object> rollup(String objectiveId) {
        Objective objective = get(objectiveId);
        List<String> projectIds = projectIdsOf(objectiveId);
        LocalDate from = objective.getStartDate();
        LocalDate to = objective.getEndDate();

        Map<String, Double> hoursByProject = new LinkedHashMap<>();
        Map<String, Long> completedByProject = new LinkedHashMap<>();
        if (!projectIds.isEmpty()) {
            for (Map<String, Object> row : objectiveMapper.worklogHoursByProject(projectIds, from, to)) {
                hoursByProject.put(String.valueOf(row.get("projectId")), toDouble(row.get("hours")));
            }
            for (Map<String, Object> row : objectiveMapper.completedTasksByProject(projectIds, from, to)) {
                completedByProject.put(String.valueOf(row.get("projectId")), toLong(row.get("n")));
            }
        }

        // 项目行 + 各项目任务完成率（完成状态按项目自己的工作流判定）
        List<Map<String, Object>> projectRows = new ArrayList<>();
        double totalHours = 0;
        long totalCompleted = 0;
        long totalTasks = 0;
        for (Map<String, Object> row : objectiveMapper.projectsOfObjective(objectiveId)) {
            String projectId = String.valueOf(row.get("id"));
            List<Task> tasks = taskMapper.selectList(
                    new LambdaQueryWrapper<Task>().eq(Task::getProjectId, projectId));
            Set<String> doneCodes = new HashSet<>(workflowService.statusCodesInCategory(
                    projectId, WorkflowService.TYPE_TASK, "DONE"));
            long done = tasks.stream().filter(t -> doneCodes.contains(t.getStatus())).count();
            double hours = hoursByProject.getOrDefault(projectId, 0.0);
            long completed = completedByProject.getOrDefault(projectId, 0L);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", projectId);
            item.put("name", row.get("name"));
            item.put("ownerId", row.get("ownerId"));
            item.put("status", row.get("status"));
            item.put("totalTasks", tasks.size());
            item.put("doneTasks", done);
            item.put("completedInPeriod", completed);
            item.put("worklogHours", round1(hours));
            item.put("progress", tasks.isEmpty() ? 0.0 : round1(done * 100.0 / tasks.size()));
            projectRows.add(item);

            totalHours += hours;
            totalCompleted += completed;
            totalTasks += tasks.size();
        }

        // 需求轴：直接挂在目标上的需求，按各项目工作流分类
        List<String> requirementProjectIds = requirementMapper.selectList(
                        new LambdaQueryWrapper<Requirement>().eq(Requirement::getObjectiveId, objectiveId))
                .stream().map(Requirement::getProjectId).distinct().toList();
        Map<String, Set<String>> requirementDoneCodes = new LinkedHashMap<>();
        for (String projectId : requirementProjectIds) {
            requirementDoneCodes.put(projectId, new HashSet<>(workflowService.statusCodesInCategory(
                    projectId, WorkflowService.TYPE_REQUIREMENT, "DONE")));
        }
        long requirementTotal = 0;
        long requirementDone = 0;
        for (Map<String, Object> row : objectiveMapper.requirementCountByObjective(objectiveId)) {
            String projectId = String.valueOf(row.get("projectId"));
            long n = toLong(row.get("n"));
            requirementTotal += n;
            if (requirementDoneCodes.getOrDefault(projectId, Set.of()).contains(String.valueOf(row.get("status")))) {
                requirementDone += n;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("objective", objective);
        result.put("projects", projectRows);
        result.put("projectCount", projectRows.size());
        result.put("totalTasks", totalTasks);
        result.put("completedInPeriod", totalCompleted);
        result.put("worklogHours", round1(totalHours));
        result.put("requirements", Map.of(
                "total", requirementTotal,
                "done", requirementDone,
                "progress", requirementTotal == 0 ? 0.0 : round1(requirementDone * 100.0 / requirementTotal)));
        return result;
    }

    @Override
    public void assignProject(String objectiveId, String projectId, String operatorId) {
        Objective objective = objectiveId == null ? null : get(objectiveId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        if (objective != null && !objective.getEnterpriseId().equals(project.getEnterpriseId())) {
            throw new BusinessException(403, "项目与目标不属于同一企业");
        }
        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, projectId)
                .set(Project::getObjectiveId, objective == null ? null : objective.getId()));
    }

    private List<String> projectIdsOf(String objectiveId) {
        return objectiveMapper.projectsOfObjective(objectiveId).stream()
                .map(row -> String.valueOf(row.get("id")))
                .collect(Collectors.toList());
    }

    private void detachAll(String objectiveId) {
        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getObjectiveId, objectiveId)
                .set(Project::getObjectiveId, null));
        requirementMapper.update(null, new LambdaUpdateWrapper<Requirement>()
                .eq(Requirement::getObjectiveId, objectiveId)
                .set(Requirement::getObjectiveId, null));
    }

    private static long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
