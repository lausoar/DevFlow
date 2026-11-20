import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { triageApi, type TriageStatus } from '@/lib/api/triage';

/**
 * 分诊 Hooks。分诊动作会改写 Bug / 反馈状态，因此同时失效这些缓存，
 * 避免"收件箱里已接受、缺陷列表还是空的"。
 */
function invalidateTriage(queryClient: ReturnType<typeof useQueryClient>, projectId: string) {
  queryClient.invalidateQueries({ queryKey: ['triage', projectId] });
  queryClient.invalidateQueries({ queryKey: ['triage-counts', projectId] });
  queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
  queryClient.invalidateQueries({ queryKey: ['project-feedback', projectId] });
  queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
}

export function useTriageItems(projectId: string | null, status?: TriageStatus) {
  return useQuery({
    queryKey: ['triage', projectId, status],
    queryFn: () => triageApi.list(projectId!, status),
    enabled: !!projectId,
  });
}

export function useTriageCounts(projectId: string | null) {
  return useQuery({
    queryKey: ['triage-counts', projectId],
    queryFn: () => triageApi.counts(projectId!),
    enabled: !!projectId,
  });
}

export function useTriageSimilar(itemId: string | null) {
  return useQuery({
    queryKey: ['triage-similar', itemId],
    queryFn: () => triageApi.similar(itemId!),
    enabled: !!itemId,
  });
}

export function useAcceptTriage(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => triageApi.accept(id),
    onSuccess: () => invalidateTriage(queryClient, projectId),
  });
}

export function useDeclineTriage(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason?: string }) => triageApi.decline(id, reason),
    onSuccess: () => invalidateTriage(queryClient, projectId),
  });
}

export function useMergeTriage(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, targetBugId }: { id: string; targetBugId: string }) =>
      triageApi.merge(id, targetBugId),
    onSuccess: () => invalidateTriage(queryClient, projectId),
  });
}
