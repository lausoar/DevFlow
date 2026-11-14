import { request } from './client';

// ============================================
// 版本 / 发布 API
// ============================================

export interface ProjectRelease {
  id: string;
  projectId: string;
  name: string;
  version: string;
  status: 'UNRELEASED' | 'RELEASED' | 'ARCHIVED';
  description?: string | null;
  releaseDate?: string | null;
  releasedAt?: string | null;
  releasedBy?: string | null;
  releaseNotes?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

export interface ReleaseStats {
  requirements: number;
  tasks: number;
  openTasks: number;
  bugs: number;
  openBugs: number;
}

export const releaseApi = {
  list: (projectId: string) =>
    request<ProjectRelease[]>('GET', `/projects/${projectId}/releases`),

  get: (id: string) =>
    request<ProjectRelease>('GET', `/releases/${id}`),

  create: (projectId: string, data: Partial<ProjectRelease>) =>
    request<ProjectRelease>('POST', `/projects/${projectId}/releases`, data),

  update: (id: string, data: Partial<ProjectRelease>) =>
    request<ProjectRelease>('PUT', `/releases/${id}`, data),

  remove: (id: string) =>
    request<void>('DELETE', `/releases/${id}`),

  /** 生成发布说明（按版本聚合需求/任务/缺陷） */
  generateNotes: (id: string, overwrite = true) =>
    request<{ releaseNotes: string }>('POST', `/releases/${id}/notes`, undefined, { overwrite }),

  publish: (id: string) =>
    request<ProjectRelease>('POST', `/releases/${id}/publish`),

  stats: (id: string) =>
    request<ReleaseStats>('GET', `/releases/${id}/stats`),
};
