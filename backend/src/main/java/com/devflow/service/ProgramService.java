package com.devflow.service;

import com.devflow.entity.Program;

import java.util.List;
import java.util.Map;

/**
 * 项目集：跨项目的统一视角。
 *
 * 与"企业"的区别：企业是租户边界（安全隔离），项目集是**管理与汇报边界**
 * （一个企业可以有多个产品线，各自有负责人和里程碑）。
 */
public interface ProgramService {

    List<Program> listByEnterprise(String enterpriseId);

    Program get(String programId);

    Program create(String enterpriseId, Program program, String operatorId);

    Program update(String programId, Program patch, String operatorId);

    void delete(String programId, String operatorId);

    /** 项目集概览：项目清单 + 汇总指标（项目数/任务完成情况/未关闭缺陷） */
    Map<String, Object> overview(String programId);

    /** 把项目移入/移出项目集（programId 为空表示移出） */
    void assignProject(String programId, String projectId, String operatorId);
}
