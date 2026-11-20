package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.DesignAsset;
import com.devflow.entity.DesignAssetVersion;
import com.devflow.entity.Requirement;
import com.devflow.mapper.DesignAssetMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.DesignAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
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

    private final DesignAssetService designAssetService;
    private final DesignAssetMapper designAssetMapper;
    private final RequirementMapper requirementMapper;
    private final AccessGuard guard;

    /** 需求下的设计稿列表 */
    @Operation(summary = "获取需求关联的设计稿")
    @GetMapping("/requirements/{requirementId}/designs")
    public R<List<DesignAsset>> listByRequirement(@PathVariable String requirementId,
                                                  Authentication authentication) {
        Requirement requirement = requireRequirement(requirementId);
        guard.requireView(guard.userId(authentication), requirement.getProjectId());
        return R.ok(designAssetService.listByRequirement(requirementId));
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
    public R<DesignAsset> create(@PathVariable String requirementId,
                                 @RequestBody DesignAsset asset,
                                 Authentication authentication) {
        return R.ok(designAssetService.create(requirementId, asset, guard.userId(authentication)));
    }

    /** 更新设计稿元信息（改状态请走 /status） */
    @Operation(summary = "更新设计稿")
    @PutMapping("/designs/{id}")
    public R<DesignAsset> update(@PathVariable String id,
                                 @RequestBody DesignAsset patch,
                                 Authentication authentication) {
        return R.ok(designAssetService.update(id, patch, guard.userId(authentication)));
    }

    /** 评审状态流转：DESIGNING → IN_REVIEW → APPROVED / CHANGES_REQUESTED（可打回、可复审） */
    @Operation(summary = "变更设计稿评审状态")
    @PutMapping("/designs/{id}/status")
    public R<DesignAsset> changeStatus(@PathVariable String id,
                                       @RequestBody Map<String, String> body,
                                       Authentication authentication) {
        String target = body.get("status");
        if (!StringUtils.hasText(target)) {
            throw new BusinessException(400, "请指定目标状态");
        }
        return R.ok(designAssetService.changeStatus(id, target, guard.userId(authentication)));
    }

    /** 新增版本（改稿）：版本号 +1，并留下快照链接 */
    @Operation(summary = "提交设计稿新版本")
    @PostMapping("/designs/{id}/versions")
    public R<DesignAssetVersion> addVersion(@PathVariable String id,
                                            @RequestBody DesignAssetVersion version,
                                            Authentication authentication) {
        return R.ok(designAssetService.addVersion(id, version, guard.userId(authentication)));
    }

    @Operation(summary = "设计稿版本历史")
    @GetMapping("/designs/{id}/versions")
    public R<List<DesignAssetVersion>> versions(@PathVariable String id, Authentication authentication) {
        DesignAsset asset = designAssetMapper.selectById(id);
        if (asset == null) {
            throw new BusinessException(404, "设计稿不存在");
        }
        guard.requireView(guard.userId(authentication), asset.getProjectId());
        return R.ok(designAssetService.versions(id));
    }

    @Operation(summary = "删除设计稿")
    @DeleteMapping("/designs/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        designAssetService.delete(id, guard.userId(authentication));
        return R.ok();
    }

    private Requirement requireRequirement(String id) {
        Requirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw new BusinessException(404, "需求不存在或已被删除");
        }
        return requirement;
    }
}
