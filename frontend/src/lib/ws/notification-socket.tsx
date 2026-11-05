'use client';

import { useEffect, useRef } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthStore } from '@/stores';
import { usePresenceStore } from '@/stores/presence-store';
import { useChatUnreadStore } from '@/stores/chat-unread-store';

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
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    // 未登录或 token 无效时不建立连接
    if (!accessToken || accessToken === 'mock-jwt-token') return;

    const client = new Client({
      webSocketFactory: () => new SockJS(getWsUrl()),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe('/user/queue/notifications', () => {
          // 收到实时通知，刷新通知相关查询
          queryClient.invalidateQueries({ queryKey: ['notifications'] });
        });
        // 订阅全站在线名单（连接/断开时后端广播）
        client.subscribe('/topic/presence', (message: IMessage) => {
          try {
            const body = JSON.parse(message.body);
            if (Array.isArray(body.onlineUserIds)) {
              usePresenceStore.getState().setOnlineUserIds(body.onlineUserIds);
            }
          } catch {
            // 忽略非法消息
          }
        });
        // 订阅轻量聊天事件（任意页面都能感知新消息并维护未读角标）
        client.subscribe('/topic/chat.events', (message: IMessage) => {
          try {
            const body = JSON.parse(message.body) as { channelId?: string; senderId?: string };
            if (!body.channelId) return;
            const me = useAuthStore.getState().user?.id;
            if (!me || body.senderId === me) return;
            // 只统计自己所在频道的未读（channels 缓存即我的频道清单）
            const channels = queryClient.getQueryData<{ data?: { id: string }[] }>(['channels']);
            const list = channels?.data;
            if (list && !list.some((c) => c.id === body.channelId)) return;
            useChatUnreadStore.getState().increment(body.channelId);
            // 正在查看的频道由本事件即时刷新消息列表（替代轮询的实时性来源）
            queryClient.invalidateQueries({ queryKey: ['messages', body.channelId] });
          } catch {
            // 忽略非法消息
          }
        });
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [accessToken, queryClient]);

  return null;
}
