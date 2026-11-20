import { useQuery, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notificationApi } from '@/lib/api/notification';

// ============================================
// 通知相关 Hooks
// ============================================

/** 获取通知列表 */
export function useNotifications(params?: { page?: number; pageSize?: number; type?: string; unreadOnly?: boolean }) {
  return useQuery({
    queryKey: ['notifications', params],
    queryFn: () => notificationApi.getList(params),
    staleTime: 1000 * 30,
  });
}

export type NotificationListParams = {
  pageSize?: number;
  type?: string;
  unreadOnly?: boolean;
};

/** 通知中心专用累积列表；顶栏继续使用上面的单页 hook。 */
export function useInfiniteNotifications(params?: NotificationListParams) {
  return useInfiniteQuery({
    queryKey: ['notifications', 'infinite', params],
    queryFn: ({ pageParam }) => notificationApi.getList({ ...params, page: pageParam }),
    initialPageParam: 1,
    getNextPageParam: (lastPage) => {
      const result = lastPage.data;
      return result.page < result.totalPages ? result.page + 1 : undefined;
    },
    staleTime: 1000 * 30,
  });
}

/** 获取未读通知数量 */
export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notificationApi.getUnreadCount(),
    refetchInterval: 30000, // 30秒刷新
  });
}

/** 标记通知已读 */
export function useMarkRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => notificationApi.markRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}

/** 标记全部已读 */
export function useMarkAllRead() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => notificationApi.markAllRead(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}
