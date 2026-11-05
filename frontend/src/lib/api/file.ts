import apiClient, { request } from './client';
import type { FileItem, PageResult } from '@/types';

// ============================================
// 文件 API
// ============================================
export const fileApi = {
  /** 获取文件列表 */
  getList: (params?: { page?: number; pageSize?: number; projectId?: string }) =>
    request<PageResult<FileItem>>('GET', '/files', undefined, params as Record<string, unknown>),

  /** 上传文件 */
  upload: (file: File, projectId?: string) => {
    const formData = new FormData();
    formData.append('file', file);
    if (projectId) formData.append('projectId', projectId);

    // 使用原始 axios 实例（需要 multipart 格式）
    return import('./client').then(({ default: apiClient }) =>
      apiClient.post('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    );
  },

  /** 删除文件 */
  delete: (id: string) =>
    request<void>('DELETE', `/files/${id}`),

  /** 下载/预览文件（返回 Blob 流，需配合浏览器触发下载） */
  download: (objectName: string, asDownload = true): Promise<Blob> =>
    apiClient.get(`/files/download/${objectName}`, {
      params: { download: asDownload },
      responseType: 'blob',
    }) as Promise<Blob>,
};
