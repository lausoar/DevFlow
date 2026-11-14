import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { chatApi } from '@/lib/api/chat';
import type { ChatMessage } from '@/types';

// ============================================
// 聊天相关 Hooks
// ============================================

/** 获取频道列表 */
export function useChannels() {
  return useQuery({
    queryKey: ['channels'],
    queryFn: () => chatApi.getChannels(),
    staleTime: 1000 * 60 * 5,
  });
}

/**
 * 获取频道消息（page=1 为最新一页，向后翻页即加载更早历史）
 * 实时性由全局 /topic/chat.events 事件失效缓存提供，轮询仅作断线兜底。
 */
export function useMessages(channelId: string | null, page = 1) {
  return useQuery({
    queryKey: ['messages', channelId, page],
    queryFn: () => chatApi.getMessages(channelId!, page),
    enabled: !!channelId,
    refetchInterval: 15000,
  });
}

/**
 * 向上加载历史：已加载页合并成一个升序列表（去重，避免轮询重复追加）。
 * page=1 是最新一页，页码越大越早。
 */
export function useMessageHistory(channelId: string | null, loadedPages: number) {
  const pages = Math.max(1, loadedPages);
  return useQuery({
    queryKey: ['message-history', channelId, pages],
    queryFn: async () => {
      const results = await Promise.all(
        Array.from({ length: pages }, (_, i) => chatApi.getMessages(channelId!, i + 1)),
      );
      const byId = new Map<string, ChatMessage>();
      let total = 0;
      for (const res of results) {
        total = Math.max(total, res?.data?.total ?? 0);
        for (const msg of res?.data?.records ?? []) {
          byId.set(msg.id, msg);
        }
      }
      const records = Array.from(byId.values()).sort(
        (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime(),
      );
      return { records, total };
    },
    enabled: !!channelId,
    refetchInterval: 15000,
  });
}

/** 发送消息 */
export function useSendMessage(channelId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { content: string; type?: string; replyToId?: string }) =>
      chatApi.sendMessage(channelId, data),
    onSuccess: () => {
      // 发送成功后刷新消息列表
      queryClient.invalidateQueries({ queryKey: ['messages', channelId] });
    },
  });
}
