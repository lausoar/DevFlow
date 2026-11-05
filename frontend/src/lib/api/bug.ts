import { request } from './client';
import type { Bug, PageResult, PageQuery } from '@/types';

// ============================================
// Bug API
// ============================================
export const bugApi = {
  /** 获取项目的 Bug 列表 */
  getByProject: (projectId: string, params?: PageQuery) =>
    request<PageResult<Bug>>('GET', `/projects/${projectId}/bugs`, undefined, params as Record<string, unknown>),

  /** 我的 Bug（跨项目，按严重程度排序） */
  getMy: () =>
    request<Bug[]>('GET', `/bugs/my`),

  /** 获取 Bug 详情 */
  getById: (id: string) =>
    request<Bug>('GET', `/bugs/${id}`),

  /** 创建 Bug */
  create: (projectId: string, data: Partial<Bug>) =>
    request<Bug>('POST', `/projects/${projectId}/bugs`, data),

  /** 更新 Bug */
  update: (id: string, data: Partial<Bug>) =>
    request<Bug>('PUT', `/bugs/${id}`, data),

  /** 删除 Bug */
  delete: (id: string) =>
    request<void>('DELETE', `/bugs/${id}`),

  /** 变更 Bug 状态（仅负责人/报告人可调用） */
  updateStatus: (id: string, status: string) =>
    request<Bug>('PUT', `/bugs/${id}/status`, { status }),

  /** 确认游客提交的 Bug（bug:edit = 测试人员及以上） */
  confirmGuestBug: (id: string) =>
    request<void>('POST', `/bugs/${id}/confirm`),
};
