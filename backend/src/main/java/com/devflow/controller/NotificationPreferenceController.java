package com.devflow.controller;

import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.NotificationPreference;
import com.devflow.mapper.NotificationPreferenceMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.security.AccessGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通知偏好与免打扰。
 *
 * 为什么需要：当前是"事件即通知"，任务每次字段变更都推一条，用户只能全局静音。
 * 这里做到"按类型开关 + 免打扰时段"，并让通知服务在推送前查一次偏好。
 */
@Tag(name = "通知偏好", description = "按类型开关通知、免打扰时段")
@RestController
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceMapper preferenceMapper;
    private final UserMapper userMapper;
    private final AccessGuard guard;

    /** 可配置的通知类型（与 NotificationServiceImpl 里的 type 对应） */
    private static final List<String> TYPES = List.of(
            "COMMENT", "MENTION", "TASK_UPDATE", "BUG_UPDATE", "SPRINT_UPDATE", "SYSTEM", "AI");

    @Operation(summary = "我的通知偏好与免打扰设置")
    @GetMapping("/notification-preferences")
    public R<Map<String, Object>> get(Authentication authentication) {
        String userId = guard.userId(authentication);
        List<NotificationPreference> rows = preferenceMapper.selectList(
                new LambdaQueryWrapper<NotificationPreference>()
                        .eq(NotificationPreference::getUserId, userId));
        Map<String, Boolean> byType = new LinkedHashMap<>();
        // 未配置的类型默认开启（不能因为没配过就静默收不到通知）
        TYPES.forEach(t -> byType.put(t, true));
        rows.forEach(r -> byType.put(r.getType(), Integer.valueOf(1).equals(r.getEnabled())));

        var user = userMapper.selectById(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("types", byType);
        m.put("dndEnabled", user != null && Integer.valueOf(1).equals(user.getDndEnabled()));
        m.put("dndStart", user != null && user.getDndStart() != null ? user.getDndStart().toString() : null);
        m.put("dndEnd", user != null && user.getDndEnd() != null ? user.getDndEnd().toString() : null);
        return R.ok(m);
    }

    @Data
    public static class UpdatePreferenceRequest {
        private Map<String, Boolean> types;
        private Boolean dndEnabled;
        /** HH:mm 格式 */
        private String dndStart;
        private String dndEnd;
    }

    @Operation(summary = "更新通知偏好与免打扰")
    @PutMapping("/notification-preferences")
    public R<Void> update(@RequestBody UpdatePreferenceRequest request, Authentication authentication) {
        String userId = guard.userId(authentication);

        if (request.getTypes() != null) {
            for (Map.Entry<String, Boolean> e : request.getTypes().entrySet()) {
                if (!TYPES.contains(e.getKey())) {
                    throw new BusinessException(400, "未知的通知类型: " + e.getKey());
                }
                NotificationPreference existing = preferenceMapper.selectOne(
                        new LambdaQueryWrapper<NotificationPreference>()
                                .eq(NotificationPreference::getUserId, userId)
                                .eq(NotificationPreference::getType, e.getKey()));
                if (existing == null) {
                    NotificationPreference row = new NotificationPreference();
                    row.setUserId(userId);
                    row.setType(e.getKey());
                    row.setEnabled(Boolean.TRUE.equals(e.getValue()) ? 1 : 0);
                    preferenceMapper.insert(row);
                } else {
                    existing.setEnabled(Boolean.TRUE.equals(e.getValue()) ? 1 : 0);
                    preferenceMapper.updateById(existing);
                }
            }
        }

        var user = userMapper.selectById(userId);
        if (user != null) {
            var patch = new com.devflow.entity.User();
            patch.setId(userId);
            boolean changed = false;
            if (request.getDndEnabled() != null) {
                patch.setDndEnabled(Boolean.TRUE.equals(request.getDndEnabled()) ? 1 : 0);
                changed = true;
            }
            if (request.getDndStart() != null) {
                patch.setDndStart(parseTime(request.getDndStart(), "dndStart"));
                changed = true;
            }
            if (request.getDndEnd() != null) {
                patch.setDndEnd(parseTime(request.getDndEnd(), "dndEnd"));
                changed = true;
            }
            if (changed) {
                userMapper.updateById(patch);
            }
        }
        return R.ok();
    }

    /** 允许一键全部开启/关闭 */
    @Data
    public static class BulkRequest {
        private Boolean enabled;
    }

    @Operation(summary = "一键开启/关闭全部通知类型")
    @PostMapping("/notification-preferences/bulk")
    public R<Void> bulk(@RequestBody BulkRequest request, Authentication authentication) {
        String userId = guard.userId(authentication);
        int enabled = Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0;
        for (String type : TYPES) {
            NotificationPreference existing = preferenceMapper.selectOne(
                    new LambdaQueryWrapper<NotificationPreference>()
                            .eq(NotificationPreference::getUserId, userId)
                            .eq(NotificationPreference::getType, type));
            if (existing == null) {
                NotificationPreference row = new NotificationPreference();
                row.setUserId(userId);
                row.setType(type);
                row.setEnabled(enabled);
                preferenceMapper.insert(row);
            } else {
                existing.setEnabled(enabled);
                preferenceMapper.updateById(existing);
            }
        }
        return R.ok();
    }

    private LocalTime parseTime(String raw, String field) {
        try {
            return LocalTime.parse(raw.trim());
        } catch (Exception e) {
            throw new BusinessException(400, field + " 需为 HH:mm 格式");
        }
    }
}
