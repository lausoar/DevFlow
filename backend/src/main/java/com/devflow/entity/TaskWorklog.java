package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 任务工时记录
 */
@Data
@TableName("task_worklog")
public class TaskWorklog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 任务 ID */
    private String taskId;

    /** 登记人 */
    private String userId;

    /** 工时（小时） */
    private java.math.BigDecimal hours;

    /** 工作日期 */
    private LocalDate workDate;

    /** 备注 */
    private String note;

    /** 登记人姓名（联查填充，非表字段） */
    @TableField(exist = false)
    private String userName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
