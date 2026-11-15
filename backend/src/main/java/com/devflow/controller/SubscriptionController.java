package com.devflow.controller;

import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.security.AccessGuard;
import com.devflow.service.ProjectAccessService;
import com.devflow.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工作项关注（订阅式通知）。
 *
 * 与通知类型开关互补：类型开关是"不关心某类事件"，关注是"只关心我盯着的对象"。
 */
@Tag(name = "工作项关注", description = "关注/取消关注工作项，查看我的关注")
@RestController
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final ProjectAccessService projectAccessService;
    private final AccessGuard guard;

    /** 支持关注的对象类型（白名单，避免任意字符串进库） */
    private static final Set<String> TYPES = Set.of("TASK", "BUG", "REQUIREMENT");

    @Operation(summary = "我的关注列表")
    @GetMapping("/subscriptions")
    public R<List<Map<String, Object>>> listMine(@RequestParam(required = false) String targetType,
                                                 Authentication authentication) {
        String userId = guard.userId(authentication);
        if (targetType != null && !targetType.isBlank() && !TYPES.contains(targetType.toUpperCase())) {
            throw new BusinessException(400, "不支持的对象类型");
        }
        return R.ok(subscriptionService.listMine(userId, targetType));
    }

    @Operation(summary = "关注一个工作项")
    @PostMapping("/subscriptions")
    public R<Void> subscribe(@RequestBody Map<String, String> body, Authentication authentication) {
        String userId = guard.userId(authentication);
        String type = requireType(body.get("targetType"));
        String targetId = body.get("targetId");
        if (targetId == null || targetId.isBlank()) {
            throw new BusinessException(400, "缺少 targetId");
        }
        // 必须能看到该对象的项目才能关注
        String projectId = projectOf(type, targetId);
        if (projectId == null || !projectAccessService.canView(userId, projectId)) {
            throw new BusinessException(404, "对象不存在或不可见");
        }
        subscriptionService.subscribe(userId, type, targetId, projectId);
        return R.ok();
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/subscriptions")
    public R<Void> unsubscribe(@RequestParam String targetType,
                               @RequestParam String targetId,
                               Authentication authentication) {
        // 取消关注不需要可见性校验：看不见的对象本来也没有关注关系
        subscriptionService.unsubscribe(guard.userId(authentication),
                requireType(targetType), targetId);
        return R.ok();
    }

    @Operation(summary = "是否已关注某工作项")
    @GetMapping("/subscriptions/status")
    public R<Map<String, Boolean>> status(@RequestParam String targetType,
                                          @RequestParam String targetId,
                                          Authentication authentication) {
        return R.ok(Map.of("subscribed",
                subscriptionService.isSubscribed(guard.userId(authentication),
                        requireType(targetType), targetId)));
    }

    private String requireType(String raw) {
        String type = raw == null ? "" : raw.toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BusinessException(400, "不支持的对象类型: " + raw);
        }
        return type;
    }

    /** 对象所属项目（用于可见性校验；不存在返回 null） */
    private String projectOf(String type, String targetId) {
        return switch (type) {
            case "TASK" -> taskProject(targetId);
            case "BUG" -> bugProject(targetId);
            case "REQUIREMENT" -> requirementProject(targetId);
            default -> null;
        };
    }

    private String taskProject(String id) {
        var t = taskMapper.selectById(id);
        return t == null ? null : t.getProjectId();
    }

    private String bugProject(String id) {
        var b = bugMapper.selectById(id);
        return b == null ? null : b.getProjectId();
    }

    private String requirementProject(String id) {
        var r = requirementMapper.selectById(id);
        return r == null ? null : r.getProjectId();
    }

    private final com.devflow.mapper.TaskMapper taskMapper;
    private final com.devflow.mapper.BugMapper bugMapper;
    private final com.devflow.mapper.RequirementMapper requirementMapper;
}
