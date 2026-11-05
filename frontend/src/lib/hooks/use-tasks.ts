import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { taskApi } from '@/lib/api/task';
import type { Task, PageQuery } from '@/types';

// ============================================
// 任务相关 Hooks
// ============================================

/** 获取项目的任务列表 */
export function useTasks(projectId: string | null, params?: PageQuery) {
  return useQuery({
    queryKey: ['tasks', projectId, params],
    queryFn: () => taskApi.getByProject(projectId!, params),
    enabled: !!projectId,
  });
}

/** 按 Sprint 获取任务 */
export function useTasksBySprint(sprintId: string | null) {
  return useQuery({
    queryKey: ['tasks', 'sprint', sprintId],
    queryFn: () => taskApi.getBySprint(sprintId!),
    enabled: !!sprintId,
  });
}

/** 创建任务 */
export function useCreateTask(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: Partial<Task>) => taskApi.create(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
      queryClient.invalidateQueries({ queryKey: ['sprints', projectId] });
    },
  });
}

/** 更新任务状态 */
export function useUpdateTaskStatus(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      taskApi.updateStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
      queryClient.invalidateQueries({ queryKey: ['sprints', projectId] });
    },
  });
}

/** 更新任务（通用） */
export function useUpdateTask(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<Task> }) =>
      taskApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
      queryClient.invalidateQueries({ queryKey: ['sprints', projectId] });
    },
  });
}

/** 删除任务 */
export function useDeleteTask(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => taskApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
    },
  });
}
