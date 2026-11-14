package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("design_walkthrough")
public class DesignWalkthrough {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String projectId;
    private String requirementId;
    private String designAssetId;
    private Integer designVersion;
    private String title;
    /** OPEN / CLOSED */
    private String status;
    private String createdBy;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
