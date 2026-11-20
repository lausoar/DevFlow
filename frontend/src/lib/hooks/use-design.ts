import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { designApi, type DesignAsset, type DesignStatus } from '@/lib/api/design';

/**
 * 设计稿 Hooks。
 *
 * 评审状态与版本都会影响需求的设计状态与自动化通知，所以所有写操作同时
 * 失效设计稿与需求缓存，避免需求详情里的设计状态停留在旧值。
 */
function useInvalidateDesign(projectId?: string, requirementId?: string) {
  const queryClient = useQueryClient();
  return () => {
    if (requirementId) {
      queryClient.invalidateQueries({ queryKey: ['designs', 'requirement', requirementId] });
    }
    if (projectId) {
      queryClient.invalidateQueries({ queryKey: ['designs', 'project', projectId] });
      queryClient.invalidateQueries({ queryKey: ['requirements', projectId] });
    }
  };
}

export function useRequirementDesigns(requirementId: string | null) {
  return useQuery({
    queryKey: ['designs', 'requirement', requirementId],
    queryFn: () => designApi.listByRequirement(requirementId!),
    enabled: !!requirementId,
  });
}

export function useProjectDesigns(projectId: string | null, status?: string) {
  return useQuery({
    queryKey: ['designs', 'project', projectId, status],
    queryFn: () => designApi.listByProject(projectId!, status),
    enabled: !!projectId,
  });
}

export function useCreateDesign(requirementId: string, projectId?: string) {
  const invalidate = useInvalidateDesign(projectId, requirementId);
  return useMutation({
    mutationFn: (data: Partial<DesignAsset>) => designApi.create(requirementId, data),
    onSuccess: invalidate,
  });
}

export function useChangeDesignStatus(projectId?: string, requirementId?: string) {
  const invalidate = useInvalidateDesign(projectId, requirementId);
  return useMutation({
    mutationFn: ({ id, status }: { id: string; status: DesignStatus }) =>
      designApi.changeStatus(id, status),
    onSuccess: invalidate,
  });
}

export function useAddDesignVersion(projectId?: string, requirementId?: string) {
  const invalidate = useInvalidateDesign(projectId, requirementId);
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: { snapshotUrl?: string; note?: string } }) =>
      designApi.addVersion(id, data),
    onSuccess: invalidate,
  });
}

export function useDeleteDesign(projectId?: string, requirementId?: string) {
  const invalidate = useInvalidateDesign(projectId, requirementId);
  return useMutation({
    mutationFn: (id: string) => designApi.delete(id),
    onSuccess: invalidate,
  });
}

export function useDesignVersions(assetId: string | null) {
  return useQuery({
    queryKey: ['designs', 'versions', assetId],
    queryFn: () => designApi.versions(assetId!),
    enabled: !!assetId,
  });
}
