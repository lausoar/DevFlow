import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fileApi } from '@/lib/api/file';

// ============================================
// 文件相关 Hooks
// ============================================

/** 获取文件列表 */
export function useFiles(params?: { page?: number; projectId?: string }) {
  return useQuery({
    queryKey: ['files', params],
    queryFn: () => fileApi.getList(params),
    staleTime: 1000 * 60,
  });
}

/** 上传文件 */
export function useUploadFile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ file, projectId }: { file: File; projectId?: string }) =>
      fileApi.upload(file, projectId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['files'] });
    },
  });
}

/** 删除文件 */
export function useDeleteFile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => fileApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['files'] });
    },
  });
}

/** 下载文件（返回 Blob，由调用方触发浏览器下载） */
export function useDownloadFile() {
  return useMutation({
    mutationFn: ({ objectName, asDownload }: { objectName: string; asDownload?: boolean }) =>
      fileApi.download(objectName, asDownload ?? true),
  });
}
