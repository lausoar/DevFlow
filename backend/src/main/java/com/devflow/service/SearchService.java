package com.devflow.service;

import java.util.Map;

/**
 * 全局搜索：跨项目/任务/Bug/需求/文档聚合检索。
 * 可见性 = 用户参与的项目（project_member），结果不越权。
 */
public interface SearchService {

    /** 按关键词分组搜索，返回 {projects, tasks, bugs, requirements, documents} 各至多 10 条 */
    Map<String, Object> search(String userId, String keyword);
}
