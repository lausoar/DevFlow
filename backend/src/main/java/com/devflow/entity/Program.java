package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 项目集：企业下的一组项目（产品线/业务线）。
 * 中大型客户多产品线时，需要项目集级的统一视角与跨项目风险清单。
 */
@Data
@TableName("program")
public class Program {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String enterpriseId;
    private String name;
    private String description;
    private String ownerId;

    /** ACTIVE / ARCHIVED */
    private String status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
