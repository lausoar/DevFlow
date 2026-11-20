import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { bugApi } from '@/lib/api/bug';
import { notificationApi } from '@/lib/api/notification';
import { taskApi } from '@/lib/api/task';
import type { ApiResponse, Bug, Notification, PageQuery, PageResult, Task } from '@/types';
import { useInfiniteBugs } from './use-bugs';
import { useInfiniteNotifications } from './use-notifications';
import { useInfiniteTasks } from './use-tasks';

vi.mock('@/lib/api/task', () => ({ taskApi: { getByProject: vi.fn() } }));
vi.mock('@/lib/api/bug', () => ({ bugApi: { getByProject: vi.fn() } }));
vi.mock('@/lib/api/notification', () => ({ notificationApi: { getList: vi.fn() } }));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

function page<T>(records: T[], requestedPage: number, total: number, pageSize = 2): ApiResponse<PageResult<T>> {
  return {
    code: 200,
    message: 'ok',
    data: {
      records,
      total,
      page: requestedPage,
      pageSize,
      totalPages: Math.ceil(total / pageSize),
    },
  };
}

function setup() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { wrapper };
}

const task = (id: string) => ({ id, title: id } as Task);
const bug = (id: string) => ({ id, title: id } as Bug);
const notification = (id: string) => ({ id, title: id } as Notification);

describe('cumulative project and notification pagination', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('fetches every task page with next-page pending state and API metadata', async () => {
    const secondPage = deferred<ApiResponse<PageResult<Task>>>();
    vi.mocked(taskApi.getByProject).mockImplementation(async (_projectId, params) => {
      if (params?.page === 2) return secondPage.promise;
      return page([task('task-1'), task('task-2')], 1, 3);
    });
    const { wrapper } = setup();
    const { result } = renderHook(() => ({ ...useInfiniteTasks('project-1', { keyword: 'task' }) }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.hasNextPage).toBe(true);
    expect(taskApi.getByProject).toHaveBeenCalledWith('project-1', { keyword: 'task', page: 1 });

    let request!: ReturnType<typeof result.current.fetchNextPage>;
    await act(async () => {
      request = result.current.fetchNextPage();
      await Promise.resolve();
    });
    await waitFor(() => expect(result.current.isFetchingNextPage).toBe(true));
    expect(taskApi.getByProject).toHaveBeenCalledWith('project-1', { keyword: 'task', page: 2 });

    await act(async () => {
      secondPage.resolve(page([task('task-3')], 2, 3));
      await request;
    });
    await waitFor(() => {
      expect(result.current.data?.pages.flatMap((item) => item.data.records)).toHaveLength(3);
      expect(result.current.hasNextPage).toBe(false);
      expect(result.current.isFetchingNextPage).toBe(false);
    });
  });

  it('resets accumulated task pages when the filter changes', async () => {
    vi.mocked(taskApi.getByProject).mockImplementation(async (_projectId, params) => {
      if (params?.keyword === 'new') return page([task('new-match')], 1, 1);
      return page([task('old-match')], params?.page ?? 1, 1);
    });
    const { wrapper } = setup();
    const { result, rerender } = renderHook(
      ({ keyword }) => ({ ...useInfiniteTasks('project-1', { keyword }) }),
      { wrapper, initialProps: { keyword: 'old' } },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    rerender({ keyword: 'new' });

    await waitFor(() => {
      expect(result.current.data?.pages[0]?.data.records[0]?.id).toBe('new-match');
    });
    expect(result.current.data?.pages).toHaveLength(1);
    expect(taskApi.getByProject).toHaveBeenLastCalledWith('project-1', { keyword: 'new', page: 1 });
  });

  it('accumulates bug pages and forwards keyword/status filters on every request', async () => {
    vi.mocked(bugApi.getByProject).mockImplementation(async (_projectId, params) => {
      const requestedPage = params?.page ?? 1;
      return requestedPage === 1
        ? page([bug('bug-1'), bug('bug-2')], 1, 3)
        : page([bug('bug-3')], 2, 3);
    });
    const { wrapper } = setup();
    const { result } = renderHook(
      () => ({ ...useInfiniteBugs('project-1', { keyword: 'crash', status: 'NEW' }) }),
      { wrapper },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    await act(async () => {
      await result.current.fetchNextPage();
      await Promise.resolve();
    });

    await waitFor(() => {
      expect(result.current.data?.pages.flatMap((item) => item.data.records)).toHaveLength(3);
      expect(result.current.hasNextPage).toBe(false);
    });
    for (const call of vi.mocked(bugApi.getByProject).mock.calls) {
      expect(call[1]).toMatchObject({ keyword: 'crash', status: 'NEW' });
    }
    expect(vi.mocked(bugApi.getByProject).mock.calls.map((call) => (call[1] as PageQuery).page)).toEqual([1, 2]);
  });

  it('resets notification pages for filters and reaches the final page', async () => {
    vi.mocked(notificationApi.getList).mockImplementation(async (params) => {
      if (params?.unreadOnly) return page([notification('unread')], 1, 1, 20);
      const requestedPage = params?.page ?? 1;
      return requestedPage === 1
        ? page([notification('notice-1')], 1, 2, 1)
        : page([notification('notice-2')], 2, 2, 1);
    });
    const { wrapper } = setup();
    const { result, rerender } = renderHook(
      ({ unreadOnly }) => ({ ...useInfiniteNotifications({ pageSize: 20, unreadOnly }) }),
      { wrapper, initialProps: { unreadOnly: false } },
    );
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    await act(async () => {
      await result.current.fetchNextPage();
      await Promise.resolve();
    });
    await waitFor(() => {
      expect(result.current.data?.pages).toHaveLength(2);
      expect(result.current.hasNextPage).toBe(false);
    });

    rerender({ unreadOnly: true });

    await waitFor(() => {
      expect(result.current.data?.pages[0]?.data.records[0]?.id).toBe('unread');
    });
    expect(result.current.data?.pages).toHaveLength(1);
    expect(notificationApi.getList).toHaveBeenLastCalledWith({ pageSize: 20, unreadOnly: true, page: 1 });
  });
});
