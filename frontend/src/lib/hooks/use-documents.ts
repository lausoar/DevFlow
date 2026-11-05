import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { documentApi } from '@/lib/api/document';
import type { Document } from '@/types';

// ============================================
// 文档相关 Hooks
// ============================================

/** 获取文档列表 */
export function useDocuments(params?: { projectId?: string; type?: string }) {
  return useQuery({
    queryKey: ['documents', params],
    queryFn: () => documentApi.getList(params),
    staleTime: 1000 * 60 * 2,
  });
}

/** 获取文档详情 */
export function useDocument(id: string | null) {
  return useQuery({
    queryKey: ['documents', 'detail', id],
    queryFn: () => documentApi.getById(id!),
    enabled: !!id,
  });
}

/** 创建文档 */
export function useCreateDocument() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: Partial<Document>) => documentApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}

/** 更新文档 */
export function useUpdateDocument(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: Partial<Document>) => documentApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents'] });
    },
  });
}

/** 获取 Wiki 树 */
export function useWikiTree(projectId: string | null) {
  return useQuery({
    queryKey: ['wiki-tree', projectId],
    queryFn: () => documentApi.getWikiTree(projectId!),
    enabled: !!projectId,
  });
}
