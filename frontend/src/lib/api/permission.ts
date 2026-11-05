import { request } from './client';
import type { MePermissions, PermissionMatrix, AuditLog, PageResult, PageQuery } from '@/types';

// ============================================
// 权限 API
// ============================================
export const permissionApi = {
  /** 当前用户权限点集合（企业级 + 指定项目级） */
  me: (projectId?: string) =>
    request<MePermissions>('GET', '/permissions/me', undefined, projectId ? { projectId } : undefined),

  /** 角色-权限矩阵 */
  matrix: () =>
    request<PermissionMatrix>('GET', '/permissions/matrix'),

  /** 调整角色-权限映射（仅超级管理员，全局生效） */
  updateMatrix: (data: { scope: string; role: string; permissionCode: string; enabled: boolean }) =>
    request<void>('PUT', '/permissions/matrix', data),

  /** 企业审计日志分页 */
  auditLogs: (enterpriseId: string, params?: PageQuery) =>
    request<PageResult<AuditLog>>('GET', `/enterprises/${enterpriseId}/audit-logs`, undefined, params as Record<string, unknown>),
};
