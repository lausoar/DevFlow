import { request } from './client';

// ============================================
// 收件箱 API：跨项目聚合"等我处理的事"
// ============================================

export interface InboxTaskItem {
  id: string;
  type: 'TASK';
  projectId: string;
  title: string;
  status: string;
  priority?: string;
  dueDate?: string;
  createdAt: string;
}

export interface InboxApprovalItem {
  id: string;
  type: 'DEPLOYMENT';
  projectId: string;
  title: string;
  createdAt: string;
}

export interface InboxTriageItem {
  id: string;
  type: 'TRIAGE';
  projectId: string;
  title: string;
  submitterName?: string;
  createdAt: string;
}

export interface InboxResponse {
  assignments: InboxTaskItem[];
  approvals: InboxApprovalItem[];
  triage: InboxTriageItem[];
  total: number;
}

export const inboxApi = {
  get: () => request<InboxResponse>('GET', '/inbox'),
};

/** 部署审批动作（与部署页共用同一接口） */
export const deploymentApprovalApi = {
  approve: (id: string, note?: string) =>
    request<unknown>('POST', `/deployments/${id}/approve`, { note }),
  reject: (id: string, note?: string) =>
    request<unknown>('POST', `/deployments/${id}/reject`, { note }),
};
