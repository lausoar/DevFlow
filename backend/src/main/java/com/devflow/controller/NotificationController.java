package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.devflow.common.result.R;
import com.devflow.entity.Notification;
import com.devflow.mapper.NotificationMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通知控制器
 * 所有操作都验证 userId 所有权，防止越权
 */
@Tag(name = "通知", description = "通知列表、已读状态")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationMapper notificationMapper;

    @Operation(summary = "获取我的通知列表")
    @GetMapping
    public R<Page<Notification>> list(
            Authentication authentication,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean unreadOnly) {

        String userId = (String) authentication.getPrincipal();

        Page<Notification> pageParam = new Page<>(page, pageSize);
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId);

        if (type != null && !type.isBlank()) {
            wrapper.eq(Notification::getType, type);
        }
        if (Boolean.TRUE.equals(unreadOnly)) {
            wrapper.eq(Notification::getIsRead, 0);
        }

        wrapper.orderByDesc(Notification::getCreatedAt);
        return R.ok(notificationMapper.selectPage(pageParam, wrapper));
    }

    @Operation(summary = "获取未读通知数量")
    @GetMapping("/unread-count")
    public R<Map<String, Integer>> unreadCount(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();

        Long count = notificationMapper.selectCount(
            new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
        );

        return R.ok(Map.of("count", count.intValue()));
    }

    @Operation(summary = "标记通知已读")
    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable String id, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();

        // 验证通知归属当前用户
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(userId)) {
            return R.fail("通知不存在或不属于当前用户");
        }

        notification.setIsRead(1);
        notificationMapper.updateById(notification);
        return R.ok();
    }

    @Operation(summary = "标记所有通知已读")
    @PutMapping("/read-all")
    public R<Void> markAllRead(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();

        Notification notification = new Notification();
        notification.setIsRead(1);
        notificationMapper.update(notification,
            new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
        );

        return R.ok();
    }

    @Operation(summary = "删除通知")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();

        // 验证通知归属当前用户，防止越权删除
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(userId)) {
            return R.fail("通知不存在或不属于当前用户");
        }

        notificationMapper.deleteById(id);
        return R.ok();
    }
}
