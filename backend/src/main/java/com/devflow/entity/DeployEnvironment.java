package com.devflow.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("deploy_environment")
public class DeployEnvironment {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String projectId;
    private String name;
    /** DEV / TEST / STAGING / PROD */
    private String type;
    /** 部署是否需要审批 */
    private Integer requiresApproval;
    /** 可审批的项目角色（逗号分隔，空=OWNER/MANAGER） */
    private String approvalRoles;
    private String url;
    private Integer sort;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
