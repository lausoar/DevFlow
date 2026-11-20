import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { PropsWithChildren } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { notificationApi } from '@/lib/api/notification';
import type { ApiResponse, Notification, PageResult } from '@/types';
import NotificationsContent from './notifications-content';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));
vi.mock('@/lib/api/notification', () => ({
  notificationApi: {
    getList: vi.fn(),
    getUnreadCount: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  },
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

function notification(id: string): Notification {
  return {
    id,
    userId: 'user-1',
    type: 'SYSTEM',
    title: id,
    content: 'content',
    isRead: false,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

function page(id: string, requestedPage: number): ApiResponse<PageResult<Notification>> {
  return {
    code: 200,
    message: 'ok',
    data: {
      records: [notification(id)],
      total: 2,
      page: requestedPage,
      pageSize: 1,
      totalPages: 2,
    },
  };
}

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { wrapper };
}

describe('NotificationsContent pagination and global unread state', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(notificationApi.getUnreadCount).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { count: 7 },
    });
  });

  it('uses the global badge count and exposes pending states for later pages and mark-all', async () => {
    const user = userEvent.setup();
    const secondPage = deferred<ApiResponse<PageResult<Notification>>>();
    const markAll = deferred<ApiResponse<void>>();
    vi.mocked(notificationApi.getList).mockImplementation(async (params) => {
      if (params?.page === 2) return secondPage.promise;
      return page('notice-1', 1);
    });
    vi.mocked(notificationApi.markAllRead).mockReturnValue(markAll.promise);
    const { wrapper } = setup();
    render(<NotificationsContent />, { wrapper });

    expect(await screen.findByText('notice-1')).toBeInTheDocument();
    expect(screen.getByText('7 条未读')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '全部已读' })).toBeEnabled();
    expect(screen.getByText('已加载 1 / 2 条，剩余 1 条')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '加载更多' }));
    expect(screen.getByRole('button', { name: '加载中...' })).toBeDisabled();
    expect(notificationApi.getList).toHaveBeenCalledWith({
      pageSize: 20,
      type: undefined,
      unreadOnly: false,
      page: 2,
    });

    await act(async () => {
      secondPage.resolve(page('notice-2', 2));
      await secondPage.promise;
    });
    expect(await screen.findByText('notice-2')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '加载更多' })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '全部已读' }));
    expect(screen.getByRole('button', { name: '处理中...' })).toBeDisabled();
    await act(async () => {
      markAll.resolve({ code: 200, message: 'ok', data: undefined });
      await markAll.promise;
    });
    await waitFor(() => expect(screen.getByRole('button', { name: '全部已读' })).toBeEnabled());
  });

  it('disables mark-all from the global count even when a loaded row is unread', async () => {
    vi.mocked(notificationApi.getUnreadCount).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { count: 0 },
    });
    vi.mocked(notificationApi.getList).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { records: [notification('unread-row')], total: 1, page: 1, pageSize: 20, totalPages: 1 },
    });
    const { wrapper } = setup();
    render(<NotificationsContent />, { wrapper });

    expect(await screen.findByText('unread-row')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '全部已读' })).toBeDisabled();
  });
});
