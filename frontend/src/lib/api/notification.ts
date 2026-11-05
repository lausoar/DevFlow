import { request } from './client';
import type { Notification, PageResult } from '@/types';

// ============================================
// 通知 API
// ============================================
export const notificationApi = {
  /** 获取通知列表 */
  getList: (params?: { page?: number; pageSize?: number; type?: string; unreadOnly?: boolean }) =>
    request<PageResult<Notification>>('GET', '/notifications', undefined, params as Record<string, unknown>),

  /** 获取未读通知数量 */
  getUnreadCount: () =>
    request<{ count: number }>('GET', '/notifications/unread-count'),

  /** 标记已读 */
  markRead: (id: string) =>
    request<void>('PUT', `/notifications/${id}/read`),

  /** 标记全部已读 */
  markAllRead: () =>
    request<void>('PUT', '/notifications/read-all'),

  /** 删除通知 */
  delete: (id: string) =>
    request<void>('DELETE', `/notifications/${id}`),
};
