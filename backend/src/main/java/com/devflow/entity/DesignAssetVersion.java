package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设计稿版本快照：开发领取任务时绑定具体版本，而不是"最新"。
 * 没有这个契约，改稿无痕，上线后的视觉偏差无法回溯。
 */
@Data
@TableName("design_asset_version")
public class DesignAssetVersion {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String assetId;

    private Integer version;

    /** 该版本的预览/快照链接 */
    private String snapshotUrl;

    private String note;

    private String editorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
