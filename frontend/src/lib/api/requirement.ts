import { request } from './client';
import type {
  Requirement,
  PageResult,
  CreateRequirementRequest,
  UpdateRequirementRequest,
  RequirementStats,
  RequirementChangeLog,
} from '@/types';

// ============================================
// 需求 API
// ============================================
export const requirementApi = {
  /** 获取需求列表（分页） */
  list: (projectId: string, params?: {
    page?: number;
    pageSize?: number;
    status?: string;
    priority?: string;
    keyword?: string;
  }) =>
    request<PageResult<Requirement>>('GET', `/projects/${projectId}/requirements`, undefined, params as Record<string, unknown>),

  /** 获取需求详情 */
  getById: (id: string) =>
    request<Requirement>('GET', `/requirements/${id}`),

  /** 创建需求 */
  create: (projectId: string, data: CreateRequirementRequest) =>
    request<Requirement>('POST', `/projects/${projectId}/requirements`, data),

  /** 更新需求 */
  update: (projectId: string, id: string, data: UpdateRequirementRequest) =>
    request<Requirement>('PUT', `/projects/${projectId}/requirements/${id}`, data),

  /** 删除需求 */
  delete: (projectId: string, id: string) =>
    request<void>('DELETE', `/projects/${projectId}/requirements/${id}`),

  /** 关联任务 */
  linkTask: (projectId: string, id: string, taskId: string) =>
    request<void>('POST', `/projects/${projectId}/requirements/${id}/tasks/${taskId}`),

  /** 取消关联任务 */
  unlinkTask: (projectId: string, id: string, taskId: string) =>
    request<void>('DELETE', `/projects/${projectId}/requirements/${id}/tasks/${taskId}`),

  /** 获取需求统计 */
  getStats: (projectId: string) =>
    request<RequirementStats>('GET', `/projects/${projectId}/requirements/stats`),

  /** 需求变更记录 */
  getChanges: (projectId: string, id: string) =>
    request<RequirementChangeLog[]>('GET', `/projects/${projectId}/requirements/${id}/changes`),
};
