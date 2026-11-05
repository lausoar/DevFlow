import { request } from './client';
import type { Task, Bug, Requirement } from '@/types';

// ============================================
// 回收站 API
// ============================================
export interface RecycleBinData {
  tasks: Task[];
  bugs: Bug[];
  requirements: Requirement[];
}

export const recycleBinApi = {
  /** 回收站列表（项目内已删除任务/Bug/需求） */
  list: (projectId: string) =>
    request<RecycleBinData>('GET', `/projects/${projectId}/recycle-bin`),

  restoreTask: (id: string) =>
    request<void>('POST', `/recycle-bin/tasks/${id}/restore`),

  restoreBug: (id: string) =>
    request<void>('POST', `/recycle-bin/bugs/${id}/restore`),

  restoreRequirement: (id: string) =>
    request<void>('POST', `/recycle-bin/requirements/${id}/restore`),
};
