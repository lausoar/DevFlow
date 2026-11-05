package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.devflow.common.EnterpriseRoles;
import com.devflow.common.exception.BusinessException;
import com.devflow.dto.request.CreateDepartmentRequest;
import com.devflow.dto.request.CreateEnterpriseRequest;
import com.devflow.dto.request.InviteMemberRequest;
import com.devflow.dto.request.UpdateEnterpriseRequest;
import com.devflow.dto.response.EnterpriseDetailResponse;
import com.devflow.entity.Department;
import com.devflow.entity.DepartmentMember;
import com.devflow.entity.Enterprise;
import com.devflow.entity.Project;
import com.devflow.entity.ProjectMember;
import com.devflow.entity.User;
import com.devflow.mapper.DepartmentMapper;
import com.devflow.mapper.DepartmentMemberMapper;
import com.devflow.mapper.EnterpriseMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.ProjectMemberMapper;
import com.devflow.mapper.UserMapper;
import com.devflow.service.AuditService;
import com.devflow.service.EnterpriseService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 企业服务实现
 */
@Service
@RequiredArgsConstructor
public class EnterpriseServiceImpl implements EnterpriseService {

    private final EnterpriseMapper enterpriseMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final DepartmentMemberMapper departmentMemberMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Override
    public Enterprise getUserEnterprise(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getEnterpriseId() == null) {
            throw new BusinessException(404, "用户尚未加入企业");
        }
        return enterpriseMapper.selectById(user.getEnterpriseId());
    }

    @Override
    @Transactional
    public Enterprise createEnterprise(CreateEnterpriseRequest request, String ownerId) {
        Enterprise enterprise = new Enterprise();
        enterprise.setName(request.getName());
        enterprise.setLogo(request.getLogo());
        enterprise.setOwnerId(ownerId);
        enterprise.setInviteCode(generateInviteCode());
        enterprise.setDeleted(0);
        enterpriseMapper.insert(enterprise);

        // 更新用户的 enterpriseId（创建者成为超级管理员）
        User user = userMapper.selectById(ownerId);
        user.setEnterpriseId(enterprise.getId());
        user.setRole(EnterpriseRoles.SUPER_ADMIN);
        userMapper.updateById(user);

        auditService.log(ownerId, enterprise.getId(), null, "enterprise.create",
                "enterprise", enterprise.getId(), "创建企业: " + enterprise.getName());

        return enterprise;
    }

    @Override
    @Transactional
    public Enterprise updateEnterprise(String enterpriseId, UpdateEnterpriseRequest request, String operatorId) {
        Enterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null) {
            throw new BusinessException(404, "企业不存在");
        }

        // 仅超级管理员可修改企业信息/邀请码
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !enterpriseId.equals(operator.getEnterpriseId())
                || !EnterpriseRoles.SUPER_ADMIN.equals(operator.getRole())) {
            throw new BusinessException(403, "仅超级管理员可以修改企业设置");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            enterprise.setName(request.getName());
        }
        if (request.getLogo() != null) {
            enterprise.setLogo(request.getLogo());
        }
        if (request.getWecomWebhook() != null) {
            // 空串 = 清除配置
            enterprise.setWecomWebhook(request.getWecomWebhook().trim());
        }
        if (Boolean.TRUE.equals(request.getRegenerateInviteCode())) {
            enterprise.setInviteCode(generateInviteCode());
        }

        enterpriseMapper.updateById(enterprise);
        return enterprise;
    }

    /** 批量填充成员的当前部门ID（一人一部门，取其最新的部门关联） */
    private void fillDepartmentIds(List<User> members) {
        if (members == null || members.isEmpty()) {
            return;
        }
        List<String> userIds = members.stream().map(User::getId).collect(java.util.stream.Collectors.toList());
        List<DepartmentMember> relations = departmentMemberMapper.selectList(
                new LambdaQueryWrapper<DepartmentMember>()
                        .in(DepartmentMember::getUserId, userIds)
                        .orderByDesc(DepartmentMember::getCreatedAt));
        Map<String, String> userDept = new HashMap<>();
        for (DepartmentMember rel : relations) {
            userDept.putIfAbsent(rel.getUserId(), rel.getDepartmentId());
        }
        members.forEach(m -> m.setDepartmentId(userDept.get(m.getId())));
    }

    @Override
    public EnterpriseDetailResponse getEnterpriseDetail(String enterpriseId) {
        Enterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null) {
            throw new BusinessException(404, "企业不存在");
        }

        // 查询成员
        List<User> members = userMapper.selectList(
            new LambdaQueryWrapper<User>()
                .eq(User::getEnterpriseId, enterpriseId)
                .eq(User::getStatus, "ACTIVE")
        );
        members.forEach(m -> m.setPassword(null));
        fillDepartmentIds(members);

        // 查询部门树
        List<EnterpriseDetailResponse.DepartmentTree> deptTree = buildDepartmentTree(enterpriseId);

        return EnterpriseDetailResponse.builder()
                .enterprise(enterprise)
                .members(members)
                .departments(deptTree)
                .build();
    }

    @Override
    public List<User> getMembers(String enterpriseId) {
        List<User> members = userMapper.selectList(
            new LambdaQueryWrapper<User>()
                .eq(User::getEnterpriseId, enterpriseId)
                .eq(User::getStatus, "ACTIVE")
                .orderByDesc(User::getCreatedAt)
        );
        members.forEach(m -> m.setPassword(null));
        fillDepartmentIds(members);
        return members;
    }

    @Override
    @Transactional
    public com.devflow.dto.response.InviteMemberResponse inviteMember(String enterpriseId, InviteMemberRequest request, String inviterId) {
        // 检查企业是否存在
        Enterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null) {
            throw new BusinessException(404, "企业不存在");
        }

        // 检查邀请人权限（经理及以上）与目标角色合法性
        User inviter = userMapper.selectById(inviterId);
        if (inviter == null || !enterpriseId.equals(inviter.getEnterpriseId())
                || !EnterpriseRoles.canManage(inviter.getRole())) {
            throw new BusinessException(403, "无权邀请成员");
        }
        String initialRole = request.getRole() != null ? request.getRole() : EnterpriseRoles.MEMBER;
        if (!EnterpriseRoles.canGrant(inviter.getRole(), initialRole)) {
            throw new BusinessException(403, "不能授予不低于自己职级的角色");
        }

        // 检查邮箱是否已注册
        User existingUser = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())
        );

        if (existingUser != null) {
            // 已注册 -> 直接加入企业
            if (existingUser.getEnterpriseId() != null) {
                throw new BusinessException(400, "该用户已加入其他企业");
            }
            existingUser.setEnterpriseId(enterpriseId);
            existingUser.setRole(initialRole);
            userMapper.updateById(existingUser);
            auditService.log(inviterId, enterpriseId, null, "member.invite",
                    "user", existingUser.getId(), "邀请已有用户 " + request.getEmail() + " 加入企业，职级: " + initialRole);
            existingUser.setPassword(null);
            // 已注册用户保持原密码，initialPassword 置空（无需告知新密码）
            com.devflow.dto.response.InviteMemberResponse existingResponse =
                    new com.devflow.dto.response.InviteMemberResponse();
            existingResponse.setUser(existingUser);
            existingResponse.setInitialPassword(null);
            return existingResponse;
        }

        // 未注册 -> 创建临时账号
        String username = request.getEmail().split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 4);
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setEmail(request.getEmail());
        if (request.getRealName() != null && !request.getRealName().isBlank()) {
            newUser.setRealName(request.getRealName().trim());
        }
        String tempPassword = generateTempPassword();
        newUser.setPassword(passwordEncoder.encode(tempPassword)); // 随机临时密码，邀请成功后一次性返回给邀请人
        newUser.setRole(initialRole);
        newUser.setStatus("ACTIVE");
        newUser.setEnterpriseId(enterpriseId);
        newUser.setDeleted(0);
        userMapper.insert(newUser);

        // 分配到部门
        if (request.getDepartmentId() != null) {
            DepartmentMember dm = new DepartmentMember();
            dm.setDepartmentId(request.getDepartmentId());
            dm.setUserId(newUser.getId());
            dm.setEnterpriseId(enterpriseId);
            departmentMemberMapper.insert(dm);
        }

        auditService.log(inviterId, enterpriseId, null, "member.invite",
                "user", newUser.getId(), "邀请新成员 " + request.getEmail() + " 加入企业，职级: " + initialRole);

        newUser.setPassword(null);
        com.devflow.dto.response.InviteMemberResponse response =
            new com.devflow.dto.response.InviteMemberResponse();
        response.setUser(newUser);
        response.setInitialPassword(tempPassword);
        return response;
    }

    @Override
    @Transactional
    public void removeMember(String enterpriseId, String memberId, String operatorId) {
        // 不允许移除自己
        if (memberId.equals(operatorId)) {
            throw new BusinessException(400, "不能移除自己");
        }

        // 校验操作人权限（经理及以上）与目标职级（只能移除低于自己的成员）
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !enterpriseId.equals(operator.getEnterpriseId())
                || !EnterpriseRoles.canManage(operator.getRole())) {
            throw new BusinessException(403, "无权移除成员");
        }

        User member = userMapper.selectById(memberId);
        if (member == null || !enterpriseId.equals(member.getEnterpriseId())) {
            throw new BusinessException(404, "成员不存在");
        }
        if (!EnterpriseRoles.canOperate(operator.getRole(), member.getRole())) {
            throw new BusinessException(403, "不能移除职级不低于自己的成员");
        }

        // updateById 跳过 null 字段，必须显式 set 才能把 enterprise_id 清空
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, memberId)
                .set(User::getEnterpriseId, null)
                .set(User::getRole, EnterpriseRoles.MEMBER));

        // 清除部门关联
        departmentMemberMapper.delete(
            new LambdaQueryWrapper<DepartmentMember>()
                .eq(DepartmentMember::getUserId, memberId)
        );

        auditService.log(operatorId, enterpriseId, null, "member.remove",
                "user", memberId, "移除成员 " + member.displayName() + "（原职级: " + member.getRole() + "）");
    }

    @Override
    public User updateMemberRole(String enterpriseId, String memberId,
                                 String newRole, String operatorId) {
        if (!EnterpriseRoles.isValid(newRole)) {
            throw new BusinessException(400, "非法角色: " + newRole);
        }

        User operator = userMapper.selectById(operatorId);
        if (operator == null || !enterpriseId.equals(operator.getEnterpriseId())
                || !EnterpriseRoles.canManage(operator.getRole())) {
            throw new BusinessException(403, "无权修改成员角色");
        }

        User member = userMapper.selectById(memberId);
        if (member == null || !enterpriseId.equals(member.getEnterpriseId())) {
            throw new BusinessException(404, "成员不存在");
        }
        // 只能调整低于自己的成员；新角色必须低于操作者（防越权提权）
        if (!EnterpriseRoles.canOperate(operator.getRole(), member.getRole())) {
            throw new BusinessException(403, "不能修改职级不低于自己的成员");
        }
        if (!EnterpriseRoles.canGrant(operator.getRole(), newRole)) {
            throw new BusinessException(403, "不能授予不低于自己职级的角色");
        }

        String oldRole = member.getRole();
        member.setRole(newRole);
        userMapper.updateById(member);
        auditService.log(operatorId, enterpriseId, null, "member.role",
                "user", memberId, "调整成员 " + member.displayName() + " 职级: " + oldRole + " → " + newRole);
        member.setPassword(null);
        return member;
    }

    @Override
    public User updateMemberDepartment(String enterpriseId, String memberId,
                                       String departmentId, String operatorId) {
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !enterpriseId.equals(operator.getEnterpriseId())
                || EnterpriseRoles.level(operator.getRole()) < EnterpriseRoles.level(EnterpriseRoles.MANAGER)) {
            throw new BusinessException(403, "无权调整成员部门");
        }

        User member = userMapper.selectById(memberId);
        if (member == null || !enterpriseId.equals(member.getEnterpriseId())) {
            throw new BusinessException(404, "成员不存在");
        }
        if (!EnterpriseRoles.canOperate(operator.getRole(), member.getRole())) {
            throw new BusinessException(403, "不能调整职级不低于自己的成员");
        }

        // 校验目标部门属于本企业
        if (departmentId != null && !departmentId.isBlank()) {
            Department dept = departmentMapper.selectById(departmentId);
            if (dept == null || !enterpriseId.equals(dept.getEnterpriseId())) {
                throw new BusinessException(404, "目标部门不存在");
            }
        }

        // 一人仅归属一个部门：清除旧关联后建立新关联
        departmentMemberMapper.delete(
                new LambdaQueryWrapper<DepartmentMember>()
                        .eq(DepartmentMember::getUserId, memberId)
                        .eq(DepartmentMember::getEnterpriseId, enterpriseId));
        if (departmentId != null && !departmentId.isBlank()) {
            DepartmentMember dm = new DepartmentMember();
            dm.setDepartmentId(departmentId);
            dm.setUserId(memberId);
            dm.setEnterpriseId(enterpriseId);
            departmentMemberMapper.insert(dm);
        }
        auditService.log(operatorId, enterpriseId, null, "member.department",
                "user", memberId, "调整成员 " + member.displayName() + " 部门: " + (departmentId == null || departmentId.isBlank() ? "移出部门" : departmentId));
        member.setPassword(null);
        return member;
    }

    @Override
    @Transactional
    public void dissolveEnterprise(String enterpriseId, String operatorId) {
        Enterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || (enterprise.getDeleted() != null && enterprise.getDeleted() == 1)) {
            throw new BusinessException(404, "企业不存在");
        }

        // 仅超级管理员可解散
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !enterpriseId.equals(operator.getEnterpriseId())
                || !EnterpriseRoles.SUPER_ADMIN.equals(operator.getRole())) {
            throw new BusinessException(403, "仅超级管理员可以解散企业");
        }

        // 1. 清退所有成员（enterprise_id 置空、角色降为 MEMBER）
        // 注意：updateById 会跳过 null 字段，必须用 UpdateWrapper 显式 set null
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getEnterpriseId, enterpriseId)
                .set(User::getEnterpriseId, null)
                .set(User::getRole, "MEMBER"));

        // 2. 删除部门及其成员关联
        List<Department> departments = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().eq(Department::getEnterpriseId, enterpriseId));
        for (Department dept : departments) {
            departmentMemberMapper.delete(
                    new LambdaQueryWrapper<DepartmentMember>()
                            .eq(DepartmentMember::getDepartmentId, dept.getId()));
            departmentMapper.deleteById(dept.getId());
        }

        // 3. 逻辑删除企业下所有项目
        projectMapper.update(null, new LambdaUpdateWrapper<Project>()
                .eq(Project::getEnterpriseId, enterpriseId)
                .set(Project::getDeleted, 1));

        // 4. 逻辑删除企业本身（逻辑删除字段不能用 updateById 更新，需显式 set）
        enterpriseMapper.update(null, new LambdaUpdateWrapper<Enterprise>()
                .eq(Enterprise::getId, enterpriseId)
                .set(Enterprise::getDeleted, 1));

        auditService.log(operatorId, enterpriseId, null, "enterprise.dissolve",
                "enterprise", enterpriseId, "解散企业: " + enterprise.getName());
    }

    @Override
    @Transactional
    public void leaveEnterprise(String enterpriseId, String userId) {
        Enterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || (enterprise.getDeleted() != null && enterprise.getDeleted() == 1)) {
            throw new BusinessException(404, "企业不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null || !enterpriseId.equals(user.getEnterpriseId())) {
            throw new BusinessException(403, "你不是该企业的成员");
        }

        // 最后一位超管不能退出，防止企业失管
        if (EnterpriseRoles.SUPER_ADMIN.equals(user.getRole())) {
            Long superAdmins = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getEnterpriseId, enterpriseId)
                    .eq(User::getRole, EnterpriseRoles.SUPER_ADMIN));
            if (superAdmins <= 1) {
                throw new BusinessException(400, "最后一位超级管理员不能退出，请先转让超管或解散企业");
            }
        }

        // 1. 清退本人（enterprise_id 置空、角色降为 MEMBER；updateById 跳过 null 必须显式 set）
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getEnterpriseId, null)
                .set(User::getRole, "MEMBER"));

        // 2. 移除部门关联
        List<Department> departments = departmentMapper.selectList(
                new LambdaQueryWrapper<Department>().eq(Department::getEnterpriseId, enterpriseId));
        for (Department dept : departments) {
            departmentMemberMapper.delete(new LambdaQueryWrapper<DepartmentMember>()
                    .eq(DepartmentMember::getDepartmentId, dept.getId())
                    .eq(DepartmentMember::getUserId, userId));
        }

        // 3. 移除该企业所有项目中的成员身份
        List<Project> projects = projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getEnterpriseId, enterpriseId)
                .eq(Project::getDeleted, 0));
        for (Project project : projects) {
            projectMemberMapper.delete(new LambdaQueryWrapper<ProjectMember>()
                    .eq(ProjectMember::getProjectId, project.getId())
                    .eq(ProjectMember::getUserId, userId));
        }

        auditService.log(userId, enterpriseId, null, "enterprise.leave",
                "user", userId, "退出企业: " + enterprise.getName());
    }

    @Override
    public List<EnterpriseDetailResponse.DepartmentTree> getDepartmentTree(String enterpriseId) {
        return buildDepartmentTree(enterpriseId);
    }

    @Override
    @Transactional
    public Department createDepartment(String enterpriseId, CreateDepartmentRequest request, String operatorId) {
        // 校验操作人权限（仅超级管理员/管理员可新建部门）
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !enterpriseId.equals(operator.getEnterpriseId())
                || EnterpriseRoles.level(operator.getRole()) < EnterpriseRoles.level(EnterpriseRoles.ADMIN)) {
            throw new BusinessException(403, "仅超级管理员和管理员可以创建部门");
        }

        // 检查同级是否重名
        LambdaQueryWrapper<Department> wrapper = new LambdaQueryWrapper<Department>()
            .eq(Department::getEnterpriseId, enterpriseId)
            .eq(Department::getName, request.getName());

        if (request.getParentId() != null) {
            wrapper.eq(Department::getParentId, request.getParentId());
        } else {
            wrapper.isNull(Department::getParentId);
        }

        if (departmentMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(400, "同级部门名称已存在");
        }

        Department dept = new Department();
        dept.setName(request.getName());
        dept.setEnterpriseId(enterpriseId);
        dept.setParentId(request.getParentId());
        dept.setSort(request.getSort() != null ? request.getSort() : 0);
        dept.setDeleted(0);
        departmentMapper.insert(dept);

        return dept;
    }

    @Override
    @Transactional
    public Department updateDepartment(String departmentId,
                                       com.devflow.dto.request.UpdateDepartmentRequest request,
                                       String operatorId) {
        Department dept = departmentMapper.selectById(departmentId);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在");
        }

        // 校验操作人权限（超级管理员/管理员）
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !dept.getEnterpriseId().equals(operator.getEnterpriseId())
                || EnterpriseRoles.level(operator.getRole()) < EnterpriseRoles.level(EnterpriseRoles.ADMIN)) {
            throw new BusinessException(403, "仅超级管理员和管理员可以修改部门");
        }

        // 名称：传了就不能为空
        if (request.getName() != null && request.getName().isBlank()) {
            throw new BusinessException(400, "部门名称不能为空");
        }

        // 上级部门：null = 不变；空串 = 移为顶级；否则校验同企业存在
        String newParentId = request.getParentId() == null ? dept.getParentId()
                : (request.getParentId().isBlank() ? null : request.getParentId().trim());
        if (newParentId != null) {
            if (newParentId.equals(departmentId)) {
                throw new BusinessException(400, "不能将部门设为自己的上级");
            }
            Department parent = departmentMapper.selectById(newParentId);
            if (parent == null || !dept.getEnterpriseId().equals(parent.getEnterpriseId())) {
                throw new BusinessException(400, "上级部门不存在");
            }
            // 环检测：沿新上级向上走，不能回到自己
            String cursor = newParentId;
            int depth = 0;
            while (cursor != null && depth < 50) {
                if (cursor.equals(departmentId)) {
                    throw new BusinessException(400, "不能将部门移动到自己的下级部门之下");
                }
                Department p = departmentMapper.selectById(cursor);
                cursor = p == null ? null : p.getParentId();
                depth++;
            }
        }

        // 同级重名校验（新父下，排除自己）
        LambdaQueryWrapper<Department> dup = new LambdaQueryWrapper<Department>()
                .eq(Department::getEnterpriseId, dept.getEnterpriseId())
                .eq(Department::getName, request.getName() != null ? request.getName().trim() : dept.getName())
                .ne(Department::getId, departmentId);
        if (newParentId == null) {
            dup.isNull(Department::getParentId);
        } else {
            dup.eq(Department::getParentId, newParentId);
        }
        if (departmentMapper.selectCount(dup) > 0) {
            throw new BusinessException(400, "同级部门名称已存在");
        }

        // 显式 set（updateById 跳过 null，无法把部门移为顶级）
        LambdaUpdateWrapper<Department> uw = new LambdaUpdateWrapper<Department>()
                .eq(Department::getId, departmentId);
        if (request.getName() != null) {
            uw.set(Department::getName, request.getName().trim());
        }
        uw.set(Department::getParentId, newParentId);
        departmentMapper.update(null, uw);

        auditService.log(operatorId, dept.getEnterpriseId(), null, "department.update",
                "department", departmentId,
                "修改部门「" + dept.getName() + "」"
                        + (request.getName() != null && !request.getName().trim().equals(dept.getName())
                            ? "，新名称: " + request.getName().trim() : ""));

        return departmentMapper.selectById(departmentId);
    }

    @Override
    @Transactional
    public void deleteDepartment(String departmentId, String operatorId) {
        Department dept = departmentMapper.selectById(departmentId);
        if (dept == null) {
            throw new BusinessException(404, "部门不存在");
        }

        // 校验操作人权限（超级管理员/管理员）
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !dept.getEnterpriseId().equals(operator.getEnterpriseId())
                || EnterpriseRoles.level(operator.getRole()) < EnterpriseRoles.level(EnterpriseRoles.ADMIN)) {
            throw new BusinessException(403, "仅超级管理员和管理员可以删除部门");
        }

        // 检查是否有子部门
        Long childCount = departmentMapper.selectCount(
            new LambdaQueryWrapper<Department>().eq(Department::getParentId, departmentId)
        );
        if (childCount > 0) {
            throw new BusinessException(400, "请先删除子部门");
        }

        // 清除成员关联
        departmentMemberMapper.delete(
            new LambdaQueryWrapper<DepartmentMember>().eq(DepartmentMember::getDepartmentId, departmentId)
        );

        departmentMapper.deleteById(departmentId);
    }

    @Override
    @Transactional
    public Enterprise joinByInviteCode(String inviteCode, String userId) {
        Enterprise enterprise = enterpriseMapper.selectOne(
            new LambdaQueryWrapper<Enterprise>().eq(Enterprise::getInviteCode, inviteCode.toUpperCase())
        );
        if (enterprise == null) {
            throw new BusinessException(404, "邀请码无效");
        }

        User user = userMapper.selectById(userId);
        if (user.getEnterpriseId() != null) {
            throw new BusinessException(400, "您已加入企业，请先退出当前企业");
        }

        user.setEnterpriseId(enterprise.getId());
        user.setRole("MEMBER");
        userMapper.updateById(user);

        return enterprise;
    }

    // ========== 私有方法 ==========

    /**
     * 构建部门树
     */
    private List<EnterpriseDetailResponse.DepartmentTree> buildDepartmentTree(String enterpriseId) {
        List<Department> allDepts = departmentMapper.selectList(
            new LambdaQueryWrapper<Department>()
                .eq(Department::getEnterpriseId, enterpriseId)
                .orderByAsc(Department::getSort)
        );

        // 批量查询本企业全部部门成员关联，按部门分组
        List<DepartmentMember> allRelations = departmentMemberMapper.selectList(
            new LambdaQueryWrapper<DepartmentMember>().eq(DepartmentMember::getEnterpriseId, enterpriseId)
        );
        Map<String, List<String>> deptUserIds = new HashMap<>();
        for (DepartmentMember rel : allRelations) {
            deptUserIds.computeIfAbsent(rel.getDepartmentId(), k -> new ArrayList<>()).add(rel.getUserId());
        }
        Map<String, User> userMap = userMapper.selectList(
                new LambdaQueryWrapper<User>().eq(User::getEnterpriseId, enterpriseId))
            .stream().peek(u -> u.setPassword(null))
            .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        Map<String, Integer> memberCountMap = new HashMap<>();
        Map<String, List<User>> deptMembersMap = new HashMap<>();
        for (Department dept : allDepts) {
            List<User> deptUsers = deptUserIds.getOrDefault(dept.getId(), List.of()).stream()
                .map(userMap::get).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toList());
            deptMembersMap.put(dept.getId(), deptUsers);
            memberCountMap.put(dept.getId(), deptUsers.size());
        }

        // 转换为 Tree 节点
        Map<String, EnterpriseDetailResponse.DepartmentTree> nodeMap = new LinkedHashMap<>();
        for (Department dept : allDepts) {
            nodeMap.put(dept.getId(), EnterpriseDetailResponse.DepartmentTree.builder()
                    .id(dept.getId())
                    .name(dept.getName())
                    .parentId(dept.getParentId())
                    .leaderId(dept.getLeaderId())
                    .memberCount(memberCountMap.getOrDefault(dept.getId(), 0))
                    .members(deptMembersMap.getOrDefault(dept.getId(), List.of()))
                    .children(new ArrayList<>())
                    .build());
        }

        // 组装树
        List<EnterpriseDetailResponse.DepartmentTree> roots = new ArrayList<>();
        for (var node : nodeMap.values()) {
            if (node.getParentId() == null) {
                roots.add(node);
            } else {
                var parent = nodeMap.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }

        return roots;
    }

    /**
     * 生成 8 位邀请码
     */
    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    // ============================================
    // 密码管理
    // ============================================

    @Override
    public String resetMemberPassword(String enterpriseId, String memberId, String operatorId) {
        Enterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || (enterprise.getDeleted() != null && enterprise.getDeleted() == 1)) {
            throw new BusinessException(404, "企业不存在");
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null || !enterpriseId.equals(operator.getEnterpriseId())
                || !EnterpriseRoles.SUPER_ADMIN.equals(operator.getRole())) {
            throw new BusinessException(403, "仅企业超级管理员可以重置成员密码");
        }
        User member = userMapper.selectById(memberId);
        if (member == null || !enterpriseId.equals(member.getEnterpriseId())) {
            throw new BusinessException(404, "成员不存在或不属于该企业");
        }

        String tempPassword = generateTempPassword();
        member.setPassword(passwordEncoder.encode(tempPassword));
        userMapper.updateById(member);

        // 审计只记动作，绝不记录密码明文
        auditService.log(operatorId, enterpriseId, null, "member.password.reset",
                "user", memberId, "重置成员「" + member.displayName() + "」的密码");

        return tempPassword;
    }

    /** 生成随机临时密码（10 位，去除易混淆字符 0O1lI） */
    private String generateTempPassword() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}