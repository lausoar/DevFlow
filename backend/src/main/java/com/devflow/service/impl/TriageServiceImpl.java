package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.Project;
import com.devflow.entity.ProjectFeedback;
import com.devflow.entity.TriageItem;
import com.devflow.entity.User;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectFeedbackMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.TriageItemMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AuditService;
import com.devflow.service.FullTextSearchService;
import com.devflow.service.NotificationService;
import com.devflow.service.PermissionService;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.TriageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 分诊收件箱实现。
 *
 * 关键约束：
 * - 入箱与源记录同事务，任何一步失败都不会留下"有条目但看不到"的半成品；
 * - 处理动作是原子条件更新（WHERE status='PENDING'），两人同时点只会有一个结论；
 * - 拒绝/合并都不删除源记录，游客在提交记录里仍能看到最终去向。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageServiceImpl implements TriageService {

    private final TriageItemMapper triageItemMapper;
    private final BugMapper bugMapper;
    private final ProjectFeedbackMapper projectFeedbackMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final PermissionService permissionService;
    private final ProjectAccessService projectAccessService;
    private final NotificationService notificationService;
    private final FullTextSearchService fullTextSearchService;
    private final AuditService auditService;

    @Override
    @Transactional
    public TriageItem enqueue(String projectId, String source, String sourceRefId, String title,
                              String submitterName) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(sourceRefId)) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return null;
        }
        LocalDateTime now = now();
        TriageItem item = new TriageItem();
        item.setId(UUID.randomUUID().toString().replace("-", ""));
        item.setProjectId(projectId);
        item.setEnterpriseId(project.getEnterpriseId());
        item.setSource(source);
        item.setSourceRefId(sourceRefId);
        item.setTitle(truncate(title, 200));
        item.setSubmitterName(truncate(submitterName, 50));
        item.setStatus(TriageItem.STATUS_PENDING);
        item.setSlaDueAt(project.getTriageSlaHours() != null && project.getTriageSlaHours() > 0
                ? now.plusHours(project.getTriageSlaHours()) : null);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        try {
            return triageItemMapper.insertIfAbsent(item) > 0 ? item : null;
        } catch (Exception e) {
            log.warn("分诊条目入箱失败（不影响提交）: project={}, source={}, ref={}, 原因={}",
                    projectId, source, sourceRefId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> list(String projectId, String status) {
        LambdaQueryWrapper<TriageItem> wrapper = new LambdaQueryWrapper<TriageItem>()
                .eq(TriageItem::getProjectId, projectId)
                .orderByAsc(TriageItem::getStatus)
                .orderByAsc(TriageItem::getCreatedAt)
                .last("LIMIT 200");
        if (StringUtils.hasText(status)) {
            wrapper.eq(TriageItem::getStatus, status);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TriageItem item : triageItemMapper.selectList(wrapper)) {
            rows.add(view(item));
        }
        return rows;
    }

    @Override
    public Map<String, Long> counts(String projectId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String status : List.of(TriageItem.STATUS_PENDING, TriageItem.STATUS_ACCEPTED,
                TriageItem.STATUS_DECLINED, TriageItem.STATUS_MERGED)) {
            counts.put(status, triageItemMapper.selectCount(new LambdaQueryWrapper<TriageItem>()
                    .eq(TriageItem::getProjectId, projectId)
                    .eq(TriageItem::getStatus, status)));
        }
        return counts;
    }

    @Override
    @Transactional
    public Map<String, Object> accept(String itemId, String operatorId) {
        TriageItem item = requirePending(itemId, operatorId);
        String bugId = item.getResultRefId();
        if (TriageItem.SOURCE_GUEST_BUG.equals(item.getSource())) {
            Bug bug = bugMapper.selectById(item.getSourceRefId());
            if (bug == null) {
                throw new BusinessException(404, "源 Bug 已被删除");
            }
            if (Integer.valueOf(1).equals(bug.getPending())) {
                // 条件更新：只有仍在待确认状态的游客 Bug 才会被接受
                int updated = bugMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Bug>()
                        .eq(Bug::getId, bug.getId())
                        .eq(Bug::getPending, 1)
                        .set(Bug::getPending, 0));
                if (updated == 0) {
                    throw new BusinessException(409, "该 Bug 已被其他成员处理，请刷新");
                }
            }
            bugId = bug.getId();
        } else {
            ProjectFeedback feedback = projectFeedbackMapper.selectById(item.getSourceRefId());
            if (feedback == null) {
                throw new BusinessException(404, "源建议已被删除");
            }
            if ("CONVERTED".equals(feedback.getStatus()) && StringUtils.hasText(feedback.getConvertedBugId())) {
                bugId = feedback.getConvertedBugId();
            } else {
                feedback.setStatus("ACCEPTED");
                projectFeedbackMapper.updateById(feedback);
            }
        }
        decide(itemId, TriageItem.STATUS_ACCEPTED, bugId, operatorId);
        audit(operatorId, item, "triage.accept", "接受分诊条目: " + item.getTitle());
        return view(triageItemMapper.selectById(itemId));
    }

    @Override
    @Transactional
    public Map<String, Object> decline(String itemId, String operatorId, String reason) {
        TriageItem item = requirePending(itemId, operatorId);
        if (TriageItem.SOURCE_GUEST_BUG.equals(item.getSource())) {
            // 拒绝的游客 Bug 保留在库里但不进入正式列表：标记 CLOSED 并保持 pending=0
            bugMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Bug>()
                    .eq(Bug::getId, item.getSourceRefId())
                    .eq(Bug::getPending, 1)
                    .set(Bug::getPending, 0)
                    .set(Bug::getStatus, "CLOSED"));
        } else {
            ProjectFeedback feedback = projectFeedbackMapper.selectById(item.getSourceRefId());
            if (feedback != null && !"CONVERTED".equals(feedback.getStatus())) {
                feedback.setStatus("REJECTED");
                projectFeedbackMapper.updateById(feedback);
            }
        }
        decide(itemId, TriageItem.STATUS_DECLINED, null, operatorId);
        audit(operatorId, item, "triage.decline",
                "拒绝分诊条目: " + item.getTitle() + (StringUtils.hasText(reason) ? "，原因: " + reason : ""));
        return view(triageItemMapper.selectById(itemId));
    }

    @Override
    @Transactional
    public Map<String, Object> merge(String itemId, String targetBugId, String operatorId) {
        TriageItem item = requirePending(itemId, operatorId);
        Bug target = bugMapper.selectById(targetBugId);
        if (target == null || !item.getProjectId().equals(target.getProjectId())) {
            throw new BusinessException(404, "目标 Bug 不存在或不属于该项目");
        }
        if (TriageItem.SOURCE_GUEST_BUG.equals(item.getSource())) {
            Bug source = bugMapper.selectById(item.getSourceRefId());
            if (source != null) {
                // 合并后源 Bug 不再出现在列表里，但记录保留、并留下重复说明
                bugMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Bug>()
                        .eq(Bug::getId, source.getId())
                        .eq(Bug::getPending, 1)
                        .set(Bug::getPending, 0)
                        .set(Bug::getStatus, "CLOSED")
                        .set(Bug::getDescription, appendDuplicateNote(source.getDescription(), targetBugId)));
            }
        } else {
            ProjectFeedback feedback = projectFeedbackMapper.selectById(item.getSourceRefId());
            if (feedback != null && !"CONVERTED".equals(feedback.getStatus())) {
                feedback.setStatus("CONVERTED");
                feedback.setConvertedBugId(targetBugId);
                projectFeedbackMapper.updateById(feedback);
            }
        }
        decide(itemId, TriageItem.STATUS_MERGED, targetBugId, operatorId);
        audit(operatorId, item, "triage.merge",
                "合并分诊条目「" + item.getTitle() + "」到 Bug " + targetBugId);
        return view(triageItemMapper.selectById(itemId));
    }

    @Override
    public List<Map<String, Object>> similar(String itemId) {
        TriageItem item = triageItemMapper.selectById(itemId);
        if (item == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // 词法相似：全文检索拿候选，只作提示，不自动合并
            List<String> ids = fullTextSearchService.searchIds("BUG", item.getTitle(),
                    List.of(item.getProjectId()), 5);
            if (!ids.isEmpty()) {
                for (Bug bug : bugMapper.selectBatchIds(ids)) {
                    if (bug == null || Integer.valueOf(1).equals(bug.getPending())) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", bug.getId());
                    row.put("title", bug.getTitle());
                    row.put("status", bug.getStatus());
                    result.add(row);
                }
            }
        } catch (Exception e) {
            log.debug("相似缺陷提示失败（不影响分诊）: {}", e.getMessage());
        }
        return result;
    }

    // ==================== 内部 ====================

    private TriageItem requirePending(String itemId, String operatorId) {
        TriageItem item = triageItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(404, "分诊条目不存在");
        }
        if (!TriageItem.STATUS_PENDING.equals(item.getStatus())) {
            throw new BusinessException(409, "该条目已被处理，请刷新");
        }
        if (!projectAccessService.canView(operatorId, item.getProjectId())) {
            throw new BusinessException(404, "分诊条目不存在");
        }
        if (!permissionService.hasProjectPermission(operatorId, item.getProjectId(), PermissionCodes.BUG_EDIT)) {
            throw new BusinessException(403, "仅测试人员或项目管理员可处理分诊");
        }
        return item;
    }

    private void decide(String itemId, String status, String resultRefId, String operatorId) {
        int updated = triageItemMapper.decide(itemId, status, resultRefId, operatorId, now());
        if (updated == 0) {
            throw new BusinessException(409, "该条目已被其他成员处理，请刷新");
        }
    }

    private Map<String, Object> view(TriageItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("projectId", item.getProjectId());
        row.put("source", item.getSource());
        row.put("sourceRefId", item.getSourceRefId());
        row.put("title", item.getTitle());
        row.put("submitterName", item.getSubmitterName());
        row.put("status", item.getStatus());
        row.put("resultRefId", item.getResultRefId());
        Map<String, Object> decider = new LinkedHashMap<>();
        if (StringUtils.hasText(item.getDecidedBy())) {
            User user = userMapper.selectById(item.getDecidedBy());
            decider.put("id", item.getDecidedBy());
            decider.put("name", user != null ? user.displayName() : null);
        }
        row.put("decidedBy", decider.isEmpty() ? null : decider);
        row.put("decidedAt", item.getDecidedAt());
        row.put("slaDueAt", item.getSlaDueAt());
        row.put("overdue", item.getSlaDueAt() != null
                && TriageItem.STATUS_PENDING.equals(item.getStatus())
                && item.getSlaDueAt().isBefore(now()));
        row.put("createdAt", item.getCreatedAt());
        return row;
    }

    /** 超时提醒：只发给项目负责人，且由原子占用保证不重复 */
    public void notifySlaBreach(TriageItem item) {
        if (triageItemMapper.markSlaNotified(item.getId(), now()) == 0) {
            return;
        }
        Project project = projectMapper.selectById(item.getProjectId());
        if (project == null || !StringUtils.hasText(project.getOwnerId())) {
            return;
        }
        try {
            notificationService.createNotification(project.getOwnerId(), "TRIAGE",
                    "分诊待处理超时",
                    "「" + item.getTitle() + "」超过首次响应时限仍未处理",
                    "/projects/" + item.getProjectId() + "?tab=triage");
        } catch (Exception e) {
            log.warn("分诊超时通知失败: item={}, 原因={}", item.getId(), e.getMessage());
        }
    }


    private String appendDuplicateNote(String description, String targetBugId) {
        String note = "\n\n[重复] 已合并至 Bug " + targetBugId;
        String base = description == null ? "" : description;
        return base.length() + note.length() > 60000 ? base : base + note;
    }

    private void audit(String operatorId, TriageItem item, String action, String detail) {
        try {
            Project project = projectMapper.selectById(item.getProjectId());
            auditService.log(operatorId, project != null ? project.getEnterpriseId() : null,
                    item.getProjectId(), action, "triage", item.getId(), detail);
        } catch (Exception ignored) {
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
