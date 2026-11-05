import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { chatApi } from '@/lib/api/chat';

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

/** 获取频道消息 */
export function useMessages(channelId: string | null, page = 1) {
  return useQuery({
    queryKey: ['messages', channelId, page],
    queryFn: () => chatApi.getMessages(channelId!, page),
    enabled: !!channelId,
    // 实时性由全局 /topic/chat.events 事件失效缓存提供，此处仅作断线兜底
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
