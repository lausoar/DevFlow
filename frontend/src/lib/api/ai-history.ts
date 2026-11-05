import { request } from './client';

// ============================================
// AI 消息历史 API（Java 后端，按用户持久化）
// ============================================

export interface AiChatHistoryItem {
  id: string;
  userId: string;
  role: 'user' | 'assistant';
  /** 行为类型: CHAT/REPORT/SUMMARY/TASKS/AGENT/RAG/REVIEW */
  kind: string;
  content: string;
  model?: string;
  createdAt: string;
}

export const aiChatHistoryApi = {
  /** 获取我的 AI 消息历史（时间正序） */
  history: (limit = 100) =>
    request<AiChatHistoryItem[]>('GET', '/ai/chat/history', undefined, { limit }),

  /** 保存一条 AI 消息 */
  save: (data: { role: 'user' | 'assistant'; content: string; kind?: string; model?: string }) =>
    request<AiChatHistoryItem>('POST', '/ai/chat/messages', data),

  /** 清空我的 AI 消息历史 */
  clear: () => request<void>('DELETE', '/ai/chat/history'),
};

/** 各行为类型的历史展示标签 */
export const aiKindLabels: Record<string, string> = {
  CHAT: '',
  REPORT: '日报',
  SUMMARY: '总结',
  TASKS: '任务拆解',
  AGENT: 'Agent',
  RAG: '知识库检索',
  REVIEW: '代码审查',
};

/** 保存失败静默处理（历史记录不应打断主流程） */
export function persistAiMessage(
  role: 'user' | 'assistant',
  content: string,
  kind = 'CHAT',
  model?: string
): void {
  aiChatHistoryApi.save({ role, content, kind, model }).catch(() => {});
}
