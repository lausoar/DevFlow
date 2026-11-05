import { request } from './client';
import type { ChatChannel, ChatMessage, PageResult, ProjectMember } from '@/types';

// ============================================
// 聊天 API
// ============================================
export const chatApi = {
  /** 获取频道列表 */
  getChannels: () =>
    request<ChatChannel[]>('GET', '/channels'),

  /** 创建频道 */
  createChannel: (data: Partial<ChatChannel>) =>
    request<ChatChannel>('POST', '/channels', data),

  /** 获取频道成员（项目群 = 项目成员，动态派生） */
  getChannelMembers: (channelId: string) =>
    request<ProjectMember[]>('GET', `/channels/${channelId}/members`),

  /** 获取频道消息历史 */
  getMessages: (channelId: string, page = 1, pageSize = 50) =>
    request<PageResult<ChatMessage>>('GET', `/channels/${channelId}/messages`, undefined, { page, pageSize }),

  /** 发送消息（REST） */
  sendMessage: (channelId: string, data: { content: string; type?: string; replyToId?: string }) =>
    request<ChatMessage>('POST', `/channels/${channelId}/messages`, data),
};
