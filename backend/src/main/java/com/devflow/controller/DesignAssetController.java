package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.DesignAsset;
import com.devflow.entity.DesignAssetVersion;
import com.devflow.entity.Requirement;
import com.devflow.mapper.DesignAssetMapper;
import com.devflow.mapper.DesignAssetVersionMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.security.AccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设计稿协作。
 *
 * 定位：平台不自研画布，只做「设计协作的编排层」——
 * 存指针（provider + fileKey + nodeId）、存版本快照、存评审状态，
 * 把 Figma / 蓝湖 / MasterGo 的产出编排进需求、任务、验收的主链路。
 */
@Tag(name = "设计稿", description = "设计稿关联、版本、评审状态")
@RestController
@RequiredArgsConstructor
public class DesignAssetController {

    private final DesignAssetMapper designAssetMapper;
    private final DesignAssetVersionMapper versionMapper;
    private final RequirementMapper requirementMapper;
    private final AccessGuard guard;

    /** 需求下的设计稿列表 */
    @Operation(summary = "获取需求关联的设计稿")
    @GetMapping("/requirements/{requirementId}/designs")
    public R<List<DesignAsset>> listByRequirement(@PathVariable String requirementId,
                                                  Authentication authentication) {
        Requirement requirement = requireRequirement(requirementId);
        guard.requireView(guard.userId(authentication), requirement.getProjectId());
        return R.ok(designAssetMapper.selectList(new LambdaQueryWrapper<DesignAsset>()
                .eq(DesignAsset::getRequirementId, requirementId)
                .orderByDesc(DesignAsset::getIsPrimary)
                .orderByAsc(DesignAsset::getSort)));
    }

    /** 项目维度：设计稿总览（设计覆盖率、待评审数量） */
    @Operation(summary = "项目设计稿总览")
    @GetMapping("/projects/{projectId}/designs")
    public R<Map<String, Object>> listByProject(@PathVariable String projectId,
                                                @RequestParam(required = false) String status,
                                                Authentication authentication) {
        guard.requireView(guard.userId(authentication), projectId);
        LambdaQueryWrapper<DesignAsset> wrapper = new LambdaQueryWrapper<DesignAsset>()
                .eq(DesignAsset::getProjectId, projectId)
                .orderByDesc(DesignAsset::getUpdatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(DesignAsset::getStatus, status);
        }
        List<DesignAsset> assets = designAssetMapper.selectList(wrapper);

        long requirements = requirementMapper.selectCount(
                new LambdaQueryWrapper<Requirement>().eq(Requirement::getProjectId, projectId));
        long covered = assets.stream()
                .map(DesignAsset::getRequirementId)
                .filter(StringUtils::hasText).distinct().count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", assets.size());
        stats.put("approved", assets.stream().filter(a -> DesignAsset.STATUS_APPROVED.equals(a.getStatus())).count());
        stats.put("inReview", assets.stream().filter(a -> DesignAsset.STATUS_IN_REVIEW.equals(a.getStatus())).count());
        stats.put("changesRequested", assets.stream()
                .filter(a -> DesignAsset.STATUS_CHANGES_REQUESTED.equals(a.getStatus())).count());
        stats.put("requirementsWithDesign", covered);
        stats.put("requirementTotal", requirements);
        stats.put("coverageRate", requirements > 0 ? Math.round(covered * 1000.0 / requirements) / 10.0 : 0.0);

        return R.ok(Map.of("assets", assets, "stats", stats));
    }

    /** 关联设计稿到需求（同一需求至多一张主设计稿） */
    @Operation(summary = "为需求关联设计稿")
    @PostMapping("/requirements/{requirementId}/designs")
    @Transactional
    public R<DesignAsset> create(@PathVariable String requirementId,
                                 @RequestBody DesignAsset asset,
                                 Authentication authentication) {
        String userId = guard.userId(authentication);
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

        return R.ok(asset);
    }

    /** 更新设计稿元信息（改状态请走 /status） */
    @Operation(summary = "更新设计稿")
    @PutMapping("/designs/{id}")
    public R<DesignAsset> update(@PathVariable String id,
                                 @RequestBody DesignAsset patch,
                                 Authentication authentication) {
        String userId = guard.userId(authentication);
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
        return R.ok(designAssetMapper.selectById(id));
    }

    /** 评审状态流转：DESIGNING → IN_REVIEW → APPROVED / CHANGES_REQUESTED（可打回、可复审） */
    @Operation(summary = "变更设计稿评审状态")
    @PutMapping("/designs/{id}/status")
    public R<DesignAsset> changeStatus(@PathVariable String id,
                                       @RequestBody Map<String, String> body,
                                       Authentication authentication) {
        String userId = guard.userId(authentication);
        DesignAsset asset = requireAsset(id);
        guard.requireProjectPermission(userId, asset.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);

        String target = body.get("status");
        if (!DesignAsset.canTransition(asset.getStatus(), target)) {
            throw new BusinessException(400, "不允许从「" + asset.getStatus() + "」流转到「" + target + "」");
        }
        DesignAsset patch = new DesignAsset();
        patch.setId(id);
        patch.setStatus(target);
        designAssetMapper.updateById(patch);

        // 设计通过后，回写需求的设计状态，作为"开发就绪"的依据之一
        if (StringUtils.hasText(asset.getRequirementId())) {
            Requirement requirement = requirementMapper.selectById(asset.getRequirementId());
            if (requirement != null) {
                Requirement rPatch = new Requirement();
                rPatch.setId(requirement.getId());
                rPatch.setDesignStatus(target);
                requirementMapper.updateById(rPatch);
            }
        }
        return R.ok(designAssetMapper.selectById(id));
    }

    /** 新增版本（改稿）：版本号 +1，并留下快照链接 */
    @Operation(summary = "提交设计稿新版本")
    @PostMapping("/designs/{id}/versions")
    @Transactional
    public R<DesignAssetVersion> addVersion(@PathVariable String id,
                                            @RequestBody DesignAssetVersion version,
                                            Authentication authentication) {
        String userId = guard.userId(authentication);
        DesignAsset asset = requireAsset(id);
        guard.requireProjectPermission(userId, asset.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);

        int next = (asset.getCurrentVersion() == null ? 1 : asset.getCurrentVersion()) + 1;
        version.setId(null);
        version.setAssetId(id);
        version.setVersion(next);
        version.setEditorId(userId);
        versionMapper.insert(version);

        DesignAsset patch = new DesignAsset();
        patch.setId(id);
        patch.setCurrentVersion(next);
        // 改稿即回到设计/待评审，避免旧批准状态继续生效
        patch.setStatus(DesignAsset.STATUS_DESIGNING);
        designAssetMapper.updateById(patch);
        return R.ok(version);
    }

    @Operation(summary = "设计稿版本历史")
    @GetMapping("/designs/{id}/versions")
    public R<List<DesignAssetVersion>> versions(@PathVariable String id, Authentication authentication) {
        DesignAsset asset = requireAsset(id);
        guard.requireView(guard.userId(authentication), asset.getProjectId());
        return R.ok(versionMapper.selectList(new LambdaQueryWrapper<DesignAssetVersion>()
                .eq(DesignAssetVersion::getAssetId, id)
                .orderByDesc(DesignAssetVersion::getVersion)));
    }

    @Operation(summary = "删除设计稿")
    @DeleteMapping("/designs/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        DesignAsset asset = requireAsset(id);
        guard.requireProjectPermission(userId, asset.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);
        designAssetMapper.deleteById(id);
        return R.ok();
    }

    // ==================== 内部 ====================

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
