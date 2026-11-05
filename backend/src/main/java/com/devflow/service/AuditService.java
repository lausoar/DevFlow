package com.devflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.AuditLog;

/**
 * 操作审计日志服务
 */
public interface AuditService {

    /**
     * 记录一条审计日志（内部兜底：写入失败仅打 warn，不影响业务）
     *
     * @param userId     操作用户 ID
     * @param enterpriseId 企业 ID（可空）
     * @param projectId    项目 ID（可空）
     * @param action     操作类型，如 member.invite / permission.denied
     * @param targetType 目标类型（可空）
     * @param targetId   目标 ID（可空）
     * @param detail     详情（可空）
     */
    void log(String userId, String enterpriseId, String projectId,
             String action, String targetType, String targetId, String detail);

    /** 按企业分页查询审计日志（时间倒序） */
    Page<AuditLog> pageByEnterprise(String enterpriseId, int page, int pageSize);
}
