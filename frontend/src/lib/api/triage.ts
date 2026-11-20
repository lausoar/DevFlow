import { request } from './client';

// ============================================
// 分诊收件箱 API
// 外部提交（游客 Bug / 建议）先进收件箱，由测试人员接受/拒绝/合并
// ============================================

export type TriageStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'MERGED';

export type TriageSource = 'GUEST_BUG' | 'GUEST_FEEDBACK' | 'USER_FEEDBACK';

export interface TriageItem {
  id: string;
  projectId: string;
  source: TriageSource;
  sourceRefId: string;
  title: string;
  submitterName?: string;
  status: TriageStatus;
  resultRefId?: string;
  decidedBy?: { id: string; name?: string } | null;
  decidedAt?: string;
  slaDueAt?: string;
  overdue?: boolean;
  createdAt: string;
}

export interface TriageSimilarBug {
  id: string;
  title: string;
  status: string;
}

export const triageApi = {
  list: (projectId: string, status?: string) =>
    request<TriageItem[]>('GET', `/projects/${projectId}/triage`, undefined,
      status ? { status } : undefined),

  counts: (projectId: string) =>
    request<Record<TriageStatus, number>>('GET', `/projects/${projectId}/triage/counts`),

  similar: (id: string) =>
    request<TriageSimilarBug[]>('GET', `/triage/${id}/similar`),

  accept: (id: string) => request<TriageItem>('POST', `/triage/${id}/accept`),

  decline: (id: string, reason?: string) =>
    request<TriageItem>('POST', `/triage/${id}/decline`, { reason }),

  merge: (id: string, targetBugId: string) =>
    request<TriageItem>('POST', `/triage/${id}/merge`, { targetBugId }),
};

export const TRIAGE_SOURCE_LABELS: Record<TriageSource, string> = {
  GUEST_BUG: '游客 Bug',
  GUEST_FEEDBACK: '游客建议',
  USER_FEEDBACK: '成员建议',
};

export const TRIAGE_STATUS_LABELS: Record<TriageStatus, string> = {
  PENDING: '待处理',
  ACCEPTED: '已接受',
  DECLINED: '已拒绝',
  MERGED: '已合并',
};
