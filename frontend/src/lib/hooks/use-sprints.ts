import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { sprintApi } from '@/lib/api/sprint';
import type { Sprint } from '@/types';

// ============================================
// Sprint 相关 Hooks
// ============================================

/** 获取项目的 Sprint 列表 */
export function useSprints(projectId: string | null) {
  return useQuery({
    queryKey: ['sprints', projectId],
    queryFn: () => sprintApi.getByProject(projectId!),
    enabled: !!projectId,
  });
}

/** 获取 Sprint 详情 */
export function useSprint(id: string | null) {
  return useQuery({
    queryKey: ['sprints', 'detail', id],
    queryFn: () => sprintApi.getById(id!),
    enabled: !!id,
  });
}

/** 创建 Sprint */
export function useCreateSprint(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: Partial<Sprint>) => sprintApi.create(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sprints', projectId] });
    },
  });
}

/** 更新 Sprint */
export function useUpdateSprint(id: string, projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: Partial<Sprint>) => sprintApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sprints', projectId] });
    },
  });
}

/** 激活 Sprint */
export function useActivateSprint(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => sprintApi.activate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sprints', projectId] });
    },
  });
}

/** 完成 Sprint */
export function useCompleteSprint(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => sprintApi.complete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sprints', projectId] });
      // 完成时未完成任务被移出迭代，任务列表也要刷新
      queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
    },
  });
}
