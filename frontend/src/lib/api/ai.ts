import { aiClient } from './client';

// ============================================
// AI 服务 API（直连 Python FastAPI）
// ============================================

/** AI 对话消息 */
export interface AiMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
}

/** AI 对话响应 */
export interface AiChatResponse {
  id: string;
  content: string;
  model: string;
  usage: { prompt_tokens: number; completion_tokens: number; total_tokens: number };
}

/** Agent 运行结果 */
export interface AgentResult {
  answer: string;
  tool_calls: Array<{
    tool: string;
    args: Record<string, unknown>;
    result: unknown;
  }>;
  model: string;
}

/** RAG 搜索结果 */
export interface RagSearchResult {
  content: string;
  score: number;
  metadata: { doc_id: string; title: string; chunk_index: number };
}

/** 代码审查结果 */
export interface CodeReviewResult {
  review: string;
  model: string;
}

// ============================================
// AI API
// ============================================
export const aiApi = {
  /** AI 对话 */
  chat: (messages: AiMessage[], model?: string) =>
    aiClient.post<AiChatResponse>('/chat/completions', { messages, model }),

  /** AI 对话（流式） */
  chatStream: (messages: AiMessage[], model?: string) =>
    aiClient.post('/chat/stream', { messages, model }, { responseType: 'text' }),

  // ---- RAG ----

  /** RAG 知识库搜索 */
  ragSearch: (query: string, topK = 5) =>
    aiClient.post<RagSearchResult[]>('/rag/search', { query, top_k: topK }),

  /** RAG 问答（检索 + 生成） */
  ragQuery: (question: string, projectId?: string, topK = 5) =>
    aiClient.post('/rag/query', { question, project_id: projectId, top_k: topK }),

  /** 索引文档 */
  ragIndex: (docId: string, title: string, content: string, docType = 'DOC') =>
    aiClient.post('/rag/index', { doc_id: docId, title, content, doc_type: docType }),

  // ---- 助手 ----

  /** AI 生成日报 */
  dailyReport: (userId: string, projectId?: string, date?: string) =>
    aiClient.post('/assistant/daily-report', { user_id: userId, project_id: projectId, date }),

  /** AI 总结内容 */
  summary: (content: string, summaryType = 'general') =>
    aiClient.post('/assistant/summary', { content, summary_type: summaryType }),

  /** AI 生成任务 */
  generateTasks: (description: string, projectId?: string) =>
    aiClient.post('/assistant/generate-tasks', { description, project_id: projectId }),

  /** AI 代码审查 */
  codeReview: (code: string, language = 'typescript', context?: string) =>
    aiClient.post<CodeReviewResult>('/assistant/code-review', { code, language, context }),

  /** AI 分析 Bug */
  analyzeBug: (title: string, description: string, logs?: string, environment?: string) =>
    aiClient.post('/assistant/analyze-bug', { title, description, logs, environment }),

  // ---- Agent ----

  /** Agent 运行 */
  agentRun: (query: string, projectId?: string) =>
    aiClient.post<AgentResult>('/agent/run', { query, project_id: projectId }),

  /** Agent 状态 */
  agentStatus: () =>
    aiClient.get<{ available_tools: string[]; models: string[] }>('/agent/status'),
};
