package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("design_walkthrough_item")
public class DesignWalkthroughItem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String walkthroughId;
    private String title;
    /** PENDING / PASS / FAIL / NA */
    private String result;
    private String note;
    private String screenshotUrl;
    private String bugId;
    private Integer sort;
    private String checkedBy;
    private LocalDateTime checkedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
