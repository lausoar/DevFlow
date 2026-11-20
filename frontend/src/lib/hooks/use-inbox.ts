import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deploymentApprovalApi, inboxApi } from '@/lib/api/inbox';

/** 收件箱：轮询 + 动作后失效（没有服务端推送时也能在 60s 内看到新事项） */
export function useInbox() {
  return useQuery({
    queryKey: ['inbox'],
    queryFn: () => inboxApi.get(),
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
}

export function useApproveDeployment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) => deploymentApprovalApi.approve(id, note),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inbox'] });
      queryClient.invalidateQueries({ queryKey: ['deployments'] });
    },
  });
}

export function useRejectDeployment() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) => deploymentApprovalApi.reject(id, note),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inbox'] });
      queryClient.invalidateQueries({ queryKey: ['deployments'] });
    },
  });
}
