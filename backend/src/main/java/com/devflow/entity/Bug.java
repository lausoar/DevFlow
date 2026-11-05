package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Bug 实体
 */
@Data
@TableName("bug")
public class Bug {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String title;
    private String description;
    private String projectId;
    private String assigneeId;
    private String reporterId;

    /** 报告人类型: USER / GUEST */
    private String reporterType;

    /** 游客 ID（reporter_type=GUEST 时） */
    private String guestId;

    /** 游客昵称 */
    private String guestNickname;

    /** 游客提交待确认: 0-否 1-待确认 */
    private Integer pending;

    /** 状态: NEW / ASSIGNED / DOING / RESOLVED / CLOSED */
    private String status;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    /** 严重程度: BLOCKER / CRITICAL / MAJOR / MINOR / TRIVIAL */
    private String severity;

    private String environment;
    private String screenshots;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
