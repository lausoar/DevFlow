import { request } from './client';

// ============================================
// 全局搜索 API
// ============================================
export interface SearchResultItem {
  id: string;
  title: string;
  projectId: string;
  subtitle?: string | null;
  createdAt?: string;
}

export interface GlobalSearchResult {
  projects: SearchResultItem[];
  tasks: SearchResultItem[];
  bugs: SearchResultItem[];
  requirements: SearchResultItem[];
  documents: SearchResultItem[];
}

export const searchApi = {
  /** 全局搜索（结果按用户参与的项目过滤） */
  search: (q: string) =>
    request<GlobalSearchResult>('GET', '/search', undefined, { q }),
};
