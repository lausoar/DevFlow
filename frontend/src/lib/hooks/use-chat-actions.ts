import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { chatActionApi } from '@/lib/api/chat-actions';

// ============================================
// 消息动作 Hooks
// ============================================

export function useEditMessage(channelId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ messageId, content }: { messageId: string; content: string }) =>
      chatActionApi.edit(messageId, content),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['message-history', channelId] }),
  });
}

export function useRecallMessage(channelId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (messageId: string) => chatActionApi.recall(messageId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['message-history', channelId] }),
  });
}

export function useToggleReaction(channelId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ messageId, emoji }: { messageId: string; emoji: string }) =>
      chatActionApi.toggleReaction(messageId, emoji),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['message-history', channelId] }),
  });
}

export function useMarkRead(channelId: string | null) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => chatActionApi.markRead(channelId!),
    onSuccess: () => {
      if (channelId) {
        queryClient.invalidateQueries({ queryKey: ['channel-unread', channelId] });
      }
    },
  });
}

export function useChannelUnread(channelId: string | null) {
  return useQuery({
    queryKey: ['channel-unread', channelId],
    queryFn: () => chatActionApi.unread(channelId!),
    enabled: !!channelId,
  });
}
