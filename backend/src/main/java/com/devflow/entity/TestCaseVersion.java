package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 测试用例版本快照：编辑用例时把上一版存下来。
 * 执行记录带 case_version，失败时才能回答"当时按哪一版步骤执行的"。
 */
@Data
@TableName("test_case_version")
public class TestCaseVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String caseId;

    private Integer version;

    private String title;

    private String precondition;

    private String steps;

    private String expectedResult;

    private String editorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
