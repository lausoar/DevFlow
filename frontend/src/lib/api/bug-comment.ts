import { request } from './client';

// ============================================
// Bug 评论 API
// ============================================
export interface BugCommentInfo {
  id: string;
  bugId: string;
  projectId: string;
  userId: string;
  content: string;
  createdAt: string;
  userName?: string;
  userAvatar?: string;
}

export const bugCommentApi = {
  list: (bugId: string) =>
    request<BugCommentInfo[]>('GET', `/bugs/${bugId}/comments`),
  create: (bugId: string, content: string) =>
    request<BugCommentInfo>('POST', `/bugs/${bugId}/comments`, { content }),
  remove: (commentId: string) =>
    request<void>('DELETE', `/bugs/comments/${commentId}`),
};
