package com.devflow.service;

import java.util.List;

/**
 * 项目可见性统一口径：
 * 普通成员只能看到自己参与的项目；企业超级管理员额外可见本企业全部项目（管理视野）。
 * 列表、详情、频道、搜索及项目子资源一律走这里判定，禁止各处自定口径。
 */
public interface ProjectAccessService {

    /** 用户可见的项目 ID 集合（参与的项目 ∪ 企业超管的本企业项目） */
    List<String> visibleProjectIds(String userId);

    /** 用户能否查看某项目 */
    boolean canView(String userId, String projectId);
}
