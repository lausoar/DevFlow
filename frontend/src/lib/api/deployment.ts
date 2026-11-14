import { request } from './client';

// ============================================
// 部署 / 环境 / DORA API
// ============================================

export interface DeployEnvironment {
  id: string;
  projectId: string;
  name: string;
  type: 'DEV' | 'TEST' | 'STAGING' | 'PROD';
  requiresApproval: number;
  approvalRoles?: string | null;
  url?: string | null;
  sort?: number;
}

export type DeploymentStatus =
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'DEPLOYING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'ROLLED_BACK'
  | 'CANCELLED';

export interface Deployment {
  id: string;
  projectId: string;
  environmentId: string;
  releaseId?: string | null;
  version?: string | null;
  commitSha?: string | null;
  firstCommitAt?: string | null;
  status: DeploymentStatus;
  triggeredBy: string;
  approvedBy?: string | null;
  approvedAt?: string | null;
  approvalNote?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  rollbackOf?: string | null;
  note?: string | null;
  createdAt: string;
}

export interface DoraMetrics {
  hasProdEnvironment: boolean;
  windowDays: number;
  deploymentFrequency: number;
  deploymentFrequencyLabel: string;
  leadTimeHours: number | null;
  changeFailureRate: number | null;
  mttrHours: number | null;
  totalDeployments: number;
  failedDeployments: number;
  sampleSize: number;
  leadTimeSamples?: number;
  note?: string;
}

export const deploymentApi = {
  listEnvironments: (projectId: string) =>
    request<DeployEnvironment[]>('GET', `/projects/${projectId}/environments`),

  createEnvironment: (projectId: string, data: Partial<DeployEnvironment>) =>
    request<DeployEnvironment>('POST', `/projects/${projectId}/environments`, data),

  updateEnvironment: (id: string, data: Partial<DeployEnvironment>) =>
    request<DeployEnvironment>('PUT', `/environments/${id}`, data),

  removeEnvironment: (id: string) =>
    request<void>('DELETE', `/environments/${id}`),

  listDeployments: (
    projectId: string,
    params?: { environmentId?: string; releaseId?: string; limit?: number },
  ) =>
    request<Deployment[]>('GET', `/projects/${projectId}/deployments`, undefined, params as Record<string, unknown>),

  createDeployment: (projectId: string, environmentId: string, data: Partial<Deployment>) =>
    request<Deployment>('POST', `/projects/${projectId}/environments/${environmentId}/deployments`, data),

  approve: (id: string, note?: string) =>
    request<Deployment>('POST', `/deployments/${id}/approve`, { note }),

  reject: (id: string, note?: string) =>
    request<Deployment>('POST', `/deployments/${id}/reject`, { note }),

  start: (id: string) =>
    request<Deployment>('POST', `/deployments/${id}/start`),

  finish: (id: string, success: boolean, note?: string) =>
    request<Deployment>('POST', `/deployments/${id}/finish`, { note }, { success }),

  rollback: (id: string, note?: string) =>
    request<Deployment>('POST', `/deployments/${id}/rollback`, { note }),

  dora: (projectId: string, days = 30) =>
    request<DoraMetrics>('GET', `/projects/${projectId}/dora`, undefined, { days }),
};
