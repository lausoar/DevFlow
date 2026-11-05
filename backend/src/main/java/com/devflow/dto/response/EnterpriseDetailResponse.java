package com.devflow.dto.response;

import com.devflow.entity.Department;
import com.devflow.entity.Enterprise;
import com.devflow.entity.User;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 企业详情响应（含成员和部门树）
 */
@Data
@Builder
public class EnterpriseDetailResponse {

    private Enterprise enterprise;
    private List<User> members;
    private List<DepartmentTree> departments;

    @Data
    @Builder
    public static class DepartmentTree {
        private String id;
        private String name;
        private String parentId;
        private String leaderId;
        private Integer memberCount;
        private List<User> members;
        private List<DepartmentTree> children;
    }
}
