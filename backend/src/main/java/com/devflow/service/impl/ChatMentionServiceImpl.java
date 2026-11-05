package com.devflow.service.impl;

import com.devflow.entity.ChatChannel;
import com.devflow.entity.ProjectMember;
import com.devflow.mapper.ChatChannelMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.service.ChatMentionService;
import com.devflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 聊天 @提及实现。
 * 匹配规则：@+用户名（大小写不敏感，用户名为登录名）；
 * 群成员 = 频道所属项目的成员；已在线也不额外提醒，统一走通知中心。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMentionServiceImpl implements ChatMentionService {

    private final ChatChannelMapper channelMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final NotificationService notificationService;

    /** @用户名（登录名为字母数字下划线点横线） */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_.\\-\u4e00-\u9fa5]+)");

    @Override
    public void processMentions(String channelId, String senderId, String content) {
        try {
            if (channelId == null || senderId == null || content == null || !content.contains("@")) {
                return;
            }
            ChatChannel channel = channelMapper.selectById(channelId);
            if (channel == null || !"PROJECT".equals(channel.getType())) {
                return;
            }

            // 提取 @ 的名字集合
            Set<String> mentioned = new HashSet<>();
            Matcher matcher = MENTION_PATTERN.matcher(content);
            while (matcher.find()) {
                mentioned.add(matcher.group(1).toLowerCase());
            }
            if (mentioned.isEmpty()) {
                return;
            }

            List<ProjectMember> members = projectMemberMapper.selectMembersWithUser(channel.getProjectId());
            String preview = buildPreview(content);
            String url = "/chat?channel=" + channelId;
            for (ProjectMember member : members) {
                if (member.getUserId().equals(senderId)) {
                    continue;
                }
                String username = member.getUsername();
                String realName = member.getRealName();
                boolean hit = (username != null && mentioned.contains(username.toLowerCase()))
                        || (realName != null && mentioned.contains(realName.toLowerCase()));
                if (!hit) {
                    continue;
                }
                notificationService.createNotification(
                        member.getUserId(),
                        "MENTION",
                        channel.getName() + " 中提到了你",
                        preview,
                        url);
                // 同一个人在一条消息里被 @ 多次只发一条
                if (username != null) mentioned.remove(username.toLowerCase());
                if (realName != null) mentioned.remove(realName.toLowerCase());
            }
        } catch (Exception e) {
            log.warn("@提及处理失败（不影响消息）: {}", e.getMessage());
        }
    }

    /** 附件消息内容为 JSON（{text, attachments}），提取纯文本并截断 */
    private String buildPreview(String content) {
        String text = content;
        if (text.startsWith("{")) {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(text);
            if (m.find()) {
                text = m.group(1).replace("\\n", " ");
            }
        }
        text = text.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "[图片]");
        return text.length() > 100 ? text.substring(0, 100) + "…" : text;
    }
}
