import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { docExtraApi } from '@/lib/api/document';

// ============================================
// 文档版本历史与评论 Hooks
// ============================================

export function useDocVersions(documentId: string | null) {
  return useQuery({
    queryKey: ['doc-versions', documentId],
    queryFn: () => docExtraApi.versions(documentId!),
    enabled: !!documentId,
  });
}

export function useRestoreDocVersion(documentId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (versionId: string) => docExtraApi.restoreVersion(documentId!, versionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
      queryClient.invalidateQueries({ queryKey: ['doc-versions', documentId] });
    },
  });
}

export function useDocComments(documentId: string | null) {
  return useQuery({
    queryKey: ['doc-comments', documentId],
    queryFn: () => docExtraApi.comments(documentId!),
    enabled: !!documentId,
  });
}

export function useAddDocComment(documentId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (content: string) => docExtraApi.addComment(documentId!, content),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['doc-comments', documentId] });
    },
  });
}

export function useDeleteDocComment(documentId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (commentId: string) => docExtraApi.deleteComment(documentId!, commentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['doc-comments', documentId] });
    },
  });
}
