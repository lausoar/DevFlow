package com.devflow.service;

import com.devflow.entity.ProjectRelease;

import java.util.List;

/**
 * 版本 / 发布服务。
 *
 * Release 是把需求-任务-缺陷按版本聚合的主键，也是自动生成发布说明、
 * 后续接入部署记录与 DORA 指标的基础。
 */
public interface ProjectReleaseService {

    /** 项目版本列表（未归档优先，按版本号倒序） */
    List<ProjectRelease> list(String projectId);

    ProjectRelease get(String releaseId);

    ProjectRelease create(String projectId, ProjectRelease release, String operatorId);

    ProjectRelease update(String releaseId, ProjectRelease patch, String operatorId);

    void delete(String releaseId, String operatorId);

    /**
     * 生成发布说明：按版本聚合需求、任务、缺陷，分「新功能 / 改进 / 修复」输出 Markdown。
     * 结果写回 release_notes（可自动生成后再人工编辑）。
     */
    String generateReleaseNotes(String releaseId, boolean overwrite);

    /** 标记发布（写入发布时间、操作人；已发布不可重复发布） */
    ProjectRelease markReleased(String releaseId, String operatorId);

    /** 该版本下各类工作项的计数（发布前检查用） */
    java.util.Map<String, Object> stats(String releaseId);
}
