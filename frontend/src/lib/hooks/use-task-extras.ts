import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { taskExtraApi, worklogApi, type TaskChecklistItem, type TaskWorklogInfo } from '@/lib/api/task';

// ============================================
// 任务清单与依赖 Hooks
// ============================================

export function useTaskChecklist(taskId: string | null) {
  return useQuery({
    queryKey: ['task-checklist', taskId],
    queryFn: () => taskExtraApi.checklist(taskId!),
    enabled: !!taskId,
  });
}

export function useTaskDependencies(taskId: string | null) {
  return useQuery({
    queryKey: ['task-dependencies', taskId],
    queryFn: () => taskExtraApi.dependencies(taskId!),
    enabled: !!taskId,
  });
}

/** 项目内所有任务的清单进度与未完成前置数（看板徽章） */
export function useTaskExtras(projectId: string | null) {
  return useQuery({
    queryKey: ['task-extras', projectId],
    queryFn: () => taskExtraApi.extras(projectId!),
    enabled: !!projectId,
  });
}

function useInvalidateExtras(taskId: string | null) {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: ['task-checklist', taskId] });
    queryClient.invalidateQueries({ queryKey: ['task-dependencies', taskId] });
    queryClient.invalidateQueries({ queryKey: ['task-extras'] });
  };
}

export function useAddChecklistItem(taskId: string | null) {
  const invalidate = useInvalidateExtras(taskId);
  return useMutation({
    mutationFn: (title: string) => taskExtraApi.addChecklistItem(taskId!, title),
    onSuccess: invalidate,
  });
}

export function useUpdateChecklistItem(taskId: string | null) {
  const invalidate = useInvalidateExtras(taskId);
  return useMutation({
    mutationFn: ({ itemId, data }: { itemId: string; data: { title?: string; done?: boolean } }) =>
      taskExtraApi.updateChecklistItem(taskId!, itemId, data),
    onSuccess: invalidate,
  });
}

export function useDeleteChecklistItem(taskId: string | null) {
  const invalidate = useInvalidateExtras(taskId);
  return useMutation({
    mutationFn: (itemId: string) => taskExtraApi.deleteChecklistItem(taskId!, itemId),
    onSuccess: invalidate,
  });
}

export function useAddDependency(taskId: string | null) {
  const invalidate = useInvalidateExtras(taskId);
  return useMutation({
    mutationFn: (dependsOnTaskId: string) => taskExtraApi.addDependency(taskId!, dependsOnTaskId),
    onSuccess: invalidate,
  });
}

export function useRemoveDependency(taskId: string | null) {
  const invalidate = useInvalidateExtras(taskId);
  return useMutation({
    mutationFn: (dependencyId: string) => taskExtraApi.removeDependency(taskId!, dependencyId),
    onSuccess: invalidate,
  });
}

export type { TaskChecklistItem };

// ============================================
// 工时登记 Hooks
// ============================================

export function useWorklogs(taskId: string | null) {
  return useQuery({
    queryKey: ['task-worklogs', taskId],
    queryFn: () => worklogApi.list(taskId!),
    enabled: !!taskId,
  });
}

export function useAddWorklog(taskId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { hours: number; workDate?: string; note?: string }) =>
      worklogApi.add(taskId!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['task-worklogs', taskId] });
      queryClient.invalidateQueries({ queryKey: ['task-extras'] });
    },
  });
}

export function useDeleteWorklog(taskId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (logId: string) => worklogApi.remove(taskId!, logId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['task-worklogs', taskId] });
      queryClient.invalidateQueries({ queryKey: ['task-extras'] });
    },
  });
}

export type { TaskWorklogInfo };
