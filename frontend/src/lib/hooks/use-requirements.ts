import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { requirementApi } from '@/lib/api/requirement';
import type {
  CreateRequirementRequest,
  UpdateRequirementRequest,
} from '@/types';
// ============================================
// 需求相关 Hooks
// ============================================

/** 获取需求列表 */
export function useRequirements(
  projectId: string | null,
  params?: {
    page?: number;
    pageSize?: number;
    status?: string;
    priority?: string;
    keyword?: string;
  }
) {
  return useQuery({
    queryKey: ['requirements', projectId, params],
    queryFn: () => requirementApi.list(projectId!, params),
    enabled: !!projectId,
  });
}

/** 获取需求详情 */
export function useRequirement(id: string | null) {
  return useQuery({
    queryKey: ['requirement', id],
    queryFn: () => requirementApi.getById(id!),
    enabled: !!id,
  });
}

/** 获取需求统计 */
export function useRequirementStats(projectId: string | null) {
  return useQuery({
    queryKey: ['requirement-stats', projectId],
    queryFn: () => requirementApi.getStats(projectId!),
    enabled: !!projectId,
  });
}

/** 创建需求 */
export function useCreateRequirement(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: CreateRequirementRequest) =>
      requirementApi.create(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['requirements', projectId] });
      queryClient.invalidateQueries({ queryKey: ['requirement-stats', projectId] });
    },
  });
}

/** 更新需求 */
export function useUpdateRequirement(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateRequirementRequest }) =>
      requirementApi.update(projectId, id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['requirements', projectId] });
      queryClient.invalidateQueries({ queryKey: ['requirement'] });
      queryClient.invalidateQueries({ queryKey: ['requirement-stats', projectId] });
    },
  });
}

/** 删除需求 */
export function useDeleteRequirement(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => requirementApi.delete(projectId, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['requirements', projectId] });
      queryClient.invalidateQueries({ queryKey: ['requirement-stats', projectId] });
    },
  });
}


/** 关联任务 */
export function useLinkTask(projectId: string, requirementId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (taskId: string) =>
      requirementApi.linkTask(projectId, requirementId, taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['requirements', projectId] });
    },
  });
}

/** 取消关联任务 */
export function useUnlinkTask(projectId: string, requirementId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (taskId: string) =>
      requirementApi.unlinkTask(projectId, requirementId, taskId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['requirements', projectId] });
    },
  });
}

/** 需求变更记录（详情弹窗打开才请求） */
export function useRequirementChanges(projectId: string | null, requirementId: string | null) {
  return useQuery({
    queryKey: ['requirement-changes', requirementId],
    queryFn: () => requirementApi.getChanges(projectId!, requirementId!),
    enabled: !!projectId && !!requirementId,
  });
}
