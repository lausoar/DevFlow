import { request } from './client';
import type { Sprint } from '@/types';

// ============================================
// Sprint API
// ============================================
export const sprintApi = {
  /** 获取项目的 Sprint 列表 */
  getByProject: (projectId: string) =>
    request<Sprint[]>('GET', `/projects/${projectId}/sprints`),

  /** 获取 Sprint 详情 */
  getById: (id: string) =>
    request<Sprint>('GET', `/sprints/${id}`),

  /** 创建 Sprint */
  create: (projectId: string, data: Partial<Sprint>) =>
    request<Sprint>('POST', `/projects/${projectId}/sprints`, data),

  /** 更新 Sprint */
  update: (id: string, data: Partial<Sprint>) =>
    request<Sprint>('PUT', `/sprints/${id}`, data),

  /** 激活 Sprint */
  activate: (id: string) =>
    request<Sprint>('POST', `/sprints/${id}/activate`),

  /** 完成 Sprint */
  complete: (id: string) =>
    request<Sprint>('POST', `/sprints/${id}/complete`),

  /** 删除 Sprint */
  delete: (id: string) =>
    request<void>('DELETE', `/sprints/${id}`),
};
