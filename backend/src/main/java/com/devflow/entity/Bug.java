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
    /** 通用更新也必须能显式写入 NULL，以便与其他字段共用一次乐观锁更新。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
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

    /** 乐观锁版本号（乐观锁冲突时 409） */
    @Version
    private Integer version;

    /** 状态：取值来自项目工作流配置（默认 NEW/ASSIGNED/DOING/RESOLVED/REOPENED/CLOSED） */
    private String status;

    /** 优先级: CRITICAL / HIGH / MEDIUM / LOW */
    private String priority;

    /** 严重程度: BLOCKER / CRITICAL / MAJOR / MINOR / TRIVIAL */
    private String severity;

    private String environment;
    private String screenshots;

    /** 修复版本（project_release.id） */
    private String fixVersionId;

    @TableLogic
    private Integer deleted;

    /**
     * 删除时间 / 删除人（deleted=1 时才有值）。
     *
     * 之前只有 deleted 一个布尔标记，回收站只能拿 updated_at 冒充"删除于"——
     * 那条记录删除前如果被改过标题，显示的就是改标题的时间。
     */
    private LocalDateTime deletedAt;
    private String deletedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
