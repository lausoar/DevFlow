package com.devflow.service;

import com.devflow.entity.BugComment;

import java.util.List;

/**
 * Bug 评论服务（增删查 + @提及通知）
 */
public interface BugCommentService {

    /** 评论列表（按时间正序，含评论人展示名/头像） */
    List<BugComment> listByBug(String bugId);

    /**
     * 创建评论：通知 Bug 负责人与报告人（排除自己），并解析 @提及通知被提及的项目成员。
     *
     * @return 创建后的评论
     */
    BugComment create(String bugId, String content, String userId);

    /** 删除评论：仅本人或项目管理员以上 */
    void delete(String commentId, String userId);
}
