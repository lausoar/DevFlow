import { request } from './client';

// ============================================
// 工作流 API（状态集合 / 流转规则 / 看板列）
// ============================================

export type WorkflowCategory = 'TODO' | 'IN_PROGRESS' | 'DONE';

export interface WorkflowStatusDef {
  code: string;
  name: string;
  category: WorkflowCategory;
  color?: string | null;
  sort?: number;
  isInitial?: number;
  isTerminal?: number;
  /** 允许直接流转到的状态码 */
  next: string[];
}

export interface WorkflowDef {
  id: string;
  projectId?: string | null;
  name: string;
  issueType: string;
  isDefault?: number;
}

export interface WorkflowPayload {
  workflow: WorkflowDef;
  statuses: WorkflowStatusDef[];
}

export const workflowApi = {
  /** 读取项目工作流（issueType: TASK / BUG / REQUIREMENT） */
  get: (projectId: string, issueType: 'TASK' | 'BUG' | 'REQUIREMENT' = 'TASK') =>
    request<WorkflowPayload>('GET', `/projects/${projectId}/workflow`, undefined, { issueType }),

  /** 覆盖式保存工作流（仅测试/研发负责人及以上） */
  update: (
    projectId: string,
    issueType: 'TASK' | 'BUG' | 'REQUIREMENT',
    statuses: Array<{
      code: string;
      name: string;
      category: WorkflowCategory;
      color?: string;
      sort?: number;
      isInitial?: boolean;
      isTerminal?: boolean;
      next?: string[];
    }>,
  ) =>
    request<WorkflowPayload>('PUT', `/projects/${projectId}/workflow`, { statuses }, { issueType }),

  /** 恢复默认（删除项目自定义快照） */
  reset: (projectId: string, issueType: 'TASK' | 'BUG' | 'REQUIREMENT' = 'TASK') =>
    request<void>('DELETE', `/projects/${projectId}/workflow`, undefined, { issueType }),
};
