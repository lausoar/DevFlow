import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { permissionApi } from '@/lib/api/permission';
import type { PageQuery } from '@/types';

// ============================================
// 权限相关 Hooks
// ============================================

/** 当前用户权限点集合（按钮可见性判定统一走这里，不再手写角色名比较） */
export function useMyPermissions(projectId?: string | null) {
  return useQuery({
    queryKey: ['my-permissions', projectId || ''],
    queryFn: () => permissionApi.me(projectId || undefined),
    enabled: !!projectId,
    staleTime: 30 * 1000,
  });
}

/** 角色-权限矩阵（企业设置页用） */
export function usePermissionMatrix(enabled: boolean) {
  return useQuery({
    queryKey: ['permission-matrix'],
    queryFn: () => permissionApi.matrix(),
    enabled,
  });
}

/** 调整角色-权限映射 */
export function useUpdatePermissionMatrix() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { scope: string; role: string; permissionCode: string; enabled: boolean }) =>
      permissionApi.updateMatrix(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['permission-matrix'] });
      // 权限点变了，所有用户的权限缓存都要失效
      queryClient.invalidateQueries({ queryKey: ['my-permissions'] });
    },
  });
}

/** 企业审计日志 */
export function useAuditLogs(enterpriseId: string | null, enabled: boolean, params?: PageQuery) {
  return useQuery({
    queryKey: ['audit-logs', enterpriseId, params],
    queryFn: () => permissionApi.auditLogs(enterpriseId!, params),
    enabled: enabled && !!enterpriseId,
  });
}
