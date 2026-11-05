import { request } from './client';
import type { TaskComment } from '@/types';

// ============================================
// 任务评论 API
// ============================================
export const taskCommentApi = {
  /** 获取任务评论列表 */
  getByTask: (taskId: string) =>
    request<TaskComment[]>('GET', `/tasks/${taskId}/comments`),

  /** 创建评论 */
  create: (taskId: string, data: { content: string; replyToId?: string }) =>
    request<TaskComment>('POST', `/tasks/${taskId}/comments`, data),

  /** 删除评论 */
  delete: (id: string) =>
    request<void>('DELETE', `/comments/${id}`),
};
