package com.devflow.dto.request;

import lombok.Data;

/**
 * 更新部门请求（均可选，传了才更新；parentId 传空串表示移动为顶级部门）
 */
@Data
public class UpdateDepartmentRequest {

    /** 部门名称（可选） */
    private String name;

    /** 上级部门 ID（空串/null = 移动为顶级部门） */
    private String parentId;
}
