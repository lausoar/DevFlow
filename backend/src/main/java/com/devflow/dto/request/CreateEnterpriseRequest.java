package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建企业请求
 */
@Data
public class CreateEnterpriseRequest {

    @NotBlank(message = "企业名称不能为空")
    @Size(min = 2, max = 50, message = "企业名称长度 2-50 个字符")
    private String name;

    /** 企业 Logo URL（可选） */
    private String logo;
}
