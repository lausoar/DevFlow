import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { chatApi } from '@/lib/api/chat';
import { useAuthStore } from '@/stores';
import { useChatUnreadStore } from '@/stores/chat-unread-store';
import { usePresenceStore } from '@/stores/presence-store';
import type { ApiResponse, ChatChannel, User } from '@/types';
import { NotificationSocket } from './notification-socket';

const stomp = vi.hoisted(() => {
  const callbacks = new Map<string, (message: { body: string }) => void>();
  const subscriptions = new Map<string, { unsubscribe: ReturnType<typeof vi.fn> }>();
  const client = {
    connected: false,
    activate: vi.fn(),
    deactivate: vi.fn(),
    subscribe: vi.fn((topic: string, callback: (message: { body: string }) => void) => {
      callbacks.set(topic, callback);
      const subscription = {
        unsubscribe: vi.fn(() => {
          callbacks.delete(topic);
        }),
      };
      subscriptions.set(topic, subscription);
      return subscription;
    }),
  };
  return {
    client,
    callbacks,
    subscriptions,
    config: undefined as { onConnect?: () => void } | undefined,
  };
});

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn(function (config: { onConnect?: () => void }) {
    stomp.config = config;
    return stomp.client;
  }),
}));
vi.mock('sockjs-client', () => ({ default: vi.fn() }));
vi.mock('@/lib/api/chat', () => ({
  chatApi: { getChannels: vi.fn() },
}));

function channel(id: string): ChatChannel {
  return { id, name: id, type: 'PROJECT', projectId: `project-${id}`, memberIds: [], unreadCount: 0 };
}

function channelsResponse(channels: ChatChannel[]): ApiResponse<ChatChannel[]> {
  return { code: 200, message: 'ok', data: channels };
}

function user(enterpriseId?: string): User {
  return {
    id: 'user-1',
    username: 'member',
    email: 'member@example.com',
    role: 'MEMBER',
    status: 'ACTIVE',
    enterpriseId,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

function setup() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { queryClient, wrapper };
}

describe('NotificationSocket channel-scoped chat events', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    stomp.callbacks.clear();
    stomp.subscriptions.clear();
    stomp.client.connected = false;
    stomp.config = undefined;
    useChatUnreadStore.setState({ unreadMap: {}, activeChannelId: null });
    usePresenceStore.setState({ onlineUserIds: ['previous-user'] });
  });

  it('subscribes after channels load, unsubscribes removed channels, and invalidates message-history', async () => {
    const pendingChannels = deferred<ApiResponse<ChatChannel[]>>();
    vi.mocked(chatApi.getChannels).mockReturnValue(pendingChannels.promise);
    useAuthStore.getState().setAuth(user('enterprise-1'), 'access-token', 'refresh-token');
    const { queryClient, wrapper } = setup();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    render(<NotificationSocket />, { wrapper });

    stomp.client.connected = true;
    act(() => {
      stomp.config?.onConnect?.();
    });
    expect(stomp.client.subscribe).toHaveBeenCalledWith('/user/queue/notifications', expect.any(Function));
    expect(stomp.client.subscribe).toHaveBeenCalledWith('/topic/presence.enterprise-1', expect.any(Function));
    expect(stomp.client.subscribe).not.toHaveBeenCalledWith('/topic/chat.events.channel-1', expect.any(Function));

    await act(async () => {
      pendingChannels.resolve(channelsResponse([channel('channel-1'), channel('channel-2')]));
      await pendingChannels.promise;
    });
    await waitFor(() => {
      expect(stomp.callbacks.has('/topic/chat.events.channel-1')).toBe(true);
      expect(stomp.callbacks.has('/topic/chat.events.channel-2')).toBe(true);
    });
    expect(stomp.callbacks.has('/topic/chat.events')).toBe(false);

    act(() => {
      queryClient.setQueryData(['channels'], channelsResponse([channel('channel-2')]));
    });
    await waitFor(() => {
      expect(stomp.subscriptions.get('/topic/chat.events.channel-1')?.unsubscribe).toHaveBeenCalled();
    });

    act(() => {
      stomp.callbacks.get('/topic/chat.events.channel-2')?.({
        body: JSON.stringify({ channelId: 'channel-2', senderId: 'user-2' }),
      });
    });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['message-history', 'channel-2'] });
    expect(useChatUnreadStore.getState().unreadMap['channel-2']).toBe(1);

    act(() => {
      stomp.callbacks.get('/topic/presence.enterprise-1')?.({
        body: JSON.stringify({ onlineUserIds: [] }),
      });
    });
    expect(usePresenceStore.getState().onlineUserIds).toEqual([]);
  });

  it('does not subscribe to presence without enterprise membership', async () => {
    vi.mocked(chatApi.getChannels).mockResolvedValue(channelsResponse([]));
    useAuthStore.getState().setAuth(user(), 'access-token', 'refresh-token');
    const { wrapper } = setup();
    render(<NotificationSocket />, { wrapper });

    stomp.client.connected = true;
    act(() => {
      stomp.config?.onConnect?.();
    });

    expect(stomp.client.subscribe).toHaveBeenCalledWith('/user/queue/notifications', expect.any(Function));
    expect(stomp.client.subscribe).not.toHaveBeenCalledWith(
      expect.stringContaining('/topic/presence.'),
      expect.any(Function),
    );
  });
});
