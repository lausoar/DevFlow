import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { bugCommentApi } from '@/lib/api/bug-comment';

// ============================================
// Bug 评论 Hooks
// ============================================

export function useBugComments(bugId: string | null) {
  return useQuery({
    queryKey: ['bug-comments', bugId],
    queryFn: () => bugCommentApi.list(bugId!),
    enabled: !!bugId,
  });
}

export function useCreateBugComment(bugId: string | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (content: string) => bugCommentApi.create(bugId!, content),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bug-comments', bugId] });
    },
  });
}

export function useDeleteBugComment(bugId: string | null) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (commentId: string) => bugCommentApi.remove(commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bug-comments', bugId] });
    },
  });
}
