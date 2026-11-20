package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.DesignAsset;
import com.devflow.entity.DesignAssetVersion;
import com.devflow.entity.Project;
import com.devflow.entity.Requirement;
import com.devflow.entity.RequirementTask;
import com.devflow.entity.Task;
import com.devflow.entity.User;
import com.devflow.mapper.DesignAssetMapper;
import com.devflow.mapper.DesignAssetVersionMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.RequirementTaskMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.AutomationMessage;
import com.devflow.service.AutomationRuleService;
import com.devflow.service.DesignAssetService;
import com.devflow.service.ImPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 设计稿服务实现。
 *
 * 通知按"真实发生的流转"触发：
 * - 状态流转走 CAS（带前置状态），并发评审只有一个能成功；
 * - 新版本依靠 uk_asset_version 去重，重试不会重复通知；
 * - 只有 primary 设计稿的 APPROVED 才触发 DESIGN_APPROVED，避免一张副稿
 *   让需求看起来"设计已通过"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesignAssetServiceImpl implements DesignAssetService {

    private final DesignAssetMapper designAssetMapper;
    private final DesignAssetVersionMapper versionMapper;
    private final RequirementMapper requirementMapper;
    private final RequirementTaskMapper requirementTaskMapper;
    private final TaskMapper taskMapper;
    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final AccessGuard guard;
    private final AutomationRuleService automationRuleService;
    private final ImPushService imPushService;

    @Override
    public List<DesignAsset> listByRequirement(String requirementId) {
        return designAssetMapper.selectList(new LambdaQueryWrapper<DesignAsset>()
                .eq(DesignAsset::getRequirementId, requirementId)
                .orderByDesc(DesignAsset::getIsPrimary)
                .orderByAsc(DesignAsset::getSort));
    }

    @Override
    @Transactional
    public DesignAsset create(String requirementId, DesignAsset asset, String userId) {
        Requirement requirement = requireRequirement(requirementId);
        guard.requireProjectPermission(userId, requirement.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);

        if (!StringUtils.hasText(asset.getTitle())) {
            throw new BusinessException(400, "设计稿标题不能为空");
        }
        if (!StringUtils.hasText(asset.getEmbedUrl()) && !StringUtils.hasText(asset.getFileKey())) {
            throw new BusinessException(400, "请至少提供预览链接或外部文件标识");
        }

        asset.setId(null);
        asset.setProjectId(requirement.getProjectId());
        asset.setRequirementId(requirementId);
        asset.setTaskId(null);
        asset.setCreatedBy(userId);
        asset.setStatus(DesignAsset.STATUS_DESIGNING);
        asset.setCurrentVersion(1);
        asset.setDeleted(0);
        if (asset.getIsPrimary() == null) {
            asset.setIsPrimary(0);
        }
        if (asset.getSort() == null) {
            asset.setSort(0);
        }

        // 主设计稿唯一：新指定的主稿会把旧主稿降级
        if (Integer.valueOf(1).equals(asset.getIsPrimary())) {
            demotePrimary(requirementId, null);
        }
        designAssetMapper.insert(asset);

        // 初始版本快照，开发可绑定到具体版本
        DesignAssetVersion v = new DesignAssetVersion();
        v.setAssetId(asset.getId());
        v.setVersion(1);
        v.setSnapshotUrl(asset.getEmbedUrl());
        v.setNote("创建");
        v.setEditorId(userId);
        versionMapper.insert(v);
        return asset;
    }

    @Override
    public DesignAsset update(String id, DesignAsset patch, String userId) {
        DesignAsset existing = requireAsset(id);
        guard.requireProjectPermission(userId, existing.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);

        patch.setId(id);
        patch.setProjectId(existing.getProjectId());
        patch.setRequirementId(existing.getRequirementId());
        patch.setStatus(existing.getStatus());
        patch.setCurrentVersion(existing.getCurrentVersion());
        if (Integer.valueOf(1).equals(patch.getIsPrimary())) {
            demotePrimary(existing.getRequirementId(), id);
        }
        designAssetMapper.updateById(patch);
        return designAssetMapper.selectById(id);
    }

    @Override
    @Transactional
    public DesignAsset changeStatus(String id, String target, String userId) {
        DesignAsset asset = requireAsset(id);
        guard.requireProjectPermission(userId, asset.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);

        String from = asset.getStatus();
        if (!DesignAsset.canTransition(from, target)) {
            throw new BusinessException(400, "不允许从「" + from + "」流转到「" + target + "」");
        }

        // CAS：并发评审（例如同时通过和打回）只有一个能改成功
        if (designAssetMapper.transitionStatus(id, from, target) == 0) {
            throw new BusinessException(409, "设计稿状态已被他人更新，请刷新后重试");
        }

        DesignAsset updated = designAssetMapper.selectById(id);
        syncRequirementStatus(updated);

        // 只有主稿的通过才算需求"设计已通过"；副稿通过按普通变更通知
        boolean primary = Integer.valueOf(1).equals(updated.getIsPrimary());
        if (DesignAsset.STATUS_APPROVED.equals(target) && primary) {
            fire(TRIGGER_DESIGN_APPROVED, updated, from, target, userId);
        } else if (DesignAsset.STATUS_APPROVED.equals(target) || isNoteworthyChange(from, target)) {
            fire(TRIGGER_DESIGN_CHANGED, updated, from, target, userId);
        }
        return updated;
    }

    @Override
    @Transactional
    public DesignAssetVersion addVersion(String id, DesignAssetVersion version, String userId) {
        DesignAsset asset = requireAsset(id);
        guard.requireProjectPermission(userId, asset.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);

        int next = (asset.getCurrentVersion() == null ? 1 : asset.getCurrentVersion()) + 1;
        version.setId(null);
        version.setAssetId(id);
        version.setVersion(next);
        version.setEditorId(userId);
        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException e) {
            // 唯一键 (asset_id, version) 是并发改稿的最后防线：另一个请求已经创建了该版本
            throw new BusinessException(409, "该版本已存在，请刷新后重试");
        }

        DesignAsset patch = new DesignAsset();
        patch.setId(id);
        patch.setCurrentVersion(next);
        // 改稿即回到设计/待评审，避免旧批准状态继续生效
        patch.setStatus(DesignAsset.STATUS_DESIGNING);
        designAssetMapper.updateById(patch);

        DesignAsset updated = designAssetMapper.selectById(id);
        syncRequirementStatus(updated);
        // 改稿 = DESIGN_CHANGED（每版本一次，由唯一键保证不重复）
        fire(TRIGGER_DESIGN_CHANGED, updated, asset.getStatus(), DesignAsset.STATUS_DESIGNING, userId);
        return version;
    }

    @Override
    public List<DesignAssetVersion> versions(String id) {
        requireAsset(id);
        return versionMapper.selectList(new LambdaQueryWrapper<DesignAssetVersion>()
                .eq(DesignAssetVersion::getAssetId, id)
                .orderByDesc(DesignAssetVersion::getVersion));
    }

    @Override
    public void delete(String id, String userId) {
        DesignAsset asset = requireAsset(id);
        guard.requireProjectPermission(userId, asset.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);
        designAssetMapper.deleteById(id);
        syncRequirementStatus(asset);
    }

    @Override
    public String resolveRequirementDesignStatus(String requirementId) {
        if (!StringUtils.hasText(requirementId)) {
            return "NONE";
        }
        List<DesignAsset> assets = designAssetMapper.selectList(new LambdaQueryWrapper<DesignAsset>()
                .eq(DesignAsset::getRequirementId, requirementId)
                .orderByDesc(DesignAsset::getIsPrimary)
                .orderByDesc(DesignAsset::getUpdatedAt));
        if (assets.isEmpty()) {
            return "NONE";
        }
        return assets.get(0).getStatus();
    }

    // ==================== 内部 ====================

    /** 需求设计状态由主稿决定：没有主稿时退回最近更新的一张，避免多稿互相覆盖 */
    private void syncRequirementStatus(DesignAsset asset) {
        if (asset == null || !StringUtils.hasText(asset.getRequirementId())) {
            return;
        }
        Requirement requirement = requirementMapper.selectById(asset.getRequirementId());
        if (requirement == null) {
            return;
        }
        String derived = resolveRequirementDesignStatus(asset.getRequirementId());
        if (Objects.equals(derived, requirement.getDesignStatus())) {
            return;
        }
        Requirement rPatch = new Requirement();
        rPatch.setId(requirement.getId());
        rPatch.setDesignStatus(derived);
        requirementMapper.updateById(rPatch);
    }

    /** 哪些流转值得通知：打回、复审、撤回批准；IN_REVIEW 只是流程内推进，不打扰 */
    private boolean isNoteworthyChange(String from, String to) {
        if (DesignAsset.STATUS_CHANGES_REQUESTED.equals(to)) {
            return true;
        }
        if (DesignAsset.STATUS_APPROVED.equals(from) && DesignAsset.STATUS_DESIGNING.equals(to)) {
            return true;
        }
        return DesignAsset.STATUS_CHANGES_REQUESTED.equals(from)
                && DesignAsset.STATUS_IN_REVIEW.equals(to);
    }

    private void fire(String triggerType, DesignAsset asset, String from, String to, String actorId) {
        try {
            Requirement requirement = StringUtils.hasText(asset.getRequirementId())
                    ? requirementMapper.selectById(asset.getRequirementId()) : null;
            AutomationMessage message = buildMessage(triggerType, asset, requirement, from, to);
            automationRuleService.fire(triggerType, asset.getProjectId(), asset.getId(),
                    asset.getTitle(), actorId, message);
            pushWeCom(triggerType, asset, requirement, from, to);
        } catch (Exception e) {
            log.warn("设计稿通知触发失败（不影响主流程）asset={} trigger={}: {}",
                    asset.getId(), triggerType, e.getMessage());
        }
    }

    private AutomationMessage buildMessage(String triggerType, DesignAsset asset,
                                           Requirement requirement, String from, String to) {
        String action = TRIGGER_DESIGN_APPROVED.equals(triggerType) ? "设计已通过" : "设计稿有变更";
        String requirementTitle = requirement != null ? requirement.getTitle() : "（需求已删除）";
        List<String> taskTitles = linkedTaskTitles(requirement != null ? requirement.getId() : null);
        String owner = displayName(ownerOf(asset, requirement));

        StringBuilder content = new StringBuilder();
        content.append("需求「").append(requirementTitle).append("」的").append(action).append("：")
                .append(asset.getTitle());
        content.append("（v").append(asset.getCurrentVersion() == null ? 1 : asset.getCurrentVersion())
                .append("，").append(from).append(" → ").append(to).append("）");
        if (StringUtils.hasText(owner)) {
            content.append("；负责人：").append(owner);
        }
        if (!taskTitles.isEmpty()) {
            content.append("；关联任务：").append(String.join("、", taskTitles));
        }
        String url = "/projects/" + asset.getProjectId()
                + "?tab=requirements&requirement=" + asset.getRequirementId() + "&design=" + asset.getId();
        return new AutomationMessage(action, content.toString(), url);
    }

    /** 负责人口径：需求负责人 > 需求提出人 > 设计稿创建人 */
    private String ownerOf(DesignAsset asset, Requirement requirement) {
        if (requirement != null) {
            if (StringUtils.hasText(requirement.getAssigneeId())) {
                return requirement.getAssigneeId();
            }
            if (StringUtils.hasText(requirement.getRequesterId())) {
                return requirement.getRequesterId();
            }
        }
        return asset.getCreatedBy();
    }

    private String displayName(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user != null ? user.displayName() : null;
    }

    private List<String> linkedTaskTitles(String requirementId) {
        if (!StringUtils.hasText(requirementId)) {
            return List.of();
        }
        List<RequirementTask> links = requirementTaskMapper.selectList(
                new LambdaQueryWrapper<RequirementTask>().eq(RequirementTask::getRequirementId, requirementId));
        if (links.isEmpty()) {
            return List.of();
        }
        List<String> ids = links.stream().map(RequirementTask::getTaskId).filter(Objects::nonNull)
                .distinct().limit(5).collect(Collectors.toList());
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Task> tasks = taskMapper.selectBatchIds(ids);
        return tasks.stream().map(Task::getTitle).filter(StringUtils::hasText).collect(Collectors.toList());
    }

    /**
     * 企微群推送：APPROVED 低频直接推；CHANGED 只在确实配置了对应规则时推，
     * 避免每次改稿都刷企业群。
     */
    private void pushWeCom(String triggerType, DesignAsset asset,
                           Requirement requirement, String from, String to) {
        Project project = projectMapper.selectById(asset.getProjectId());
        if (project == null || !StringUtils.hasText(project.getEnterpriseId())) {
            return;
        }
        if (TRIGGER_DESIGN_CHANGED.equals(triggerType) && !hasEnabledRule(triggerType, asset.getProjectId())) {
            return;
        }
        String title = TRIGGER_DESIGN_APPROVED.equals(triggerType) ? "**✅ 设计已通过**" : "**🎨 设计稿变更**";
        String md = title + "\n"
                + "> **" + asset.getTitle() + "**\n"
                + "> 需求：" + (requirement != null ? requirement.getTitle() : "（已删除）") + "\n"
                + "> 版本：v" + (asset.getCurrentVersion() == null ? 1 : asset.getCurrentVersion())
                + "（" + from + " → " + to + "）\n"
                + "> [查看详情](/projects/" + asset.getProjectId() + "?tab=requirements&requirement="
                + asset.getRequirementId() + ")";
        imPushService.pushMarkdown(project.getEnterpriseId(), md);
    }

    private boolean hasEnabledRule(String triggerType, String projectId) {
        try {
            return automationRuleService.listByProject(projectId).stream()
                    .anyMatch(r -> triggerType.equals(r.getTriggerType())
                            && Integer.valueOf(1).equals(r.getEnabled())
                            && StringUtils.hasText(r.getNotifyUserId()));
        } catch (Exception e) {
            return false;
        }
    }

    private DesignAsset requireAsset(String id) {
        DesignAsset asset = designAssetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException(404, "设计稿不存在");
        }
        return asset;
    }

    private Requirement requireRequirement(String id) {
        Requirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw new BusinessException(404, "需求不存在或已被删除");
        }
        return requirement;
    }

    /** 同一需求只保留一张主设计稿 */
    private void demotePrimary(String requirementId, String exceptId) {
        if (!StringUtils.hasText(requirementId)) {
            return;
        }
        List<DesignAsset> primaries = designAssetMapper.selectList(new LambdaQueryWrapper<DesignAsset>()
                .eq(DesignAsset::getRequirementId, requirementId)
                .eq(DesignAsset::getIsPrimary, 1));
        for (DesignAsset p : primaries) {
            if (p.getId().equals(exceptId)) {
                continue;
            }
            DesignAsset patch = new DesignAsset();
            patch.setId(p.getId());
            patch.setIsPrimary(0);
            designAssetMapper.updateById(patch);
        }
    }
}
