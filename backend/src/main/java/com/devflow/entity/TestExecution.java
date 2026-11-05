package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 测试执行记录
 */
@Data
@TableName("test_execution")
public class TestExecution {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 用例 ID */
    private String caseId;

    /** 项目 ID */
    private String projectId;

    /** 结果: PASS / FAIL / BLOCKED / SKIP */
    private String result;

    /** 执行备注 */
    private String note;

    /** 关联缺陷（失败转缺陷时回填） */
    private String bugId;

    /** 执行人 */
    private String executorId;

    /** 执行人展示名/头像（联查填充，非表字段） */
    @TableField(exist = false)
    private String executorName;

    /** 关联缺陷标题（联查填充，非表字段） */
    @TableField(exist = false)
    private String bugTitle;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
