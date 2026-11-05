package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.AuditLog;
import com.devflow.mapper.AuditLogMapper;
import com.devflow.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 操作审计日志实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void log(String userId, String enterpriseId, String projectId,
                    String action, String targetType, String targetId, String detail) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setEnterpriseId(enterpriseId);
            auditLog.setProjectId(projectId);
            auditLog.setAction(action);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            auditLog.setDetail(detail);
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // 审计失败不能影响业务主流程
            log.warn("审计日志写入失败: action={}, user={}, err={}", action, userId, e.getMessage());
        }
    }

    @Override
    public Page<AuditLog> pageByEnterprise(String enterpriseId, int page, int pageSize) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuditLog::getEnterpriseId, enterpriseId);
        wrapper.orderByDesc(AuditLog::getCreatedAt);
        return auditLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
    }
}
