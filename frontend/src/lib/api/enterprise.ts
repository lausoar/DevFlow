import { request } from './client';
import type { User, Department, InviteMemberResponse } from '@/types';

// ============================================
// 企业 API
// ============================================

export interface Enterprise {
  id: string;
  name: string;
  logo?: string;
  /** 企业微信群机器人 Webhook（空 = 不推送） */
  wecomWebhook?: string;
  inviteCode: string;
  ownerId: string;
  createdAt: string;
}

export interface DepartmentTree {
  id: string;
  name: string;
  parentId?: string;
  leaderId?: string;
  memberCount: number;
    members?: Array<{ id: string; username: string; realName?: string; email: string; role: string; avatar?: string; departmentId?: string }>;
  children: DepartmentTree[];
}

export interface EnterpriseDetail {
  enterprise: Enterprise;
  members: User[];
  departments: DepartmentTree[];
}

export const enterpriseApi = {
  /** 获取当前用户的企业 */
  getCurrent: () =>
    request<Enterprise>('GET', '/enterprises/current'),

  /** 创建企业 */
  create: (data: { name: string; logo?: string }) =>
    request<Enterprise>('POST', '/enterprises', data),

  /** 获取企业详情 */
  getDetail: (id: string) =>
    request<EnterpriseDetail>('GET', `/enterprises/${id}`),

  /** 获取企业成员 */
  getMembers: (id: string) =>
    request<User[]>('GET', `/enterprises/${id}/members`),

  /** 邀请成员 */
  inviteMember: (id: string, data: { email: string; realName?: string; role?: string; departmentId?: string }) =>
    request<InviteMemberResponse>('POST', `/enterprises/${id}/members/invite`, data),

  /** 超管重置成员密码：返回一次性展示的随机临时密码 */
  resetMemberPassword: (id: string, memberId: string) =>
    request<{ password: string }>('POST', `/enterprises/${id}/members/${memberId}/reset-password`),

  /** 移除成员 */
  removeMember: (enterpriseId: string, memberId: string) =>
    request<void>('DELETE', `/enterprises/${enterpriseId}/members/${memberId}`),

  /** 获取部门树 */
  getDepartmentTree: (enterpriseId: string) =>
    request<DepartmentTree[]>('GET', '/departments/tree', undefined, { enterpriseId }),

  /** 创建部门 */
  createDepartment: (enterpriseId: string, data: { name: string; parentId?: string; sort?: number }) =>
    request<{ id: string; name: string }>('POST', `/departments?enterpriseId=${enterpriseId}`, data),

  /** 删除部门 */
  deleteDepartment: (id: string) =>
    request<void>('DELETE', `/departments/${id}`),

  /** 更新部门（重命名/调整上级；parentId 传空串表示移为顶级） */
  updateDepartment: (id: string, data: { name?: string; parentId?: string }) =>
    request<Department>('PUT', `/departments/${id}`, data),

  /** 通过邀请码加入企业 */
  /** 修改成员角色（需高于目标成员职级） */
  updateMemberRole: (enterpriseId: string, memberId: string, role: string) =>
    request<User>('PUT', `/enterprises/${enterpriseId}/members/${memberId}/role`, { role }),

  /** 调整成员部门（departmentId 为空表示移出部门） */
  updateMemberDepartment: (enterpriseId: string, memberId: string, departmentId?: string) =>
    request<User>('PUT', `/enterprises/${enterpriseId}/members/${memberId}/department`, { departmentId }),

  /** 解散企业（仅企业管理员）：清退成员、删除部门与项目 */
  dissolve: (id: string) => request<void>('POST', `/enterprises/${id}/dissolve`),

  /** 成员自助退出企业（最后一位超管不可退出） */
  leave: (id: string) => request<void>('POST', `/enterprises/${id}/leave`),

  joinByInviteCode: (inviteCode: string) =>
    request<Enterprise>('POST', '/enterprises/join', undefined, { inviteCode }),

  /** 更新企业信息（名称/Logo/重新生成邀请码/企微机器人） */
  update: (id: string, data: { name?: string; logo?: string; regenerateInviteCode?: boolean; wecomWebhook?: string }) =>
    request<Enterprise>('PUT', `/enterprises/${id}`, data),
};
