package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.Program;
import com.devflow.entity.Project;
import com.devflow.entity.Task;
import com.devflow.entity.WorkflowStatus;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProgramMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.ProgramService;
import com.devflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目集实现。
 *
 * 汇总口径与项目内报表保持一致：完成/未完成都按工作流的 status_category 判定
 * （项目可以自定义状态码，项目集层不能写死 "DONE"）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramServiceImpl implements ProgramService {

    private final ProgramMapper programMapper;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final WorkflowService workflowService;

    @Override
    public List<Program> listByEnterprise(String enterpriseId) {
        return programMapper.selectList(new LambdaQueryWrapper<Program>()
                .eq(Program::getEnterpriseId, enterpriseId)
                .orderByAsc(Program::getStatus)
                .orderByAsc(Program::getCreatedAt));
    }

    @Override
    public Program get(String programId) {
        Program p = programMapper.selectById(programId);
        if (p == null) {
            throw new BusinessException(404, "项目集不存在");
        }
        return p;
    }

    @Override
    public Program create(String enterpriseId, Program program, String operatorId) {
        if (!StringUtils.hasText(program.getName())) {
            throw new BusinessException(400, "项目集名称不能为空");
        }
        Program p = new Program();
        p.setEnterpriseId(enterpriseId);
        p.setName(program.getName().trim());
        p.setDescription(program.getDescription());
        p.setOwnerId(StringUtils.hasText(program.getOwnerId()) ? program.getOwnerId() : operatorId);
        p.setStatus("ACTIVE");
        p.setDeleted(0);
        programMapper.insert(p);
        return p;
    }

    @Override
    public Program update(String programId, Program patch, String operatorId) {
        Program existing = get(programId);
        patch.setId(programId);
        patch.setEnterpriseId(existing.getEnterpriseId());
        programMapper.updateById(patch);
        return get(programId);
    }

    @Override
    public void delete(String programId, String operatorId) {
        get(programId);
        // 解散项目集前先把项目移出，避免留下指向已删除项目集的项目
        for (Project p : projectsOf(programId)) {
            projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                    .eq(Project::getId, p.getId())
                    .set(Project::getProgramId, null));
        }
        programMapper.deleteById(programId);
    }

    @Override
    public Map<String, Object> overview(String programId) {
        Program program = get(programId);
        List<Project> projects = projectsOf(programId);
        List<String> projectIds = projects.stream().map(Project::getId).toList();

        List<Task> tasks = projectIds.isEmpty() ? List.of()
                : taskMapper.selectList(new LambdaQueryWrapper<Task>().in(Task::getProjectId, projectIds));
        List<Bug> bugs = projectIds.isEmpty() ? List.of()
                : bugMapper.selectList(new LambdaQueryWrapper<Bug>().in(Bug::getProjectId, projectIds));

        // 按各项目自己的工作流分类统计（项目可自定义状态）
        long doneTasks = 0;
        Map<String, Long> perProjectDone = new LinkedHashMap<>();
        for (Project p : projects) {
            var doneCodes = workflowService.statusCodesInCategory(
                    p.getId(), WorkflowService.TYPE_TASK, WorkflowStatus.CATEGORY_DONE);
            long n = tasks.stream()
                    .filter(t -> p.getId().equals(t.getProjectId()) && doneCodes.contains(t.getStatus()))
                    .count();
            perProjectDone.put(p.getId(), n);
            doneTasks += n;
        }
        long openBugs = 0;
        for (Project p : projects) {
            var closedCodes = workflowService.statusCodesInCategory(
                    p.getId(), WorkflowService.TYPE_BUG, WorkflowStatus.CATEGORY_DONE);
            openBugs += bugs.stream()
                    .filter(b -> p.getId().equals(b.getProjectId()) && !closedCodes.contains(b.getStatus())
                            && !"RESOLVED".equals(b.getStatus()))
                    .count();
        }

        List<Map<String, Object>> projectRows = new ArrayList<>();
        for (Project p : projects) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("name", p.getName());
            row.put("status", p.getStatus());
            row.put("ownerId", p.getOwnerId());
            long total = tasks.stream().filter(t -> p.getId().equals(t.getProjectId())).count();
            long done = perProjectDone.getOrDefault(p.getId(), 0L);
            row.put("totalTasks", total);
            row.put("completedTasks", done);
            row.put("progress", total > 0 ? Math.round(done * 1000.0 / total) / 10.0 : 0.0);
            projectRows.add(row);
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("program", program);
        m.put("projects", projectRows);
        m.put("projectCount", projects.size());
        m.put("totalTasks", tasks.size());
        m.put("completedTasks", doneTasks);
        m.put("openBugs", openBugs);
        m.put("progress", tasks.isEmpty() ? 0.0 : Math.round(doneTasks * 1000.0 / tasks.size()) / 10.0);
        return m;
    }

    @Override
    public void assignProject(String programId, String projectId, String operatorId) {
        Program program = programId == null ? null : get(programId);
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(404, "项目不存在");
        }
        // 只允许挂到同企业的项目集
        if (program != null && !program.getEnterpriseId().equals(project.getEnterpriseId())) {
            throw new BusinessException(403, "项目与项目集不属于同一企业");
        }
        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getId, projectId)
                .set(Project::getProgramId, program == null ? null : program.getId()));
    }

    private List<Project> projectsOf(String programId) {
        return projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getProgramId, programId));
    }
}
