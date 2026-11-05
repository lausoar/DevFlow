package com.devflow.service;

/**
 * 通用 @提及解析：从文本中解析 @用户名 / @真实姓名，
 * 给匹配到的项目成员发 MENTION 通知。处理失败静默兜底。
 */
public interface MentionService {

    /**
     * @param projectId  项目 ID（成员范围）
     * @param senderId   发送者（不给自己发）
     * @param content    文本内容
     * @param sourceTitle 通知标题前缀，如「测试项目 - 项目群」或「Bug「xxx」的评论」
     * @param actionUrl  通知点击跳转地址
     * @return 被通知的人数
     */
    int notifyMentions(String projectId, String senderId, String content,
                       String sourceTitle, String actionUrl);
}
