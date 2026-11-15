import { request } from './client';

// ============================================
// 工作项关注 API
// ============================================

export type SubscriptionTarget = 'TASK' | 'BUG' | 'REQUIREMENT';

export interface SubscriptionItem {
  targetType: string;
  targetId: string;
  projectId?: string | null;
  auto: number;
  createdAt?: string;
  title?: string;
}

export const subscriptionApi = {
  list: (targetType?: SubscriptionTarget) =>
    request<SubscriptionItem[]>('GET', '/subscriptions', undefined, targetType ? { targetType } : undefined),

  subscribe: (targetType: SubscriptionTarget, targetId: string) =>
    request<void>('POST', '/subscriptions', { targetType, targetId }),

  unsubscribe: (targetType: SubscriptionTarget, targetId: string) =>
    request<void>('DELETE', '/subscriptions', undefined, { targetType, targetId }),

  status: (targetType: SubscriptionTarget, targetId: string) =>
    request<{ subscribed: boolean }>('GET', '/subscriptions/status', undefined, { targetType, targetId }),
};
