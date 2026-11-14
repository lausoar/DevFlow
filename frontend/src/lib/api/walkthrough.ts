import { request } from './client';

// ============================================
// UI 走查 API
// ============================================

export type WalkthroughResult = 'PENDING' | 'PASS' | 'FAIL' | 'NA';

export interface WalkthroughItem {
  id: string;
  walkthroughId: string;
  title: string;
  result: WalkthroughResult;
  note?: string | null;
  screenshotUrl?: string | null;
  bugId?: string | null;
  sort?: number;
  checkedBy?: string | null;
  checkedAt?: string | null;
}

export interface Walkthrough {
  id: string;
  projectId: string;
  requirementId: string;
  designAssetId?: string | null;
  designVersion?: number | null;
  title: string;
  status: 'OPEN' | 'CLOSED';
  createdBy: string;
  createdAt?: string;
}

export interface WalkthroughProgress {
  total: number;
  checked: number;
  pending: number;
  pass: number;
  fail: number;
  na: number;
  passRate: number;
  convertedBugs: number;
}

export interface WalkthroughSummary extends WalkthroughProgress {
  walkthrough: Walkthrough;
}

export const walkthroughApi = {
  listByRequirement: (requirementId: string) =>
    request<WalkthroughSummary[]>('GET', `/requirements/${requirementId}/walkthroughs`),

  detail: (id: string) =>
    request<WalkthroughProgress & { walkthrough: Walkthrough; items: WalkthroughItem[] }>(
      'GET', `/walkthroughs/${id}`,
    ),

  create: (requirementId: string, data: Partial<Walkthrough>) =>
    request<Walkthrough>('POST', `/requirements/${requirementId}/walkthroughs`, data),

  addItems: (id: string, titles: string[]) =>
    request<{ added: number }>('POST', `/walkthroughs/${id}/items`, { titles }),

  setResult: (itemId: string, data: { result: WalkthroughResult; note?: string; screenshotUrl?: string }) =>
    request<WalkthroughItem>('PUT', `/walkthrough-items/${itemId}`, data),

  deleteItem: (itemId: string) =>
    request<void>('DELETE', `/walkthrough-items/${itemId}`),

  close: (id: string) =>
    request<void>('POST', `/walkthroughs/${id}/close`),

  toBug: (itemId: string, note?: string) =>
    request<{ bugId: string }>('POST', `/walkthrough-items/${itemId}/to-bug`, { note }),
};
