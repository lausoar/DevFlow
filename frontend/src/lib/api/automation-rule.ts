import { request } from './client';

// ============================================
// 自动化规则 API（MVP）
// 触发器: BUG_CREATED / BUG_FAIL / CASE_FAIL / TASK_DONE
// 动作: notify_user_id 通知 + assign_to_user_id 自动指派（仅 BUG_CREATED 新对象）
// ============================================
export interface AutomationRuleInfo {
  id: string;
  projectId: string;
  name: string;
  triggerType: 'BUG_CREATED' | 'BUG_FAIL' | 'CASE_FAIL' | 'TASK_DONE';
  assignToUserId?: string;
  notifyUserId?: string;
  enabled: number;
  creatorId: string;
  createdAt: string;
}

export const automationRuleApi = {
  list: (projectId: string) =>
    request<AutomationRuleInfo[]>('GET', `/projects/${projectId}/automations`),
  create: (projectId: string, data: Partial<AutomationRuleInfo>) =>
    request<AutomationRuleInfo>('POST', `/projects/${projectId}/automations`, data),
  update: (projectId: string, ruleId: string, data: Partial<AutomationRuleInfo>) =>
    request<AutomationRuleInfo>('PUT', `/projects/${projectId}/automations/${ruleId}`, data),
  remove: (projectId: string, ruleId: string) =>
    request<void>('DELETE', `/projects/${projectId}/automations/${ruleId}`),
};
