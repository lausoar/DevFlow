import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { releaseApi, type ProjectRelease } from '@/lib/api/release';

// ============================================
// 版本 / 发布 Hooks
// ============================================

export function useReleases(projectId: string | null) {
  return useQuery({
    queryKey: ['releases', projectId],
    queryFn: () => releaseApi.list(projectId!),
    enabled: !!projectId,
  });
}

export function useReleaseStats(releaseId: string | null) {
  return useQuery({
    queryKey: ['release-stats', releaseId],
    queryFn: () => releaseApi.stats(releaseId!),
    enabled: !!releaseId,
  });
}

export function useCreateRelease(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<ProjectRelease>) => releaseApi.create(projectId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['releases', projectId] }),
  });
}

export function useUpdateRelease(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<ProjectRelease> }) =>
      releaseApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['releases', projectId] });
      queryClient.invalidateQueries({ queryKey: ['release-stats'] });
    },
  });
}

export function useDeleteRelease(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => releaseApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['releases', projectId] }),
  });
}

/** 生成发布说明（写回后端，前端刷新详情即可） */
export function useGenerateNotes(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, overwrite = true }: { id: string; overwrite?: boolean }) =>
      releaseApi.generateNotes(id, overwrite),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['releases', projectId] });
    },
  });
}

/** 标记发布（发布是不可逆动作，前端需二次确认） */
export function usePublishRelease(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => releaseApi.publish(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['releases', projectId] });
      queryClient.invalidateQueries({ queryKey: ['release-stats'] });
    },
  });
}
