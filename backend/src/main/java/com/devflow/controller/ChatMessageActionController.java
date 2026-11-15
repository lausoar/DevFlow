package com.devflow.controller;

import com.devflow.common.exception.BusinessException;
import com.devflow.common.result.R;
import com.devflow.entity.ChatChannel;
import com.devflow.mapper.ChatChannelMapper;
import com.devflow.security.AccessGuard;
import com.devflow.service.ChatMessageActionService;
import com.devflow.service.ProjectAccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 消息动作：编辑、撤回、表情、已读。
 *
 * 鉴权：消息所属频道的项目必须对当前用户可见；
 * 编辑/撤回额外要求"本人"（服务层校验）。
 */
@Tag(name = "消息动作", description = "编辑、撤回、表情回应、已读回执")
@RestController
@RequiredArgsConstructor
public class ChatMessageActionController {

    private final ChatMessageActionService actionService;
    private final ChatChannelMapper channelMapper;
    private final ProjectAccessService projectAccessService;
    private final AccessGuard guard;

    @Data
    public static class EditRequest {
        private String content;
    }

    @Operation(summary = "编辑消息（仅本人）")
    @PutMapping("/messages/{id}")
    public R<Map<String, Object>> edit(@PathVariable String id,
                                       @RequestBody EditRequest request,
                                       Authentication authentication) {
        String userId = guard.userId(authentication);
        requireChannelVisible(id, userId);
        return R.ok(actionService.edit(id, request.getContent(), userId));
    }

    @Operation(summary = "撤回消息（仅本人，保留原文供审计）")
    @PostMapping("/messages/{id}/recall")
    public R<Map<String, Object>> recall(@PathVariable String id, Authentication authentication) {
        String userId = guard.userId(authentication);
        requireChannelVisible(id, userId);
        return R.ok(actionService.recall(id, userId));
    }

    @Data
    public static class ReactionRequest {
        private String emoji;
    }

    @Operation(summary = "切换表情回应（再点一次取消）")
    @PostMapping("/messages/{id}/reactions")
    public R<Map<String, Object>> toggleReaction(@PathVariable String id,
                                                @RequestBody ReactionRequest request,
                                                Authentication authentication) {
        String userId = guard.userId(authentication);
        requireChannelVisible(id, userId);
        return R.ok(actionService.toggleReaction(id, request.getEmoji(), userId));
    }

    @Data
    public static class MarkReadRequest {
        /** 已读至该时间（ISO-8601，可空=当前时间） */
        private String readAt;
    }

    @Operation(summary = "标记频道已读")
    @PostMapping("/channels/{channelId}/read")
    public R<Map<String, Object>> markRead(@PathVariable String channelId,
                                           @RequestBody(required = false) MarkReadRequest request,
                                           Authentication authentication) {
        String userId = guard.userId(authentication);
        requireChannel(channelId, userId);
        LocalDateTime at = null;
        if (request != null && StringUtils.hasText(request.getReadAt())) {
            try {
                at = LocalDateTime.parse(request.getReadAt());
            } catch (Exception e) {
                throw new BusinessException(400, "readAt 需为 ISO-8601 时间");
            }
        }
        actionService.markRead(channelId, userId, at);
        return R.ok(Map.of("unread", actionService.unreadCount(channelId, userId)));
    }

    @Operation(summary = "频道未读数")
    @GetMapping("/channels/{channelId}/unread")
    public R<Map<String, Integer>> unread(@PathVariable String channelId, Authentication authentication) {
        String userId = guard.userId(authentication);
        requireChannel(channelId, userId);
        return R.ok(Map.of("unread", actionService.unreadCount(channelId, userId)));
    }

    @Operation(summary = "频道已读情况（已读人数与各成员已读时间）")
    @GetMapping("/channels/{channelId}/reads")
    public R<Map<String, Object>> reads(@PathVariable String channelId, Authentication authentication) {
        String userId = guard.userId(authentication);
        requireChannel(channelId, userId);
        return R.ok(actionService.readSummary(channelId, 100));
    }

    /** 频道必须存在且所属项目可见，否则 404（不泄露存在性） */
    private ChatChannel requireChannel(String channelId, String userId) {
        ChatChannel channel = StringUtils.hasText(channelId) ? channelMapper.selectById(channelId) : null;
        if (channel == null || !StringUtils.hasText(channel.getProjectId())
                || !projectAccessService.canView(userId, channel.getProjectId())) {
            throw new BusinessException(404, "频道不存在");
        }
        return channel;
    }

    /** 按消息 id 反查频道再校验可见性 */
    private void requireChannelVisible(String messageId, String userId) {
        var msg = messageActionChannelOf(messageId);
        if (msg == null || !projectAccessService.canView(userId, msg)) {
            throw new BusinessException(404, "消息不存在");
        }
    }

    private String messageActionChannelOf(String messageId) {
        var msg = messageMapper.selectById(messageId);
        if (msg == null) {
            return null;
        }
        var channel = channelMapper.selectById(msg.getChannelId());
        return channel == null ? null : channel.getProjectId();
    }

    private final com.devflow.mapper.ChatMessageMapper messageMapper;
}
