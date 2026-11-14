package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 保存的筛选器：结构化查询表达式的落点。
 *
 * expression 是受限表达式（不是 SQL），由 QueryExpressionParser 解析成白名单条件，
 * 看板、列表、导出、将来的仪表盘都复用同一份查询。
 */
@Data
@TableName("saved_filter")
public class SavedFilter {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属项目；NULL 表示跨项目 */
    private String projectId;

    private String ownerId;

    private String name;

    /** 查询对象: TASK / BUG / REQUIREMENT */
    private String entity;

    /** 结构化查询表达式，如: status IN (TODO, DOING) AND priority = HIGH AND assignee = currentUser() */
    private String expression;

    /** 是否共享给项目成员 */
    private Integer isShared;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
