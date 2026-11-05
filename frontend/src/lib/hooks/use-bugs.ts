import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { bugApi } from '@/lib/api/bug';
import type { Bug, PageQuery } from '@/types';

// ============================================
// Bug 相关 Hooks
// ============================================

/** 获取项目的 Bug 列表 */
export function useBugs(projectId: string | null, params?: PageQuery) {
  return useQuery({
    queryKey: ['bugs', projectId, params],
    queryFn: () => bugApi.getByProject(projectId!, params),
    enabled: !!projectId,
  });
}

/** 创建 Bug */
export function useCreateBug(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: Partial<Bug>) => bugApi.create(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
    },
  });
}

/** 更新 Bug */
export function useUpdateBug(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<Bug> }) =>
      bugApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
    },
  });
}

/** 删除 Bug */
export function useDeleteBug(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => bugApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
    },
  });
}

/** 变更 Bug 状态（负责人/报告人） */
export function useUpdateBugStatus(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      bugApi.updateStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
    },
  });
}

/** 确认游客提交的 Bug（bug:edit，pending 1→0 进入正常流转） */
export function useConfirmGuestBug(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => bugApi.confirmGuestBug(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
    },
  });
}
