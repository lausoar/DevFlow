package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Workflow;
import com.devflow.entity.WorkflowStatus;
import com.devflow.entity.WorkflowTransition;
import com.devflow.mapper.WorkflowMapper;
import com.devflow.mapper.WorkflowStatusMapper;
import com.devflow.mapper.WorkflowTransitionMapper;
import com.devflow.service.WorkflowService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流服务实现。
 *
 * 缓存：projectId + issueType → 工作流快照（含状态与流转），进程内缓存，
 * 配置改动后调用 reload() 刷新。与权限矩阵一样按项目分组，不跨项目串味。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final WorkflowMapper workflowMapper;
    private final WorkflowStatusMapper statusMapper;
    private final WorkflowTransitionMapper transitionMapper;

    /** 解析结果缓存：key = projectId + ':' + issueType */
    private final Map<String, WorkflowSnapshot> cache = new ConcurrentHashMap<>();

    /** 工作流快照（一次查询组装，判定时零额外查表） */
    public record WorkflowSnapshot(Workflow workflow,
                                   List<WorkflowStatus> statuses,
                                   List<WorkflowTransition> transitions) {
    }

    @PostConstruct
    public void init() {
        // 启动时不预加载全部项目（项目可能很多），首次访问时按需加载并缓存
        log.info("工作流服务已就绪（按项目惰性加载）");
    }

    /** 配置变更后清空缓存 */
    public void reload() {
        cache.clear();
    }

    @Override
    public Workflow resolve(String projectId, String issueType) {
        return snapshot(projectId, issueType).workflow();
    }

    @Override
    public List<WorkflowStatus> statuses(String workflowId) {
        return statusMapper.selectList(new LambdaQueryWrapper<WorkflowStatus>()
                .eq(WorkflowStatus::getWorkflowId, workflowId)
                .orderByAsc(WorkflowStatus::getSort));
    }

    @Override
    public List<WorkflowTransition> transitions(String workflowId) {
        return transitionMapper.selectList(new LambdaQueryWrapper<WorkflowTransition>()
                .eq(WorkflowTransition::getWorkflowId, workflowId));
    }

    @Override
    public String initialStatus(String projectId, String issueType) {
        return snapshot(projectId, issueType).statuses().stream()
                .filter(s -> Integer.valueOf(1).equals(s.getIsInitial()))
                .map(WorkflowStatus::getCode)
                .findFirst()
                .orElseGet(() -> snapshot(projectId, issueType).statuses().stream()
                        .map(WorkflowStatus::getCode)
                        .findFirst()
                        .orElse("TODO"));
    }

    @Override
    public void assertTransitionAllowed(String projectId, String issueType, String from, String to) {
        if (!StringUtils.hasText(to)) {
            throw new BusinessException(400, "目标状态不能为空");
        }
        WorkflowSnapshot snap = snapshot(projectId, issueType);
        List<String> codes = snap.statuses().stream().map(WorkflowStatus::getCode).toList();
        if (!codes.contains(to)) {
            throw new BusinessException(400, "无效的状态: " + to);
        }
        if (from == null || from.equals(to)) {
            return; // 新建或原地更新
        }
        boolean allowed = snap.transitions().stream()
                .anyMatch(t -> from.equals(t.getFromStatus()) && to.equals(t.getToStatus()));
        if (!allowed) {
            throw new BusinessException(400, "不允许从「" + labelOf(snap, from) + "」流转到「" + labelOf(snap, to) + "」");
        }
    }

    @Override
    public String categoryOf(String projectId, String issueType, String statusCode) {
        WorkflowSnapshot snap = snapshot(projectId, issueType);
        return snap.statuses().stream()
                .filter(s -> s.getCode().equals(statusCode))
                .map(WorkflowStatus::getCategory)
                .findFirst()
                .orElseGet(() -> {
                    log.warn("未知状态 {}（project={}, type={}），按 TODO 归类", statusCode, projectId, issueType);
                    return WorkflowStatus.CATEGORY_TODO;
                });
    }

    @Override
    public String labelOf(String projectId, String issueType, String statusCode) {
        return labelOf(snapshot(projectId, issueType), statusCode);
    }

    @Override
    public List<String> statusCodesInCategory(String projectId, String issueType, String category) {
        return snapshot(projectId, issueType).statuses().stream()
                .filter(s -> category.equals(s.getCategory()))
                .map(WorkflowStatus::getCode)
                .toList();
    }

    @Override
    public List<String> statusCodes(String projectId, String issueType) {
        return snapshot(projectId, issueType).statuses().stream().map(WorkflowStatus::getCode).toList();
    }

    @Override
    @Transactional
    public Workflow ensureProjectWorkflow(String projectId, String issueType, String operatorId) {
        Workflow existing = findProjectWorkflow(projectId, issueType);
        if (existing != null) {
            return existing;
        }
        // 从全局模板（或内置默认）复制一份，保持未自定义前的行为不变
        Workflow template = findTemplate(issueType);
        WorkflowSnapshot templateSnap = loadSnapshot(template);

        Workflow copy = new Workflow();
        copy.setProjectId(projectId);
        copy.setName(template.getName());
        copy.setIssueType(issueType);
        copy.setIsDefault(1);
        copy.setDeleted(0);
        workflowMapper.insert(copy);

        for (WorkflowStatus src : templateSnap.statuses()) {
            WorkflowStatus row = new WorkflowStatus();
            row.setWorkflowId(copy.getId());
            row.setCode(src.getCode());
            row.setName(src.getName());
            row.setCategory(src.getCategory());
            row.setColor(src.getColor());
            row.setSort(src.getSort());
            row.setIsInitial(src.getIsInitial());
            row.setIsTerminal(src.getIsTerminal());
            statusMapper.insert(row);
        }
        for (WorkflowTransition src : templateSnap.transitions()) {
            WorkflowTransition row = new WorkflowTransition();
            row.setWorkflowId(copy.getId());
            row.setFromStatus(src.getFromStatus());
            row.setToStatus(src.getToStatus());
            row.setName(src.getName());
            transitionMapper.insert(row);
        }
        reload();
        log.info("已为项目 {} 建立 {} 工作流快照（来源模板 {}）", projectId, issueType, template.getId());
        return copy;
    }

    // ==================== 内部 ====================

    private String labelOf(WorkflowSnapshot snap, String statusCode) {
        return snap.statuses().stream()
                .filter(s -> s.getCode().equals(statusCode))
                .map(WorkflowStatus::getName)
                .findFirst()
                .orElse(statusCode);
    }

    private WorkflowSnapshot snapshot(String projectId, String issueType) {
        String key = (projectId == null ? "" : projectId) + ":" + issueType;
        return cache.computeIfAbsent(key, k -> {
            Workflow wf = findProjectWorkflow(projectId, issueType);
            if (wf == null) {
                wf = findTemplate(issueType);
            }
            return loadSnapshot(wf);
        });
    }

    private WorkflowSnapshot loadSnapshot(Workflow wf) {
        if (wf == null) {
            return new WorkflowSnapshot(null, List.of(), List.of());
        }
        return new WorkflowSnapshot(wf, statuses(wf.getId()), transitions(wf.getId()));
    }

    private Workflow findProjectWorkflow(String projectId, String issueType) {
        if (!StringUtils.hasText(projectId)) {
            return null;
        }
        return workflowMapper.selectList(new LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getProjectId, projectId)
                        .eq(Workflow::getIssueType, issueType)
                        .orderByDesc(Workflow::getIsDefault)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    /** 全局模板：project_id IS NULL 且 is_default=1；没有则用内置默认初始化为模板 */
    private Workflow findTemplate(String issueType) {
        Workflow template = workflowMapper.selectList(new LambdaQueryWrapper<Workflow>()
                        .isNull(Workflow::getProjectId)
                        .eq(Workflow::getIssueType, issueType)
                        .orderByDesc(Workflow::getIsDefault)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        if (template != null) {
            return template;
        }
        return createBuiltinTemplate(issueType);
    }

    /** 内置默认流程：与改造前硬编码的状态集合完全一致，保证行为不变 */
    private synchronized Workflow createBuiltinTemplate(String issueType) {
        // 双重检查：并发下可能已被其他线程创建
        Workflow again = workflowMapper.selectList(new LambdaQueryWrapper<Workflow>()
                        .isNull(Workflow::getProjectId)
                        .eq(Workflow::getIssueType, issueType)
                        .last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        if (again != null) {
            return again;
        }

        Workflow wf = new Workflow();
        wf.setProjectId(null);
        wf.setName("默认" + typeLabel(issueType) + "流程");
        wf.setIssueType(issueType);
        wf.setIsDefault(1);
        wf.setDeleted(0);
        workflowMapper.insert(wf);

        List<String[]> rows = switch (issueType) {
            case TYPE_BUG -> List.of(
                    new String[]{"NEW", "新提出", WorkflowStatus.CATEGORY_TODO, "bg-slate-400"},
                    new String[]{"ASSIGNED", "已指派", WorkflowStatus.CATEGORY_TODO, "bg-blue-400"},
                    new String[]{"DOING", "解决中", WorkflowStatus.CATEGORY_IN_PROGRESS, "bg-blue-500"},
                    new String[]{"RESOLVED", "已解决", WorkflowStatus.CATEGORY_IN_PROGRESS, "bg-amber-500"},
                    new String[]{"REOPENED", "重新打开", WorkflowStatus.CATEGORY_IN_PROGRESS, "bg-red-500"},
                    new String[]{"CLOSED", "已关闭", WorkflowStatus.CATEGORY_DONE, "bg-green-500"});
            case TYPE_REQUIREMENT -> List.of(
                    new String[]{"OPEN", "开放", WorkflowStatus.CATEGORY_TODO, "bg-slate-400"},
                    new String[]{"COMPLETED", "已完成", WorkflowStatus.CATEGORY_DONE, "bg-green-500"},
                    new String[]{"CLOSED", "已关闭", WorkflowStatus.CATEGORY_DONE, "bg-slate-500"});
            default -> List.of(
                    new String[]{"TODO", "待办", WorkflowStatus.CATEGORY_TODO, "bg-slate-400"},
                    new String[]{"DOING", "进行中", WorkflowStatus.CATEGORY_IN_PROGRESS, "bg-blue-500"},
                    new String[]{"TESTING", "测试中", WorkflowStatus.CATEGORY_IN_PROGRESS, "bg-amber-500"},
                    new String[]{"DONE", "已完成", WorkflowStatus.CATEGORY_DONE, "bg-green-500"});
        };

        int sort = 0;
        for (String[] r : rows) {
            WorkflowStatus st = new WorkflowStatus();
            st.setWorkflowId(wf.getId());
            st.setCode(r[0]);
            st.setName(r[1]);
            st.setCategory(r[2]);
            st.setColor(r[3]);
            st.setSort(sort++);
            st.setIsInitial(sort == 1 ? 1 : 0);
            st.setIsTerminal(WorkflowStatus.CATEGORY_DONE.equals(r[2]) ? 1 : 0);
            statusMapper.insert(st);
        }

        // 默认允许任意两状态之间流转（与改造前"只要在状态集合内即可"一致），
        // 项目的自定义收紧在 ensureProjectWorkflow 之后由配置决定
        List<WorkflowStatus> created = statuses(wf.getId());
        for (WorkflowStatus from : created) {
            for (WorkflowStatus to : created) {
                if (from.getCode().equals(to.getCode())) {
                    continue;
                }
                WorkflowTransition tr = new WorkflowTransition();
                tr.setWorkflowId(wf.getId());
                tr.setFromStatus(from.getCode());
                tr.setToStatus(to.getCode());
                transitionMapper.insert(tr);
            }
        }
        log.info("已创建内置 {} 工作流模板: workflowId={}, 状态数={}", issueType, wf.getId(), created.size());
        return wf;
    }

    private String typeLabel(String issueType) {
        return switch (issueType) {
            case TYPE_BUG -> "缺陷";
            case TYPE_REQUIREMENT -> "需求";
            default -> "任务";
        };
    }

    /** 供项目管理端展示：项目工作流概览 */
    public List<Map<String, Object>> describe(String projectId, String issueType) {
        WorkflowSnapshot snap = snapshot(projectId, issueType);
        List<Map<String, Object>> out = new ArrayList<>();
        for (WorkflowStatus s : snap.statuses()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.getCode());
            m.put("name", s.getName());
            m.put("category", s.getCategory());
            m.put("color", s.getColor());
            m.put("sort", s.getSort());
            m.put("isInitial", s.getIsInitial());
            m.put("isTerminal", s.getIsTerminal());
            m.put("next", snap.transitions().stream()
                    .filter(t -> s.getCode().equals(t.getFromStatus()))
                    .map(WorkflowTransition::getToStatus)
                    .toList());
            out.add(m);
        }
        return out;
    }
}
