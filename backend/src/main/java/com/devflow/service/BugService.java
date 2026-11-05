package com.devflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.Bug;

/**
 * Bug 服务接口
 */
public interface BugService {

    Page<Bug> listByProject(String projectId, int page, int pageSize, String keyword, String status);

    java.util.List<Bug> listMyBugs(String userId);

    Bug getById(String id);

    Bug create(String projectId, Bug bug, String reporterId);

    Bug update(String id, Bug bug);

    /** 仅负责人/报告人可调用：变更 Bug 状态并通知对方 */
    Bug changeStatus(String id, String status, String operatorId);

    void delete(String id);
}
