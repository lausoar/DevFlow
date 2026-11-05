package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.EnterpriseRoles;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.BugComment;
import com.devflow.entity.User;
import com.devflow.mapper.BugCommentMapper;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.BugCommentService;
import com.devflow.service.MentionService;
import com.devflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Bug 评论实现：评论后通知 Bug 负责人/报告人，并解析 @提及通知项目成员。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BugCommentServiceImpl implements BugCommentService {

    private final BugCommentMapper bugCommentMapper;
    private final BugMapper bugMapper;
    private final UserMapper userMapper;
    private final MentionService mentionService;
    private final NotificationService notificationService;

    @Override
    public List<BugComment> listByBug(String bugId) {
        return bugCommentMapper.selectByBug(bugId);
    }

    @Override
    public BugComment create(String bugId, String content, String userId) {
        Bug bug = bugMapper.selectById(bugId);
        if (bug == null) {
            throw new BusinessException(404, "Bug 不存在");
        }
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(400, "评论内容不能为空");
        }
        if (content.length() > 2000) {
            throw new BusinessException(400, "评论不能超过 2000 字");
        }

        BugComment comment = new BugComment();
        comment.setBugId(bugId);
        comment.setProjectId(bug.getProjectId());
        comment.setUserId(userId);
        comment.setContent(content.trim());
        bugCommentMapper.insert(comment);

        // 通知 Bug 负责人与报告人（排除自己）
        Set<String> notifyUsers = new LinkedHashSet<>();
        if (StringUtils.hasText(bug.getAssigneeId()) && !bug.getAssigneeId().equals(userId)) {
            notifyUsers.add(bug.getAssigneeId());
        }
        if (StringUtils.hasText(bug.getReporterId()) && !bug.getReporterId().equals(userId)) {
            notifyUsers.add(bug.getReporterId());
        }
        for (String uid : notifyUsers) {
            notificationService.createNotification(
                    uid, "COMMENT",
                    "Bug「" + bug.getTitle() + "」有新评论",
                    preview(content),
                    "/projects/" + bug.getProjectId() + "?tab=bugs");
        }

        // @提及：评论里 @ 项目成员（用户名/真实姓名）额外通知
        mentionService.notifyMentions(
                bug.getProjectId(), userId, content,
                "Bug「" + bug.getTitle() + "」的评论",
                "/projects/" + bug.getProjectId() + "?tab=bugs");

        log.debug("Bug 评论已创建 bug={} user={} 通知负责人/报告人 {} 人", bugId, userId, notifyUsers.size());
        return comment;
    }

    @Override
    public void delete(String commentId, String userId) {
        BugComment comment = bugCommentMapper.selectById(commentId);
        if (comment == null || Integer.valueOf(1).equals(comment.getDeleted())) {
            throw new BusinessException(404, "评论不存在");
        }
        User operator = userMapper.selectById(userId);
        boolean isAdmin = operator != null
                && EnterpriseRoles.level(operator.getRole()) >= EnterpriseRoles.level(EnterpriseRoles.ADMIN)
                && comment.getProjectId() != null
                && comment.getProjectId().equals(operator.getEnterpriseId());
        if (!comment.getUserId().equals(userId) && !isAdmin) {
            throw new BusinessException(403, "只能删除自己的评论");
        }
        bugCommentMapper.deleteById(commentId);
    }

    private String preview(String content) {
        return content.length() > 100 ? content.substring(0, 100) + "…" : content;
    }
}
