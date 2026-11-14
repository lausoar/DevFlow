package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建项目请求（白名单字段：企业归属/负责人/邀请码/密钥一律由服务端决定）
 */
@Data
public class CreateProjectRequest {

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称不能超过 100 字")
    private String name;

    @NotBlank(message = "项目标识不能为空")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,15}$",
            message = "项目标识需以字母开头，由 2~16 位字母/数字/下划线/中划线组成")
    private String projectKey;

    @Size(max = 2000, message = "项目描述不能超过 2000 字")
    private String description;

    @Size(max = 500, message = "图标地址过长")
    private String icon;

    @Size(max = 500, message = "仓库地址过长")
    private String gitRepoUrl;
}
