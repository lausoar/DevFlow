import { request } from './client';

// ============================================
// 结构化查询 / 保存筛选器 API
// ============================================

export type QueryEntity = 'TASK' | 'BUG' | 'REQUIREMENT';

export interface QueryResult {
  entity: string;
  records: Record<string, unknown>[];
  total: number;
  page: number;
  pageSize: number;
}

export interface SavedFilter {
  id: string;
  projectId?: string | null;
  ownerId: string;
  name: string;
  entity: QueryEntity;
  expression: string;
  isShared: number;
  createdAt?: string;
  updatedAt?: string;
}

export const queryApi = {
  run: (data: {
    entity: QueryEntity;
    expression: string;
    projectId?: string;
    page?: number;
    pageSize?: number;
  }) => request<QueryResult>('POST', '/query', data),

  listFilters: (params?: { projectId?: string; entity?: string }) =>
    request<SavedFilter[]>('GET', '/filters', undefined, params as Record<string, unknown>),

  saveFilter: (data: {
    projectId?: string;
    entity: QueryEntity;
    name: string;
    expression: string;
    isShared?: number;
  }) => request<SavedFilter>('POST', '/filters', data),

  deleteFilter: (id: string) =>
    request<void>('DELETE', `/filters/${id}`),
};
