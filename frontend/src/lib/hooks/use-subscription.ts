import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { subscriptionApi, type SubscriptionTarget } from '@/lib/api/subscription';

// ============================================
// 工作项关注 Hooks
// ============================================

export function useSubscriptionStatus(targetType: SubscriptionTarget, targetId: string | null) {
  return useQuery({
    queryKey: ['subscription-status', targetType, targetId],
    queryFn: () => subscriptionApi.status(targetType, targetId!),
    enabled: !!targetId,
  });
}

export function useToggleSubscription(targetType: SubscriptionTarget, targetId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (subscribe: boolean) => {
      if (!targetId) return;
      if (subscribe) {
        await subscriptionApi.subscribe(targetType, targetId);
      } else {
        await subscriptionApi.unsubscribe(targetType, targetId);
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subscription-status', targetType, targetId] });
      queryClient.invalidateQueries({ queryKey: ['subscriptions'] });
    },
  });
}
