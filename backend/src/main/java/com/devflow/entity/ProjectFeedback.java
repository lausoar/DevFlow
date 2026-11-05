package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 项目建议反馈（站内成员或游客提交，可转化为 Bug）
 */
@Data
@TableName("project_feedback")
public class ProjectFeedback {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 项目 ID */
    private String projectId;

    /** 类型: SUGGESTION / IMPROVEMENT / OTHER */
    private String type;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 提交者类型: USER / GUEST */
    private String submitterType;

    /** 站内用户 ID（submitter_type=USER 时） */
    private String submitterUserId;

    /** 游客 ID（submitter_type=GUEST 时） */
    private String guestId;

    /** 游客昵称 */
    private String guestNickname;

    /** 状态: OPEN / ACCEPTED / REJECTED / CONVERTED */
    private String status;

    /** 转化后的 Bug ID */
    private String convertedBugId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
