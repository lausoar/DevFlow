package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("deployment")
public class Deployment {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String projectId;
    private String environmentId;
    /** 部署的版本（project_release.id） */
    private String releaseId;
    /** 版本号快照：版本被删也不丢历史 */
    private String version;
    private String commitSha;
    /** 本版本首个提交时间（变更前置时间起点） */
    private LocalDateTime firstCommitAt;
    /** PENDING_APPROVAL/APPROVED/DEPLOYING/SUCCEEDED/FAILED/ROLLED_BACK/CANCELLED */
    private String status;
    private String triggeredBy;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String approvalNote;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    /** 回滚目标部署记录 */
    private String rollbackOf;
    private String note;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
