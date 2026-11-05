package com.devflow.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.result.R;
import com.devflow.entity.AiChatMessage;
import com.devflow.mapper.AiChatMessageMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * AI 助手消息历史控制器
 * 按用户账号持久化 AI 中心的对话与行为记录
 */
@Tag(name = "AI 消息历史", description = "AI 助手对话与行为历史的持久化")
@RestController
@RequestMapping("/ai/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final AiChatMessageMapper messageMapper;

    @Operation(summary = "获取我的 AI 消息历史（时间正序，最近 limit 条）")
    @GetMapping("/history")
    public R<List<AiChatMessage>> history(
            @RequestParam(defaultValue = "100") Integer limit,
            Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        List<AiChatMessage> recent = messageMapper.selectList(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getUserId, userId)
                        .orderByDesc(AiChatMessage::getCreatedAt)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 500))));
        // 倒序取出最近 N 条后翻转为正序
        Collections.reverse(recent);
        return R.ok(recent);
    }

    @Operation(summary = "保存一条 AI 消息")
    @PostMapping("/messages")
    public R<AiChatMessage> save(@RequestBody AiChatMessage message, Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        // 用户与 ID 由服务端控制，防止伪造/覆盖
        message.setUserId(userId);
        message.setId(null);
        if (message.getRole() == null || message.getRole().isBlank()) {
            message.setRole("user");
        }
        if (message.getKind() == null || message.getKind().isBlank()) {
            message.setKind("CHAT");
        }
        if (message.getContent() == null || message.getContent().isBlank()) {
            return R.fail("消息内容不能为空");
        }
        // 超长内容截断，防止单条记录过大
        if (message.getContent().length() > 20000) {
            message.setContent(message.getContent().substring(0, 20000));
        }
        messageMapper.insert(message);
        return R.ok(message);
    }

    @Operation(summary = "清空我的 AI 消息历史")
    @DeleteMapping("/history")
    public R<Void> clear(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        messageMapper.delete(
                new LambdaQueryWrapper<AiChatMessage>().eq(AiChatMessage::getUserId, userId));
        return R.ok();
    }
}
