import { request } from './client';
import type { Task, PageResult, PageQuery } from '@/types';

// ============================================
// 任务 API
// ============================================
export const taskApi = {
  /** 获取项目的任务列表 */
  getByProject: (projectId: string, params?: PageQuery) =>
    request<PageResult<Task>>('GET', `/projects/${projectId}/tasks`, undefined, params as Record<string, unknown>),

  /** 按 Sprint 获取任务 */
  getBySprint: (sprintId: string) =>
    request<Task[]>('GET', `/sprints/${sprintId}/tasks`),

  /** 我的任务（跨项目，按截止时间排序） */
  getMy: () =>
    request<Task[]>('GET', `/tasks/my`),

  /** 获取任务详情 */
  getById: (id: string) =>
    request<Task>('GET', `/tasks/${id}`),

  /** 创建任务 */
  create: (projectId: string, data: Partial<Task>) =>
    request<Task>('POST', `/projects/${projectId}/tasks`, data),

  /** 更新任务 */
  update: (id: string, data: Partial<Task>) =>
    request<Task>('PUT', `/tasks/${id}`, data),

  /** 更新任务状态 */
  updateStatus: (id: string, status: string) =>
    request<void>('PUT', `/tasks/${id}/status`, { status }),

  /** 删除任务 */
  delete: (id: string) =>
    request<void>('DELETE', `/tasks/${id}`),
};

// ============================================
// 任务清单与依赖
// ============================================
export interface TaskChecklistItem {
  id: string;
  taskId: string;
  title: string;
  done: number;
  createdAt: string;
}

export interface TaskDependencyInfo {
  dependencyId: string;
  taskId: string;
  title: string;
  status?: string;
  done?: boolean;
}

export interface TaskDependenciesView {
  blockedBy: TaskDependencyInfo[];
  blocking: TaskDependencyInfo[];
}

export interface TaskExtrasSummary {
  checklist: Record<string, { total: number; done: number }>;
  undoneBlockers: Record<string, number>;
  worklogHours: Record<string, number>;
}

export const taskExtraApi = {
  checklist: (taskId: string) =>
    request<TaskChecklistItem[]>('GET', `/tasks/${taskId}/checklist`),
  addChecklistItem: (taskId: string, title: string) =>
    request<TaskChecklistItem>('POST', `/tasks/${taskId}/checklist`, { title }),
  updateChecklistItem: (taskId: string, itemId: string, data: { title?: string; done?: boolean }) =>
    request<TaskChecklistItem>('PUT', `/tasks/${taskId}/checklist/${itemId}`, data),
  deleteChecklistItem: (taskId: string, itemId: string) =>
    request<void>('DELETE', `/tasks/${taskId}/checklist/${itemId}`),
  dependencies: (taskId: string) =>
    request<TaskDependenciesView>('GET', `/tasks/${taskId}/dependencies`),
  addDependency: (taskId: string, dependsOnTaskId: string) =>
    request<unknown>('POST', `/tasks/${taskId}/dependencies`, { dependsOnTaskId }),
  removeDependency: (taskId: string, dependencyId: string) =>
    request<void>('DELETE', `/tasks/${taskId}/dependencies/${dependencyId}`),
  extras: (projectId: string) =>
    request<TaskExtrasSummary>('GET', `/projects/${projectId}/task-extras`),
};

export interface TaskWorklogInfo {
  id: string;
  taskId: string;
  userId: string;
  hours: number;
  workDate: string;
  note?: string;
  createdAt: string;
  userName?: string;
}

export const worklogApi = {
  list: (taskId: string) =>
    request<{ logs: TaskWorklogInfo[]; totalHours: number }>('GET', `/tasks/${taskId}/worklogs`),
  add: (taskId: string, data: { hours: number; workDate?: string; note?: string }) =>
    request<{ log: TaskWorklogInfo; totalHours: number }>('POST', `/tasks/${taskId}/worklogs`, data),
  remove: (taskId: string, logId: string) =>
    request<void>('DELETE', `/tasks/${taskId}/worklogs/${logId}`),
};
