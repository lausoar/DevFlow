package com.devflow.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新项目请求（白名单字段）。
 * 不允许通过本接口改写 enterpriseId / ownerId / inviteCode / gitWebhookSecret / deleted。
 */
@Data
public class UpdateProjectRequest {

    @Size(max = 100, message = "项目名称不能超过 100 字")
    private String name;

    @Size(max = 16, message = "项目标识不能超过 16 位")
    private String projectKey;

    @Size(max = 2000, message = "项目描述不能超过 2000 字")
    private String description;

    @Size(max = 500, message = "图标地址过长")
    private String icon;

    @Size(max = 500, message = "仓库地址过长")
    private String gitRepoUrl;

    /** ACTIVE / ARCHIVED / PLANNING（服务端再做白名单校验） */
    private String status;
}
