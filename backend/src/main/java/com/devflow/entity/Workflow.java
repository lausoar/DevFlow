package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 工作流定义（任务/缺陷/需求共用一套机制，按 issue_type 区分）
 *
 * project_id 为 NULL 表示全局默认模板；项目自定义时复制一份该项目的快照，
 * 之后只影响本项目 —— 与权限矩阵的企业快照是同一套思路。
 */
@Data
@TableName("workflow")
public class Workflow {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属项目；NULL = 全局默认模板 */
    private String projectId;

    private String name;

    /** 适用问题类型: TASK / BUG / REQUIREMENT */
    private String issueType;

    /** 是否该项目该类型的默认工作流 */
    private Integer isDefault;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
