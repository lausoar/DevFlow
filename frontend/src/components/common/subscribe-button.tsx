'use client';

import { Button } from '@/components/ui/button';
import { Bell, BellOff, Loader2 } from 'lucide-react';
import { useSubscriptionStatus, useToggleSubscription } from '@/lib/hooks/use-subscription';
import type { SubscriptionTarget } from '@/lib/api/subscription';

/**
 * 关注 / 取消关注按钮。
 *
 * 说明文案刻意区分了"自动关注"：被指派或作为创建人时会被自动加入关注，
 * 此时按钮显示"已关注"——用户看到这个状态才知道"原来我会收到它的通知"。
 */
export function SubscribeButton({
  targetType,
  targetId,
  size = 'sm',
}: {
  targetType: SubscriptionTarget;
  targetId: string | null;
  size?: 'sm' | 'default';
}) {
  const { data: res, isLoading } = useSubscriptionStatus(targetType, targetId);
  const toggle = useToggleSubscription(targetType, targetId);
  const subscribed = !!res?.data?.subscribed;

  if (!targetId) return null;

  if (isLoading) {
    return (
      <Button variant="outline" size={size} disabled className="gap-1.5">
        <Loader2 className="h-3.5 w-3.5 animate-spin" />
      </Button>
    );
  }

  return (
    <Button
      variant={subscribed ? 'secondary' : 'outline'}
      size={size}
      className="gap-1.5"
      onClick={() => toggle.mutate(!subscribed)}
      disabled={toggle.isPending}
      title={subscribed ? '已关注：该工作项的变更会通知你' : '关注后该工作项的变更会通知你'}
    >
      {subscribed ? <Bell className="h-3.5 w-3.5" /> : <BellOff className="h-3.5 w-3.5" />}
      {subscribed ? '已关注' : '关注'}
    </Button>
  );
}
