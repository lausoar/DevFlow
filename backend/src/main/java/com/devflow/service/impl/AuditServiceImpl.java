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

    /** 链头标记 */
    private static final String GENESIS = "GENESIS";

    /** 链式写入串行化：并发写会让 prev_hash 指向错误的前驱 */
    private final Object chainLock = new Object();

    /**
     * 当前链头哈希（本进程内维护）。
     * 不能靠 "ORDER BY created_at DESC LIMIT 1" 找前驱：created_at 只有秒精度，
     * 同一秒内写入的多条记录会取到同一个前驱，链就分叉了 —— 分叉之后
     * 删除中间记录将无法被检测出来。
     */
    private String chainHead;

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
            // 显式固定到秒再落库：activity_log.created_at 是 DATETIME(0)，
            // MySQL 插入带小数秒的值时会「四舍五入」（37.9 → 38），
            // 若哈希用内存值（37）而校验读回 38，每条新记录都会被判为篡改。
            auditLog.setCreatedAt(java.time.LocalDateTime.now()
                    .truncatedTo(java.time.temporal.ChronoUnit.SECONDS));

            synchronized (chainLock) {
                if (chainHead == null) {
                    chainHead = loadChainHead();
                }
                String prevHash = chainHead;
                auditLog.setPrevHash(prevHash);
                auditLogMapper.insert(auditLog);
                // 插入后才能拿到自增/生成的 id 与 createdAt，这里回填哈希
                auditLog.setRecordHash(hashOf(auditLog));
                AuditLog patch = new AuditLog();
                patch.setId(auditLog.getId());
                patch.setRecordHash(auditLog.getRecordHash());
                auditLogMapper.updateById(patch);
                // 推进链头（仅在落库成功后）
                chainHead = auditLog.getRecordHash();
            }
        } catch (Exception e) {
            // 审计失败不能影响业务主流程
            log.warn("审计日志写入失败: action={}, user={}, err={}", action, userId, e.getMessage());
        }
    }

    /**
     * 冷启动时确定链头：取"没有被任何记录当作 prev_hash 引用"的链式记录。
     * 正常情况下只有一条（真链头），取它的 record_hash 作为下一个前驱。
     */
    private String loadChainHead() {
        List<AuditLog> chained = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .isNotNull(AuditLog::getRecordHash));
        if (chained.isEmpty()) {
            return GENESIS;
        }
        java.util.Set<String> referenced = new java.util.HashSet<>();
        for (AuditLog e : chained) {
            if (e.getPrevHash() != null) {
                referenced.add(e.getPrevHash());
            }
        }
        return chained.stream()
                .map(AuditLog::getRecordHash)
                .filter(h -> !referenced.contains(h))
                .findFirst()
                .orElse(GENESIS);
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
        List<AuditLog> chained = auditLogMapper.selectList(new LambdaQueryWrapper<AuditLog>()
                .eq(AuditLog::getEnterpriseId, enterpriseId)
                .isNotNull(AuditLog::getRecordHash));
        if (chained.isEmpty()) {
            return new ChainVerifyResult(true, 0, null,
                    "该企业暂无哈希链记录（历史记录产生于哈希链启用之前）");
        }

        // 按链结构遍历，而不是按时间排序：created_at 只有秒精度，
        // 同一秒内写入的多条记录排序会乱，靠排序校验会误判为"断链"。
        java.util.Map<String, AuditLog> byPrev = new java.util.HashMap<>();
        AuditLog genesis = null;
        for (AuditLog entry : chained) {
            if (GENESIS.equals(entry.getPrevHash())) {
                genesis = entry;
            } else {
                byPrev.put(entry.getPrevHash(), entry);
            }
        }
        if (genesis == null) {
            return new ChainVerifyResult(false, 0, null,
                    "找不到链头（prev_hash = GENESIS 的记录），历史记录可能被删除");
        }

        int checked = 0;
        AuditLog cursor = genesis;
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (cursor != null) {
            if (!visited.add(cursor.getId())) {
                return new ChainVerifyResult(false, checked, cursor.getId(), "检测到环形引用");
            }
            String recomputed = hashOf(cursor);
            if (!recomputed.equals(cursor.getRecordHash())) {
                return new ChainVerifyResult(false, checked, cursor.getId(),
                        "第 " + (checked + 1) + " 条记录的内容与哈希不符（记录被修改）");
            }
            checked++;
            cursor = byPrev.get(cursor.getRecordHash());
        }
        if (checked < chained.size()) {
            return new ChainVerifyResult(false, checked, null,
                    "有 " + (chained.size() - checked) + " 条记录不在这条链上（可能被插入或删改）");
        }
        return new ChainVerifyResult(true, checked, null, "校验通过");
    }

    /** 哈希 = SHA-256(prevHash | 关键字段)，字段用 \u0001 分隔避免拼接歧义 */
    private String hashOf(AuditLog e) {
        String payload = String.join("\u0001",
                nz(e.getPrevHash()), nz(e.getId()), nz(e.getUserId()), nz(e.getEnterpriseId()),
                nz(e.getProjectId()), nz(e.getAction()), nz(e.getTargetType()),
                nz(e.getTargetId()), nz(e.getDetail()),
                // 必须截断到秒：DB 列是 DATETIME（秒精度），内存值带纳秒，
                // 不截断会导致写入与校验算出不同哈希，每条新记录都判定为"被篡改"
                e.getCreatedAt() == null ? ""
                        : e.getCreatedAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString());
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
