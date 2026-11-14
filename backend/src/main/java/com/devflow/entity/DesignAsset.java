package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设计稿（外部设计工具的指针）。
 *
 * 平台不自研画布：只存 provider + fileKey + nodeId + 预览链接，
 * 以及版本快照与评审状态。画布、标注、原型交互交给 Figma / 蓝湖 / MasterGo。
 */
@Data
@TableName("design_asset")
public class DesignAsset {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String projectId;

    /** 关联需求（可空） */
    private String requirementId;

    /** 关联任务（可空） */
    private String taskId;

    /** 来源: FIGMA / LANHU / MASTERGO / OTHER */
    private String provider;

    private String title;

    /** 外部文件标识（如 Figma fileKey） */
    private String fileKey;

    /** 外部节点标识（如 Figma node-id） */
    private String nodeId;

    /** 嵌入/预览链接 */
    private String embedUrl;

    private String thumbnailUrl;

    /** 是否该需求的主设计稿（同一需求至多一张） */
    private Integer isPrimary;

    /** 评审状态: DESIGNING / IN_REVIEW / CHANGES_REQUESTED / APPROVED */
    private String status;

    private Integer currentVersion;

    private Integer sort;

    private String createdBy;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public static final String STATUS_DESIGNING = "DESIGNING";
    public static final String STATUS_IN_REVIEW = "IN_REVIEW";
    public static final String STATUS_CHANGES_REQUESTED = "CHANGES_REQUESTED";
    public static final String STATUS_APPROVED = "APPROVED";

    /** 状态推进是否合法（打回后可复审，APPROVED 后仍可因改稿回到 DESIGNING） */
    public static boolean canTransition(String from, String to) {
        if (from == null) {
            return STATUS_DESIGNING.equals(to);
        }
        return switch (from) {
            case STATUS_DESIGNING -> STATUS_IN_REVIEW.equals(to);
            case STATUS_IN_REVIEW -> STATUS_APPROVED.equals(to) || STATUS_CHANGES_REQUESTED.equals(to);
            case STATUS_CHANGES_REQUESTED -> STATUS_IN_REVIEW.equals(to) || STATUS_DESIGNING.equals(to);
            case STATUS_APPROVED -> STATUS_DESIGNING.equals(to);
            default -> false;
        };
    }
}
