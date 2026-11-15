package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.entity.AuditLog;
import com.devflow.mapper.AuditLogMapper;
import com.devflow.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * 操作审计日志实现。
 *
 * 防篡改（哈希链）：每条记录存 prev_hash 与 record_hash，
 * record_hash = SHA-256(prev_hash + 关键字段)。
 * 有人直接 UPDATE 中间某条记录，后续记录的 prev_hash 就对不上 —— 校验时能立即定位断点。
 * 这是"可发现篡改"而不是"阻止篡改"：数据库管理员仍能改，但改完能被看出来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogMapper auditLogMapper;

    /** 链式写入串行化：并发写会让 prev_hash 指向错误的前驱 */
    private final Object chainLock = new Object();

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
            fillRequestContext(auditLog);

            synchronized (chainLock) {
                AuditLog previous = auditLogMapper.selectOne(new LambdaQueryWrapper<AuditLog>()
                        .orderByDesc(AuditLog::getCreatedAt)
                        .orderByDesc(AuditLog::getId)
                        .last("LIMIT 1"));
                String prevHash = previous != null && previous.getRecordHash() != null
                        ? previous.getRecordHash() : "GENESIS";
                auditLog.setPrevHash(prevHash);
                auditLogMapper.insert(auditLog);
                // 插入后才能拿到自增/生成的 id 与 createdAt，这里回填哈希
                auditLog.setRecordHash(hashOf(auditLog));
                AuditLog patch = new AuditLog();
                patch.setId(auditLog.getId());
                patch.setRecordHash(auditLog.getRecordHash());
                auditLogMapper.updateById(patch);
            }
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

    @Override
    public List<AuditLog> listForExport(String enterpriseId, int limit) {
        return auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getEnterpriseId, enterpriseId)
                .orderByDesc(AuditLog::getCreatedAt)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 10000)));
    }

    @Override
    public ChainVerifyResult verifyChain(String enterpriseId) {
        List<AuditLog> logs = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getEnterpriseId, enterpriseId)
                .orderByAsc(AuditLog::getCreatedAt)
                .orderByAsc(AuditLog::getId));
        if (logs.isEmpty()) {
            return new ChainVerifyResult(true, 0, null, "该企业暂无审计记录");
        }
        String expectedPrev = "GENESIS";
        int checked = 0;
        for (AuditLog entry : logs) {
            // 早期记录没有哈希（本批次之前写入的），跳过并提示，而不是直接判定"被篡改"
            if (entry.getRecordHash() == null) {
                continue;
            }
            if (!expectedPrev.equals(entry.getPrevHash())) {
                return new ChainVerifyResult(false, checked, entry.getId(),
                        "第 " + (checked + 1) + " 条记录的链接与前一条不匹配（可能被删除或篡改）");
            }
            String recomputed = hashOf(entry);
            if (!recomputed.equals(entry.getRecordHash())) {
                return new ChainVerifyResult(false, checked, entry.getId(),
                        "第 " + (checked + 1) + " 条记录的内容与哈希不符（记录被修改）");
            }
            expectedPrev = entry.getRecordHash();
            checked++;
        }
        return new ChainVerifyResult(true, checked, null,
                checked == 0 ? "该企业的审计记录均产生于哈希链启用之前" : "校验通过");
    }

    /** 哈希 = SHA-256(prevHash | 关键字段)，字段用 \u0001 分隔避免拼接歧义 */
    private String hashOf(AuditLog e) {
        String payload = String.join("\u0001",
                nz(e.getPrevHash()), nz(e.getId()), nz(e.getUserId()), nz(e.getEnterpriseId()),
                nz(e.getProjectId()), nz(e.getAction()), nz(e.getTargetType()),
                nz(e.getTargetId()), nz(e.getDetail()),
                e.getCreatedAt() == null ? "" : e.getCreatedAt().toString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("审计哈希计算失败", ex);
        }
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    /** 记录来源 IP 与 UA（从当前请求上下文取；异步线程里取不到时留空） */
    private void fillRequestContext(AuditLog auditLog) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return;
            }
            var request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            auditLog.setIp(forwarded != null && !forwarded.isBlank()
                    ? forwarded.split(",")[0].trim()
                    : request.getRemoteAddr());
            String ua = request.getHeader("User-Agent");
            auditLog.setUserAgent(ua != null && ua.length() > 300 ? ua.substring(0, 300) : ua);
        } catch (Exception ignored) {
            // 取不到请求上下文不影响审计本身
        }
    }
}
