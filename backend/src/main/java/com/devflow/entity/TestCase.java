package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 测试用例（步骤以 JSON 数组存储：[{"action":"操作","expected":"预期"}]）
 */
@Data
@TableName("test_case")
public class TestCase {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 项目 ID */
    private String projectId;

    /** 用例标题 */
    private String title;

    /** 前置条件 */
    private String precondition;

    /** 步骤 JSON 数组字符串 [{"action","expected"}] */
    private String steps;

    /** 预期结果（整体） */
    private String expectedResult;

    /** 优先级: HIGH / MEDIUM / LOW */
    private String priority;

    /** 关联需求（可空） */
    private String requirementId;

    /** 创建人 */
    private String creatorId;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
