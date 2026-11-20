import { request } from './client';

// ============================================
// 企业目标（OKR）API
// 读 = 企业成员；写 = enterprise:manage（与项目集同口径）
// ============================================

export type ObjectiveStatus = 'ACTIVE' | 'ACHIEVED' | 'ARCHIVED';

export interface Objective {
  id: string;
  enterpriseId: string;
  programId?: string | null;
  name: string;
  description?: string;
  ownerId?: string;
  period?: string;
  startDate?: string;
  endDate?: string;
  status: ObjectiveStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface ObjectiveProjectRow {
  id: string;
  name: string;
  ownerId?: string;
  status?: string;
  totalTasks: number;
  doneTasks: number;
  completedInPeriod: number;
  worklogHours: number;
  progress: number;
}

export interface ObjectiveRollup {
  objective: Objective;
  projects: ObjectiveProjectRow[];
  projectCount: number;
  totalTasks: number;
  completedInPeriod: number;
  worklogHours: number;
  requirements: {
    total: number;
    done: number;
    progress: number;
  };
}

export const objectiveApi = {
  list: (enterpriseId: string) =>
    request<Objective[]>('GET', `/enterprises/${enterpriseId}/objectives`),

  rollup: (id: string) => request<ObjectiveRollup>('GET', `/objectives/${id}`),

  create: (enterpriseId: string, data: Partial<Objective>) =>
    request<Objective>('POST', `/enterprises/${enterpriseId}/objectives`, data),

  update: (id: string, data: Partial<Objective>) =>
    request<Objective>('PUT', `/objectives/${id}`, data),

  remove: (id: string) => request<void>('DELETE', `/objectives/${id}`),

  /** 把项目挂到目标（objectiveId 为 null 表示移出） */
  assignProject: (projectId: string, objectiveId: string | null) =>
    request<void>('PUT', `/projects/${projectId}/objective`, { objectiveId }),
};
