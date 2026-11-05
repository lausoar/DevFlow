import { request } from './client';
import type { Project, ProjectMember, PageResult, PageQuery, GitCommit } from '@/types';

// ============================================
// 项目 API
// ============================================
export const projectApi = {
  /** 获取项目列表 */
  getList: (params?: PageQuery) =>
    request<PageResult<Project>>('GET', '/projects', undefined, params as Record<string, unknown>),

  /** 获取项目详情 */
  getById: (id: string) =>
    request<Project>('GET', `/projects/${id}`),

  /** 创建项目 */
  create: (data: Partial<Project>) =>
    request<Project>('POST', '/projects', data),

  /** 更新项目 */
  update: (id: string, data: Partial<Project>) =>
    request<Project>('PUT', `/projects/${id}`, data),

  /** 删除项目 */
  delete: (id: string) =>
    request<void>('DELETE', `/projects/${id}`),

  // ============================================
  // 项目成员
  // ============================================

  /** 获取项目成员列表（含用户信息） */
  getMembers: (projectId: string) =>
    request<ProjectMember[]>('GET', `/projects/${projectId}/members`),

  /** 项目最近提交（Git webhook 落库） */
  getGitCommits: (projectId: string) =>
    request<GitCommit[]>('GET', `/projects/${projectId}/git/commits`),

  /** 添加项目成员 */
  addMember: (projectId: string, data: { userId: string; role?: string }) =>
    request<ProjectMember>('POST', `/projects/${projectId}/members`, data),

  /** 更新成员角色 */
  updateMemberRole: (projectId: string, memberId: string, data: { role: string }) =>
    request<void>('PUT', `/projects/${projectId}/members/${memberId}`, data),

  /** 移除项目成员 */
  removeMember: (projectId: string, memberId: string) =>
    request<void>('DELETE', `/projects/${projectId}/members/${memberId}`),

  // ============================================
  // 项目邀请链接（仅限同企业成员加入）
  // ============================================

  /** 获取项目邀请码（后端首次调用时自动生成） */
  getInviteCode: (projectId: string) =>
    request<string>('GET', `/projects/${projectId}/invite-code`),

  /** 凭邀请码加入项目（非项目所属企业成员会被拒绝） */
  joinByInviteCode: (inviteCode: string) =>
    request<Project>('POST', '/projects/join', undefined, { inviteCode }),
};
