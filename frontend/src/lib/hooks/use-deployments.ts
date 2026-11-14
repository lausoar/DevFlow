import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { deploymentApi, type DeployEnvironment, type Deployment } from '@/lib/api/deployment';

// ============================================
// 部署 Hooks
// ============================================

export function useEnvironments(projectId: string | null) {
  return useQuery({
    queryKey: ['environments', projectId],
    queryFn: () => deploymentApi.listEnvironments(projectId!),
    enabled: !!projectId,
  });
}

export function useDeployments(
  projectId: string | null,
  params?: { environmentId?: string; releaseId?: string; limit?: number },
) {
  return useQuery({
    queryKey: ['deployments', projectId, params],
    queryFn: () => deploymentApi.listDeployments(projectId!, params),
    enabled: !!projectId,
    refetchInterval: 30 * 1000, // 部署状态会变化，列表轮询刷新
  });
}

export function useDoraMetrics(projectId: string | null, days = 30) {
  return useQuery({
    queryKey: ['dora', projectId, days],
    queryFn: () => deploymentApi.dora(projectId!, days),
    enabled: !!projectId,
  });
}

export function useCreateEnvironment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<DeployEnvironment>) => deploymentApi.createEnvironment(projectId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['environments', projectId] }),
  });
}

export function useCreateDeployment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ environmentId, data }: { environmentId: string; data: Partial<Deployment> }) =>
      deploymentApi.createDeployment(projectId, environmentId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['deployments', projectId] }),
  });
}

function invalidateDeployments(queryClient: ReturnType<typeof useQueryClient>, projectId: string) {
  queryClient.invalidateQueries({ queryKey: ['deployments', projectId] });
  queryClient.invalidateQueries({ queryKey: ['dora', projectId] });
}

export function useApproveDeployment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) => deploymentApi.approve(id, note),
    onSuccess: () => invalidateDeployments(queryClient, projectId),
  });
}

export function useRejectDeployment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) => deploymentApi.reject(id, note),
    onSuccess: () => invalidateDeployments(queryClient, projectId),
  });
}

export function useStartDeployment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deploymentApi.start(id),
    onSuccess: () => invalidateDeployments(queryClient, projectId),
  });
}

export function useFinishDeployment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, success, note }: { id: string; success: boolean; note?: string }) =>
      deploymentApi.finish(id, success, note),
    onSuccess: () => invalidateDeployments(queryClient, projectId),
  });
}

export function useRollbackDeployment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) => deploymentApi.rollback(id, note),
    onSuccess: () => invalidateDeployments(queryClient, projectId),
  });
}
