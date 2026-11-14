package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 测试轮次包含的用例（多对多 + 本轮执行人）。
 */
@Data
@TableName("test_cycle_case")
public class TestCycleCase {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String cycleId;

    private String caseId;

    private String projectId;

    /** 本轮执行人 */
    private String assigneeId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
