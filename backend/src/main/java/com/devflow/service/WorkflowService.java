package com.devflow.service;

import com.devflow.entity.Workflow;
import com.devflow.entity.WorkflowStatus;
import com.devflow.entity.WorkflowTransition;

import java.util.List;

/**
 * 工作流服务：状态集合、流转校验、状态归一分类。
 *
 * 核心约束（改造的地基）：
 * - 状态可配置，但每个状态必须归入 TODO / IN_PROGRESS / DONE 三类；
 * - 所有报表、燃尽、速度、看板进度一律按 category 聚合，绝不按状态码硬编码；
 * - 流转是否允许由 workflow_transition 决定，服务端强制校验。
 */
public interface WorkflowService {

    /** 问题类型 */
    String TYPE_TASK = "TASK";
    String TYPE_BUG = "BUG";
    String TYPE_REQUIREMENT = "REQUIREMENT";

    /**
     * 解析项目某问题类型的生效工作流：
     * 项目自定义优先，否则全局默认模板，都没有则按内置默认自动初始化。
     */
    Workflow resolve(String projectId, String issueType);

    /** 工作流的状态列表（按 sort 升序） */
    List<WorkflowStatus> statuses(String workflowId);

    /** 工作流的流转规则 */
    List<WorkflowTransition> transitions(String workflowId);

    /** 新建该问题类型时的初始状态码 */
    String initialStatus(String projectId, String issueType);

    /** 校验 from → to 是否允许，不允许抛 400 */
    void assertTransitionAllowed(String projectId, String issueType, String from, String to);

    /** 状态码 → 归一分类（TODO/IN_PROGRESS/DONE）；未知状态按 TODO 处理并告警 */
    String categoryOf(String projectId, String issueType, String statusCode);

    /** 状态码 → 展示名 */
    String labelOf(String projectId, String issueType, String statusCode);

    /** 某归一分类下的全部状态码（报表过滤用，避免按单个状态码硬编码） */
    List<String> statusCodesInCategory(String projectId, String issueType, String category);

    /** 项目内全部状态码（校验用） */
    List<String> statusCodes(String projectId, String issueType);

    /** 把全局模板复制成本项目快照（首次自定义时调用，幂等） */
    Workflow ensureProjectWorkflow(String projectId, String issueType, String operatorId);
}
