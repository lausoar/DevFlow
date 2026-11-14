package com.devflow.controller;

import com.devflow.common.PermissionCodes;
import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.DesignWalkthrough;
import com.devflow.entity.DesignWalkthroughItem;
import com.devflow.security.AccessGuard;
import com.devflow.service.DesignWalkthroughService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * UI 走查单：视觉验收的清单化与闭环。
 * 读 = 项目可见；写 = requirement:manage（与需求编辑同口径）。
 */
@Tag(name = "UI 走查", description = "走查单、走查项、失败项一键转缺陷")
@RestController
@RequiredArgsConstructor
public class DesignWalkthroughController {

    private final DesignWalkthroughService walkthroughService;
    private final com.devflow.mapper.RequirementMapper requirementMapper;
    private final com.devflow.mapper.DesignWalkthroughMapper walkthroughMapper;
    private final com.devflow.mapper.DesignWalkthroughItemMapper itemMapper;
    private final AccessGuard guard;

    @Operation(summary = "需求下的走查单列表（含进度）")
    @GetMapping("/requirements/{requirementId}/walkthroughs")
    public R<List<Map<String, Object>>> listByRequirement(@PathVariable String requirementId,
                                                          Authentication authentication) {
        String userId = guard.userId(authentication);
        guard.requireView(userId, requireProjectOfRequirement(requirementId));
        return R.ok(walkthroughService.listByRequirement(requirementId));
    }

    @Operation(summary = "走查单详情")
    @GetMapping("/walkthroughs/{id}")
    public R<Map<String, Object>> detail(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        DesignWalkthrough w = requireWalkthrough(id);
        guard.requireView(userId, w.getProjectId());
        return R.ok(walkthroughService.detail(id));
    }

    @Operation(summary = "创建走查单")
    @PostMapping("/requirements/{requirementId}/walkthroughs")
    public R<DesignWalkthrough> create(@PathVariable String requirementId,
                                       @RequestBody DesignWalkthrough walkthrough,
                                       Authentication authentication) {
        String userId = guard.userId(authentication);
        String projectId = requireProjectOfRequirement(requirementId);
        guard.requireProjectPermission(userId, projectId, PermissionCodes.REQUIREMENT_MANAGE);
        walkthrough.setRequirementId(requirementId);
        DesignWalkthrough created = walkthroughService.create(walkthrough, userId);
        // 允许创建时一并提交检查点，省去"建单 → 再加项"两步
        if (walkthrough.getTitle() != null) {
            // title 仅是走查单标题；检查点通过 addItems 单独提交
        }
        return R.ok(created);
    }

    @Data
    public static class AddItemsRequest {
        private List<String> titles;
    }

    @Operation(summary = "批量添加走查项")
    @PostMapping("/walkthroughs/{id}/items")
    public R<Map<String, Integer>> addItems(@PathVariable String id,
                                            @RequestBody AddItemsRequest request,
                                            Authentication authentication) {
        String userId = guard.userId(authentication);
        DesignWalkthrough w = requireWalkthrough(id);
        guard.requireProjectPermission(userId, w.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);
        return R.ok(Map.of("added", walkthroughService.addItems(id, request.getTitles(), userId)));
    }

    @Data
    public static class SetResultRequest {
        /** PENDING / PASS / FAIL / NA */
        private String result;
        private String note;
        private String screenshotUrl;
    }

    @Operation(summary = "设置走查项结果")
    @PutMapping("/walkthrough-items/{id}")
    public R<DesignWalkthroughItem> setResult(@PathVariable String id,
                                              @RequestBody SetResultRequest request,
                                              Authentication authentication) {
        String userId = guard.userId(authentication);
        DesignWalkthroughItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "走查项不存在");
        }
        DesignWalkthrough w = requireWalkthrough(item.getWalkthroughId());
        guard.requireProjectPermission(userId, w.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);
        return R.ok(walkthroughService.setResult(id, request.getResult(),
                request.getNote(), request.getScreenshotUrl(), userId));
    }

    @Operation(summary = "删除走查项")
    @DeleteMapping("/walkthrough-items/{id}")
    public R<Void> deleteItem(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        DesignWalkthroughItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "走查项不存在");
        }
        DesignWalkthrough w = requireWalkthrough(item.getWalkthroughId());
        guard.requireProjectPermission(userId, w.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);
        walkthroughService.deleteItem(id, userId);
        return R.ok();
    }

    @Operation(summary = "关闭走查单")
    @PostMapping("/walkthroughs/{id}/close")
    public R<Void> close(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        DesignWalkthrough w = requireWalkthrough(id);
        guard.requireProjectPermission(userId, w.getProjectId(), PermissionCodes.REQUIREMENT_MANAGE);
        walkthroughService.close(id, userId);
        return R.ok();
    }

    @Operation(summary = "失败走查项一键转缺陷")
    @PostMapping("/walkthrough-items/{id}/to-bug")
    public R<Map<String, String>> toBug(@PathVariable String id,
                                        @RequestBody(required = false) Map<String, String> body,
                                        Authentication authentication) {
        String userId = guard.userId(authentication);
        DesignWalkthroughItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BusinessException(404, "走查项不存在");
        }
        DesignWalkthrough w = requireWalkthrough(item.getWalkthroughId());
        guard.requireProjectPermission(userId, w.getProjectId(), PermissionCodes.BUG_CREATE);
        String note = body != null ? body.get("note") : null;
        String bugId = walkthroughService.convertItemToBug(id, userId, note);
        return R.ok(Map.of("bugId", bugId));
    }

    // ==================== 内部 ====================

    private DesignWalkthrough requireWalkthrough(String id) {
        DesignWalkthrough w = walkthroughMapper.selectById(id);
        if (w == null) {
            throw new BusinessException(404, "走查单不存在");
        }
        return w;
    }

    private String requireProjectOfRequirement(String requirementId) {
        com.devflow.entity.Requirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            throw new BusinessException(404, "需求不存在或已被删除");
        }
        return requirement.getProjectId();
    }
}
