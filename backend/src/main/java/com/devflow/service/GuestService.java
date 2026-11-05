package com.devflow.service;

import com.devflow.entity.Bug;
import com.devflow.entity.ProjectFeedback;

import java.util.List;
import java.util.Map;

/**
 * 游客分享项目服务（链接生成/撤销、游客提交、反馈处理）
 */
public interface GuestService {

    // ===== 管理端（站内，OWNER/MANAGER） =====

    /** 生成游客链接，返回 {guestId, token}（token 明文仅此一次） */
    Map<String, String> generateLink(String projectId, Integer expiresInDays, String operatorId);

    /** 游客链接列表（脱敏，不含 token） */
    List<Map<String, Object>> listLinks(String projectId);

    /** 撤销游客链接 */
    void revokeLink(String projectId, String guestId, String operatorId);

    /** 确认游客提交的 Bug（pending → 正式） */
    void confirmGuestBug(String bugId, String operatorId);

    // ===== 反馈（站内） =====

    List<ProjectFeedback> listFeedback(String projectId);

    ProjectFeedback updateFeedbackStatus(String feedbackId, String status, String operatorId);

    /** 将建议转化为 Bug（转化后建议置 CONVERTED） */
    Bug convertFeedbackToBug(String feedbackId, String operatorId);

    // ===== 游客端（免登录） =====

    Map<String, Object> guestProjectInfo(String token);

    Bug submitGuestBug(String token, String title, String description, String environment,
                       String nickname, String email, String ip);

    ProjectFeedback submitGuestFeedback(String token, String type, String title, String content,
                                        String nickname, String email, String ip);

    Map<String, Object> guestSubmissions(String token);
}
