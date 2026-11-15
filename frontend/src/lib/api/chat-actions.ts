import { request } from './client';

// ============================================
// 消息动作 API（编辑 / 撤回 / 表情 / 已读）
// ============================================

export interface ReactionMap {
  [emoji: string]: string[];
}

export const chatActionApi = {
  edit: (messageId: string, content: string) =>
    request<{ id: string; content: string; isEdited: number; editedAt: string }>(
      'PUT', `/messages/${messageId}`, { content }),

  recall: (messageId: string) =>
    request<{ id: string; recalledAt: string; recalledBy: string }>(
      'POST', `/messages/${messageId}/recall`),

  toggleReaction: (messageId: string, emoji: string) =>
    request<{ messageId: string; emoji: string; added: boolean; reactions: ReactionMap }>(
      'POST', `/messages/${messageId}/reactions`, { emoji }),

  markRead: (channelId: string, readAt?: string) =>
    request<{ unread: number }>('POST', `/channels/${channelId}/read`, { readAt }),

  unread: (channelId: string) =>
    request<{ unread: number }>('GET', `/channels/${channelId}/unread`),

  reads: (channelId: string) =>
    request<{ readers: Record<string, string>; readCount: number }>(
      'GET', `/channels/${channelId}/reads`),
};
