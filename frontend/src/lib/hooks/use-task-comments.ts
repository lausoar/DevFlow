import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { taskCommentApi } from '@/lib/api/task-comment';
import type { TaskComment } from '@/types';

// ============================================
// 任务评论相关 Hooks
// ============================================

/** 获取任务评论列表 */
export function useTaskComments(taskId: string | null) {
  return useQuery({
    queryKey: ['task-comments', taskId],
    queryFn: () => taskCommentApi.getByTask(taskId!),
    enabled: !!taskId,
  });
}

/** 创建评论 */
export function useCreateComment(taskId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { content: string; replyToId?: string }) =>
      taskCommentApi.create(taskId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['task-comments', taskId] });
    },
  });
}

/** 删除评论 */
export function useDeleteComment(taskId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => taskCommentApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['task-comments', taskId] });
    },
  });
}
