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

  /** 校验审计哈希链（发现被删除/被修改的记录） */
  verifyAuditChain: (enterpriseId: string) =>
    request<{ valid: boolean; checkedRecords: number; brokenAtId?: string | null; message: string }>(
      'GET', `/enterprises/${enterpriseId}/audit-logs/verify`,
    ),

  /** 导出审计日志 CSV（浏览器直接下载，带 JWT） */
  exportAuditLogs: (enterpriseId: string, limit = 5000) => {
    const token = localStorage.getItem('access_token');
    fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1'}/enterprises/${enterpriseId}/audit-logs/export?limit=${limit}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
      .then(async (res) => {
        if (!res.ok) throw new Error('导出失败');
        const blob = await res.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `审计日志-${new Date().toISOString().slice(0, 10)}.csv`;
        a.click();
        URL.revokeObjectURL(url);
      })
      .catch(() => alert('导出失败，请确认有审计读取权限'));
  },
};
