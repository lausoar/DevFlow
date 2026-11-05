package com.devflow.service;

import com.devflow.dto.request.CreateDepartmentRequest;
import com.devflow.dto.request.CreateEnterpriseRequest;
import com.devflow.dto.request.InviteMemberRequest;
import com.devflow.dto.request.UpdateEnterpriseRequest;
import com.devflow.dto.response.EnterpriseDetailResponse;
import com.devflow.entity.Department;
import com.devflow.entity.Enterprise;
import com.devflow.entity.User;

import java.util.List;

/**
 * 企业服务接口
 */
public interface EnterpriseService {

    /** 获取用户的企业 */
    Enterprise getUserEnterprise(String userId);

    /** 创建企业 */
    Enterprise createEnterprise(CreateEnterpriseRequest request, String ownerId);

    /** 更新企业信息（仅企业管理员） */
    Enterprise updateEnterprise(String enterpriseId, UpdateEnterpriseRequest request, String operatorId);

    /** 获取企业详情（含成员和部门树） */
    EnterpriseDetailResponse getEnterpriseDetail(String enterpriseId);

    /** 获取企业成员列表 */
    List<User> getMembers(String enterpriseId);

    /** 邀请成员 */
    com.devflow.dto.response.InviteMemberResponse inviteMember(String enterpriseId, InviteMemberRequest request, String inviterId);

    /** 超管重置成员密码：生成随机临时密码并返回（明文仅此一次） */
    String resetMemberPassword(String enterpriseId, String memberId, String operatorId);

    /** 移除成员 */
    void removeMember(String enterpriseId, String memberId, String operatorId);

    /** 解散企业（仅企业管理员）：清退成员、删除部门、逻辑删除企业及其项目 */
    void dissolveEnterprise(String enterpriseId, String operatorId);

    /** 成员自助退出企业：清空企业/部门/项目成员身份；最后一位超管不可退出 */
    void leaveEnterprise(String enterpriseId, String userId);

    /** 更新成员角色（操作者等级须高于目标成员，且新角色等级低于操作者） */
    com.devflow.entity.User updateMemberRole(String enterpriseId, String memberId,
                                              String newRole, String operatorId);

    /** 更新成员部门（departmentId 为空表示移出部门；操作者等级须高于目标成员） */
    com.devflow.entity.User updateMemberDepartment(String enterpriseId, String memberId,
                                                   String departmentId, String operatorId);

    /** 获取企业部门树 */
    List<EnterpriseDetailResponse.DepartmentTree> getDepartmentTree(String enterpriseId);

    /** 创建部门（仅企业管理员/经理） */
    Department createDepartment(String enterpriseId, CreateDepartmentRequest request, String operatorId);

    /** 删除部门（仅企业管理员） */
    void deleteDepartment(String departmentId, String operatorId);

    /** 更新部门（重命名/调整上级，仅企业管理员/经理） */
    com.devflow.entity.Department updateDepartment(String departmentId,
                                                    com.devflow.dto.request.UpdateDepartmentRequest request,
                                                    String operatorId);

    /** 通过邀请码加入企业 */
    Enterprise joinByInviteCode(String inviteCode, String userId);
}
