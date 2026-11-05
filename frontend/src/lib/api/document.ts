import { request } from './client';
import type { Document } from '@/types';

// ============================================
// 文档 API
// ============================================
export const documentApi = {
  /** 获取文档列表 */
  getList: (params?: { projectId?: string; type?: string }) =>
    request<Document[]>('GET', '/documents', undefined, params as Record<string, unknown>),

  /** 获取文档详情 */
  getById: (id: string) =>
    request<Document>('GET', `/documents/${id}`),

  /** 创建文档 */
  create: (data: Partial<Document>) =>
    request<Document>('POST', '/documents', data),

  /** 更新文档 */
  update: (id: string, data: Partial<Document>) =>
    request<Document>('PUT', `/documents/${id}`, data),

  /** 删除文档 */
  delete: (id: string) =>
    request<void>('DELETE', `/documents/${id}`),

  /** 获取 Wiki 树形结构 */
  getWikiTree: (projectId: string) =>
    request<WikiTreeNode[]>('GET', '/documents/wiki/tree', undefined, { projectId }),
};

// Wiki 树节点
export interface WikiTreeNode {
  id: string;
  title: string;
  sort: number;
  isPublished: boolean;
  children: WikiTreeNode[];
}

// ============================================
// 文档版本历史与评论
// ============================================
export interface DocumentVersionInfo {
  id: string;
  documentId: string;
  version: number;
  title: string;
  editorId: string;
  editorName?: string;
  createdAt: string;
}

export interface DocumentVersionDetail extends DocumentVersionInfo {
  content: string;
}

export interface DocumentCommentInfo {
  id: string;
  documentId: string;
  userId: string;
  content: string;
  userName?: string;
  userAvatar?: string;
  createdAt: string;
}

export const docExtraApi = {
  versions: (id: string) =>
    request<DocumentVersionInfo[]>('GET', `/documents/${id}/versions`),
  versionDetail: (id: string, versionId: string) =>
    request<DocumentVersionDetail>('GET', `/documents/${id}/versions/${versionId}`),
  restoreVersion: (id: string, versionId: string) =>
    request<Document>('POST', `/documents/${id}/versions/${versionId}/restore`),
  comments: (id: string) =>
    request<DocumentCommentInfo[]>('GET', `/documents/${id}/comments`),
  addComment: (id: string, content: string) =>
    request<DocumentCommentInfo>('POST', `/documents/${id}/comments`, { content }),
  deleteComment: (id: string, commentId: string) =>
    request<void>('DELETE', `/documents/${id}/comments/${commentId}`),
};
