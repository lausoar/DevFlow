package com.devflow.dto.request;

import lombok.Data;

/**
 * 更新成员部门请求
 * departmentId 为空表示移出当前部门
 */
@Data
public class UpdateMemberDepartmentRequest {

    /** 目标部门ID（为空表示移出部门） */
    private String departmentId;
}
