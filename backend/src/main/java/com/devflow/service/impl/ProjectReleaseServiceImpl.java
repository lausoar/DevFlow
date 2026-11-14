package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.ProjectRelease;
import com.devflow.entity.Requirement;
import com.devflow.entity.Task;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectReleaseMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.ProjectReleaseService;
import com.devflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 版本 / 发布实现。
 *
 * 发布说明生成几乎不新增数据：需求、任务、缺陷、状态机都已存在，
 * 只是按 fix_version_id 聚合一次，就能产出可交付的变更日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectReleaseServiceImpl implements ProjectReleaseService {

    private final ProjectReleaseMapper releaseMapper;
    private final RequirementMapper requirementMapper;
    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final WorkflowService workflowService;

    @Override
    public List<ProjectRelease> list(String projectId) {
        return releaseMapper.selectList(new LambdaQueryWrapper<ProjectRelease>()
                .eq(ProjectRelease::getProjectId, projectId)
                .orderByAsc(ProjectRelease::getStatus)
                .orderByDesc(ProjectRelease::getReleaseDate));
    }

    @Override
    public ProjectRelease get(String releaseId) {
        ProjectRelease release = releaseMapper.selectById(releaseId);
        if (release == null) {
            throw new BusinessException(404, "版本不存在");
        }
        return release;
    }

    @Override
    @Transactional
    public ProjectRelease create(String projectId, ProjectRelease release, String operatorId) {
        if (!StringUtils.hasText(release.getVersion())) {
            throw new BusinessException(400, "版本号不能为空");
        }
        Long exists = releaseMapper.selectCount(new LambdaQueryWrapper<ProjectRelease>()
                .eq(ProjectRelease::getProjectId, projectId)
                .eq(ProjectRelease::getVersion, release.getVersion().trim()));
        if (exists != null && exists > 0) {
            throw new BusinessException(409, "该版本号已存在");
        }
        release.setId(null);
        release.setProjectId(projectId);
        release.setVersion(release.getVersion().trim());
        if (!StringUtils.hasText(release.getName())) {
            release.setName(release.getVersion());
        }
        if (!StringUtils.hasText(release.getStatus())) {
            release.setStatus(ProjectRelease.STATUS_UNRELEASED);
        }
        release.setDeleted(0);
        releaseMapper.insert(release);
        return release;
    }

    @Override
    public ProjectRelease update(String releaseId, ProjectRelease patch, String operatorId) {
        ProjectRelease existing = get(releaseId);
        if (ProjectRelease.STATUS_RELEASED.equals(existing.getStatus())
                && patch.getStatus() != null && !patch.getStatus().equals(existing.getStatus())) {
            throw new BusinessException(400, "已发布的版本不可改状态");
        }
        patch.setId(releaseId);
        patch.setProjectId(existing.getProjectId());
        patch.setVersion(existing.getVersion());
        patch.setReleasedAt(existing.getReleasedAt());
        patch.setReleasedBy(existing.getReleasedBy());
        releaseMapper.updateById(patch);
        return get(releaseId);
    }

    @Override
    public void delete(String releaseId, String operatorId) {
        ProjectRelease existing = get(releaseId);
        if (ProjectRelease.STATUS_RELEASED.equals(existing.getStatus())) {
            throw new BusinessException(400, "已发布的版本不可删除，请改为归档");
        }
        releaseMapper.deleteById(releaseId);
    }

    @Override
    public String generateReleaseNotes(String releaseId, boolean overwrite) {
        ProjectRelease release = get(releaseId);
        if (!overwrite && StringUtils.hasText(release.getReleaseNotes())) {
            return release.getReleaseNotes();
        }
        String projectId = release.getProjectId();

        // 需求：按是否完成分组
        List<Requirement> requirements = requirementMapper.selectList(
                new LambdaQueryWrapper<Requirement>().eq(Requirement::getFixVersionId, releaseId));
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().eq(Task::getFixVersionId, releaseId));
        List<Bug> bugs = bugMapper.selectList(
                new LambdaQueryWrapper<Bug>().eq(Bug::getFixVersionId, releaseId));

        Set<String> doneTaskCodes = Set.copyOf(
                workflowService.statusCodesInCategory(projectId, WorkflowService.TYPE_TASK, "DONE"));
        Set<String> doneBugCodes = Set.copyOf(
                workflowService.statusCodesInCategory(projectId, WorkflowService.TYPE_BUG, "DONE"));
        Set<String> doneReqCodes = Set.copyOf(
                workflowService.statusCodesInCategory(projectId, WorkflowService.TYPE_REQUIREMENT, "DONE"));

        StringBuilder md = new StringBuilder();
        md.append("# ").append(release.getName()).append("（").append(release.getVersion()).append("）\n\n");
        if (StringUtils.hasText(release.getDescription())) {
            md.append(release.getDescription()).append("\n\n");
        }

        // 需求（新功能 / 改进）
        if (!requirements.isEmpty()) {
            md.append("## 需求\n\n");
            for (Requirement r : requirements) {
                boolean done = doneReqCodes.contains(r.getStatus());
                md.append("- [").append(done ? "x" : " ").append("] ")
                        .append(r.getTitle());
                if (r.getCategory() != null) {
                    md.append("（").append(categoryLabel(r.getCategory())).append("）");
                }
                md.append("\n");
            }
            md.append("\n");
        }

        // 任务（只列已完成，未完成的作为"未完成项"单列）
        List<Task> doneTasks = tasks.stream().filter(t -> doneTaskCodes.contains(t.getStatus())).toList();
        List<Task> openTasks = tasks.stream().filter(t -> !doneTaskCodes.contains(t.getStatus())).toList();
        if (!doneTasks.isEmpty()) {
            md.append("## 已完成的开发任务\n\n");
            doneTasks.forEach(t -> md.append("- ").append(t.getTitle()).append("\n"));
            md.append("\n");
        }
        if (!openTasks.isEmpty()) {
            md.append("## 本版本未完成（发布前请确认）\n\n");
            openTasks.forEach(t -> md.append("- ").append(t.getTitle())
                    .append("（").append(workflowService.labelOf(projectId, WorkflowService.TYPE_TASK, t.getStatus()))
                    .append("）\n"));
            md.append("\n");
        }

        // 缺陷（已修复的列为变更日志）
        if (!bugs.isEmpty()) {
            md.append("## 缺陷修复\n\n");
            for (Bug b : bugs) {
                boolean done = doneBugCodes.contains(b.getStatus());
                md.append("- ").append(done ? "" : "（未关闭）").append(b.getTitle()).append("\n");
            }
            md.append("\n");
        }

        String notes = md.toString().trim();
        ProjectRelease patch = new ProjectRelease();
        patch.setId(releaseId);
        patch.setReleaseNotes(notes);
        releaseMapper.updateById(patch);
        return notes;
    }

    @Override
    public ProjectRelease markReleased(String releaseId, String operatorId) {
        ProjectRelease release = get(releaseId);
        if (ProjectRelease.STATUS_RELEASED.equals(release.getStatus())) {
            throw new BusinessException(409, "该版本已发布");
        }
        // 发布前检查：未完成的工作项只做提示，不阻断（发布决策属于人的判断）
        Map<String, Object> stats = stats(releaseId);
        long unfinished = ((Number) stats.getOrDefault("openTasks", 0)).longValue()
                + ((Number) stats.getOrDefault("openBugs", 0)).longValue();
        if (unfinished > 0) {
            log.info("版本 {} 发布时仍有 {} 个未完成工作项", release.getVersion(), unfinished);
        }

        ProjectRelease patch = new ProjectRelease();
        patch.setId(releaseId);
        patch.setStatus(ProjectRelease.STATUS_RELEASED);
        patch.setReleasedAt(LocalDateTime.now());
        patch.setReleasedBy(operatorId);
        releaseMapper.updateById(patch);
        return get(releaseId);
    }

    @Override
    public Map<String, Object> stats(String releaseId) {
        ProjectRelease release = get(releaseId);
        String projectId = release.getProjectId();

        List<Requirement> requirements = requirementMapper.selectList(
                new LambdaQueryWrapper<Requirement>().eq(Requirement::getFixVersionId, releaseId));
        List<Task> tasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>().eq(Task::getFixVersionId, releaseId));
        List<Bug> bugs = bugMapper.selectList(
                new LambdaQueryWrapper<Bug>().eq(Bug::getFixVersionId, releaseId));

        Set<String> doneTaskCodes = Set.copyOf(
                workflowService.statusCodesInCategory(projectId, WorkflowService.TYPE_TASK, "DONE"));
        Set<String> doneBugCodes = Set.copyOf(
                workflowService.statusCodesInCategory(projectId, WorkflowService.TYPE_BUG, "DONE"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requirements", requirements.size());
        m.put("tasks", tasks.size());
        m.put("openTasks", tasks.stream().filter(t -> !doneTaskCodes.contains(t.getStatus())).count());
        m.put("bugs", bugs.size());
        m.put("openBugs", bugs.stream().filter(b -> !doneBugCodes.contains(b.getStatus())).count());
        return m;
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "FEATURE" -> "新功能";
            case "IMPROVEMENT" -> "改进";
            case "BUGFIX" -> "修复";
            default -> "其他";
        };
    }
}
