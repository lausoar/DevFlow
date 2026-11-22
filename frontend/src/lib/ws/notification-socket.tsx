'use client';

import { useEffect, useRef } from 'react';
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/stores';
import { usePresenceStore } from '@/stores/presence-store';
import { useChatUnreadStore } from '@/stores/chat-unread-store';
import { useChannels } from '@/lib/hooks/use-chat';
import { isTemporaryEnterpriseError, useCurrentEnterprise } from '@/lib/hooks/use-enterprise';
import { request } from '@/lib/api/client';

// 后端 WebSocket 端点（SockJS）位于 servlet context-path 之下，
// 即 http://localhost:8080/api/v1/ws —— 不能去掉 /api/v1 前缀
function getWsUrl(): string {
  const base = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1';
  return `${base.replace(/\/$/, '')}/ws`;
}

/**
 * 通知实时推送监听器
 * 订阅 /user/queue/notifications，收到新通知即刷新 React Query 缓存，
 * 从而实时更新通知中心列表与侧边栏/顶栏未读角标。
 */
export function NotificationSocket() {
  const queryClient = useQueryClient();
  const accessToken = useAuthStore((s) => s.accessToken);
  const persistedEnterpriseId = useAuthStore((s) => s.user?.enterpriseId);
  const currentEnterpriseQuery = useCurrentEnterprise();
  // 服务端企业优先；只有临时查询故障才降级到登录缓存。
  const enterpriseId = currentEnterpriseQuery.data?.data?.id ?? (
    currentEnterpriseQuery.isError && isTemporaryEnterpriseError(currentEnterpriseQuery.error)
      ? persistedEnterpriseId
      : undefined
  );
  const clientRef = useRef<Client | null>(null);
  const syncChatSubscriptionsRef = useRef<() => void>(() => {});
  const { data: channelsRes } = useChannels();
  const visibleChannelIds = (channelsRes?.data || []).map((channel) => channel.id).sort();
  const visibleChannelIdsRef = useRef<string[]>([]);
  const visibleChannelIdsKey = visibleChannelIds.join('\0');

  // 频道可能在 STOMP 连接建立后才返回；列表变化时增量订阅/退订。
  useEffect(() => {
    visibleChannelIdsRef.current = visibleChannelIdsKey ? visibleChannelIdsKey.split('\0') : [];
    syncChatSubscriptionsRef.current();
  }, [visibleChannelIdsKey]);

  useEffect(() => {
    // 未登录或 token 无效时不建立连接
    if (!accessToken || accessToken === 'mock-jwt-token') return;

    let active = true;
    let presenceRevision = 0;
    let presenceSnapshotRequest = 0;
    let presenceRetryAttempt = 0;
    let presenceRetryTimer: ReturnType<typeof setTimeout> | null = null;
    const clearPresenceRetry = () => {
      if (presenceRetryTimer !== null) clearTimeout(presenceRetryTimer);
      presenceRetryTimer = null;
    };
    const loadPresenceSnapshot = () => {
      if (!active || !enterpriseId) return;
      clearPresenceRetry();
      const revision = presenceRevision;
      const requestId = ++presenceSnapshotRequest;
      request<{ onlineUserIds: string[] }>('GET', '/presence', undefined, { enterpriseId })
        .then((res) => {
          if (!active || requestId !== presenceSnapshotRequest) return;
          if (!Array.isArray(res.data?.onlineUserIds)) throw new Error('Invalid presence snapshot');
          presenceRetryAttempt = 0;
          if (revision === presenceRevision) {
            usePresenceStore.getState().setOnlineUserIds(res.data.onlineUserIds);
          }
        })
        .catch(() => {
          if (!active || requestId !== presenceSnapshotRequest || revision !== presenceRevision) return;
          const delay = Math.min(5000 * 2 ** presenceRetryAttempt, 60000);
          presenceRetryAttempt += 1;
          presenceRetryTimer = setTimeout(() => {
            presenceRetryTimer = null;
            loadPresenceSnapshot();
          }, delay);
        });
    };

    // 广播不可重放，定期校准以恢复订阅激活间隙或断线期间错过的变化。
    const presenceReconcileTimer = setInterval(loadPresenceSnapshot, 30000);
    const chatSubscriptions = new Map<string, StompSubscription>();
    const handleChatEvent = (message: IMessage) => {
      try {
        const body = JSON.parse(message.body) as { channelId?: string; senderId?: string };
        if (!body.channelId || !visibleChannelIdsRef.current.includes(body.channelId)) return;
        const me = useAuthStore.getState().user?.id;
        if (!me || body.senderId === me) return;
        useChatUnreadStore.getState().increment(body.channelId);
        // 正在查看的频道由本事件即时刷新消息列表（轮询仍作断线兜底）
        queryClient.invalidateQueries({ queryKey: ['message-history', body.channelId] });
      } catch {
        // 忽略非法消息
      }
    };

    const client = new Client({
      webSocketFactory: () => new SockJS(getWsUrl()),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 5000,
      onWebSocketClose: loadPresenceSnapshot,
      onStompError: loadPresenceSnapshot,
      onConnect: () => {
        client.subscribe('/user/queue/notifications', () => {
          // 收到实时通知，刷新通知相关查询
          queryClient.invalidateQueries({ queryKey: ['notifications'] });
        });
        // 只有明确属于某企业的用户才订阅该企业在线名单。
        if (enterpriseId) {
          client.subscribe(`/topic/presence.${enterpriseId}`, (message: IMessage) => {
            try {
              const body = JSON.parse(message.body) as {
                enterpriseId?: string;
                onlineUserIds?: unknown;
              };
              if (!active || body.enterpriseId && body.enterpriseId !== enterpriseId) {
                return;
              }
              // 空数组是有效的“当前无人在线”广播，不能当作无效消息忽略。
              if (Array.isArray(body.onlineUserIds)) {
                presenceRevision += 1;
                presenceRetryAttempt = 0;
                clearPresenceRetry();
                usePresenceStore.getState().setOnlineUserIds(body.onlineUserIds);
              }
            } catch {
              // 忽略非法消息
            }
          });
          loadPresenceSnapshot();
        }

        const syncChatSubscriptions = () => {
          if (!client.connected) return;
          const visibleIds = new Set(visibleChannelIdsRef.current);
          for (const [channelId, subscription] of chatSubscriptions) {
            if (!visibleIds.has(channelId)) {
              subscription.unsubscribe();
              chatSubscriptions.delete(channelId);
            }
          }
          for (const channelId of visibleIds) {
            if (!chatSubscriptions.has(channelId)) {
              chatSubscriptions.set(
                channelId,
                client.subscribe(`/topic/chat.events.${channelId}`, handleChatEvent),
              );
            }
          }
        };
        syncChatSubscriptionsRef.current = syncChatSubscriptions;
        syncChatSubscriptions();
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      active = false;
      clearInterval(presenceReconcileTimer);
      clearPresenceRetry();
      syncChatSubscriptionsRef.current = () => {};
      for (const subscription of chatSubscriptions.values()) subscription.unsubscribe();
      chatSubscriptions.clear();
      client.deactivate();
      clientRef.current = null;
    };
  }, [accessToken, queryClient, enterpriseId]);

  return null;
}
