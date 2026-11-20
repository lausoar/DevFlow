import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { objectiveApi, type Objective } from '@/lib/api/objective';

/**
 * 目标 Hooks。汇总数据依赖项目/任务/工时，写操作同时失效项目缓存，
 * 避免"项目刚挂到目标、目标详情还是旧列表"。
 */
export function useObjectives(enterpriseId: string | null) {
  return useQuery({
    queryKey: ['objectives', enterpriseId],
    queryFn: () => objectiveApi.list(enterpriseId!),
    enabled: !!enterpriseId,
  });
}

export function useObjectiveRollup(id: string | null) {
  return useQuery({
    queryKey: ['objective-rollup', id],
    queryFn: () => objectiveApi.rollup(id!),
    enabled: !!id,
  });
}

export function useCreateObjective(enterpriseId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<Objective>) => objectiveApi.create(enterpriseId!, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['objectives', enterpriseId] }),
  });
}

export function useUpdateObjective(enterpriseId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<Objective> }) =>
      objectiveApi.update(id, data),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: ['objectives', enterpriseId] });
      queryClient.invalidateQueries({ queryKey: ['objective-rollup', variables.id] });
    },
  });
}

export function useDeleteObjective(enterpriseId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => objectiveApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['objectives', enterpriseId] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

export function useAssignProjectToObjective(enterpriseId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ projectId, objectiveId }: { projectId: string; objectiveId: string | null }) =>
      objectiveApi.assignProject(projectId, objectiveId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['objectives', enterpriseId] });
      queryClient.invalidateQueries({ queryKey: ['objective-rollup'] });
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}
