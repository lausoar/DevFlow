package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 文件实体
 */
@Data
@TableName("file_item")
public class FileItem {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;
    private Long size;
    private String mimeType;
    private String url;
    private String thumbnailUrl;
    private String uploaderId;
    private String projectId;
    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
