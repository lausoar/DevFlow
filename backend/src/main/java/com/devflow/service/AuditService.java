package com.devflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.AuditLog;

import java.util.List;

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

    /**
     * 记录必须成功的审计日志（导出等合规场景）。
     * 与 log 的"不影响业务主流程"不同：写失败会抛出异常，调用方必须放弃导出，
     * 不能出现"数据已导出但审计缺失"。
     */
    void logRequired(String userId, String enterpriseId, String projectId,
                     String action, String targetType, String targetId, String detail);

    /** 按企业分页查询审计日志（时间倒序） */
    Page<AuditLog> pageByEnterprise(String enterpriseId, int page, int pageSize);

    /** 导出用：按企业取一批审计记录（上限 10000） */
    List<AuditLog> listForExport(String enterpriseId, int limit);

    /** 校验哈希链完整性：返回是否连续、校验了多少条、断点位置 */
    ChainVerifyResult verifyChain(String enterpriseId);

    /** 链校验结果 */
    record ChainVerifyResult(boolean valid, int checkedRecords, String brokenAtId, String message) {
    }
}
