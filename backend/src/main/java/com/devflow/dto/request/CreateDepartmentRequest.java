package com.devflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建部门请求
 */
@Data
public class CreateDepartmentRequest {

    @NotBlank(message = "部门名称不能为空")
    private String name;

    /** 上级部门 ID（可选，为空则是顶级部门） */
    private String parentId;

    /** 排序号 */
    private Integer sort;
}
