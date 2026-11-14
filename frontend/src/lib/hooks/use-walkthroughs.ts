import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { walkthroughApi, type Walkthrough, type WalkthroughResult } from '@/lib/api/walkthrough';

// ============================================
// UI 走查 Hooks
// ============================================

export function useWalkthroughs(requirementId: string | null) {
  return useQuery({
    queryKey: ['walkthroughs', requirementId],
    queryFn: () => walkthroughApi.listByRequirement(requirementId!),
    enabled: !!requirementId,
  });
}

export function useWalkthroughDetail(id: string | null) {
  return useQuery({
    queryKey: ['walkthrough-detail', id],
    queryFn: () => walkthroughApi.detail(id!),
    enabled: !!id,
  });
}

export function useCreateWalkthrough(requirementId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<Walkthrough>) => walkthroughApi.create(requirementId, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['walkthroughs', requirementId] }),
  });
}

export function useAddWalkthroughItems(walkthroughId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (titles: string[]) => walkthroughApi.addItems(walkthroughId, titles),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['walkthrough-detail', walkthroughId] });
      queryClient.invalidateQueries({ queryKey: ['walkthroughs'] });
    },
  });
}

export function useSetWalkthroughResult(walkthroughId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, result, note, screenshotUrl }: {
      itemId: string;
      result: WalkthroughResult;
      note?: string;
      screenshotUrl?: string;
    }) => walkthroughApi.setResult(itemId, { result, note, screenshotUrl }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['walkthrough-detail', walkthroughId] });
      queryClient.invalidateQueries({ queryKey: ['walkthroughs'] });
    },
  });
}

export function useConvertWalkthroughItem(walkthroughId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ itemId, note }: { itemId: string; note?: string }) =>
      walkthroughApi.toBug(itemId, note),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['walkthrough-detail', walkthroughId] });
      queryClient.invalidateQueries({ queryKey: ['walkthroughs'] });
      queryClient.invalidateQueries({ queryKey: ['bugs'] });
    },
  });
}

export function useCloseWalkthrough(requirementId: string, walkthroughId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => walkthroughApi.close(walkthroughId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['walkthroughs', requirementId] });
      queryClient.invalidateQueries({ queryKey: ['walkthrough-detail', walkthroughId] });
    },
  });
}
