package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("task_status_event")
public class TaskStatusEvent {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String taskId;
    private String projectId;
    private String fromStatus;
    private String toStatus;
    private String fromCategory;
    private String toCategory;
    private String actorId;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
