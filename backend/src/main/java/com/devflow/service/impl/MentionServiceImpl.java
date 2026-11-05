package com.devflow.service.impl;

import com.devflow.entity.ProjectMember;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.service.MentionService;
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
 * 通用 @提及实现：匹配项目成员的用户名或真实姓名（大小写不敏感）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MentionServiceImpl implements MentionService {

    private final ProjectMemberMapper projectMemberMapper;
    private final NotificationService notificationService;

    /** @用户名 / @真实姓名（支持中英文） */
    private static final Pattern MENTION_PATTERN =
            Pattern.compile("@([A-Za-z0-9_.\\-\u4e00-\u9fa5]+)");

    @Override
    public int notifyMentions(String projectId, String senderId, String content,
                              String sourceTitle, String actionUrl) {
        try {
            if (projectId == null || senderId == null || content == null || !content.contains("@")) {
                return 0;
            }
            Set<String> mentioned = new HashSet<>();
            Matcher matcher = MENTION_PATTERN.matcher(content);
            while (matcher.find()) {
                mentioned.add(matcher.group(1).toLowerCase());
            }
            if (mentioned.isEmpty()) {
                return 0;
            }

            List<ProjectMember> members = projectMemberMapper.selectMembersWithUser(projectId);
            String preview = preview(content);
            int notified = 0;
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
                        member.getUserId(), "MENTION",
                        sourceTitle + " 中提到了你",
                        preview,
                        actionUrl);
                notified++;
                if (username != null) {
                    mentioned.remove(username.toLowerCase());
                }
                if (realName != null) {
                    mentioned.remove(realName.toLowerCase());
                }
            }
            return notified;
        } catch (Exception e) {
            log.warn("@提及处理失败（不影响主流程）: {}", e.getMessage());
            return 0;
        }
    }

    /** 附件消息内容为 JSON（{text, attachments}），提取纯文本并截断 */
    private String preview(String content) {
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
