import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { chatApi } from '@/lib/api/chat';
import type { ApiResponse, ChatMessage, PageResult } from '@/types';
import { useMessageHistory, useSendMessage } from './use-chat';

vi.mock('@/lib/api/chat', () => ({
  chatApi: {
    getMessages: vi.fn(),
    sendMessage: vi.fn(),
  },
}));

function message(id: string, createdAt: string): ChatMessage {
  return {
    id,
    channelId: 'channel-1',
    senderId: 'user-2',
    senderName: 'Sender',
    content: id,
    type: 'TEXT',
    isEdited: false,
    isDeleted: false,
    mentions: [],
    attachments: [],
    createdAt,
  };
}

function page(records: ChatMessage[]): ApiResponse<PageResult<ChatMessage>> {
  return {
    code: 200,
    message: 'ok',
    data: { records, total: records.length, page: 1, pageSize: 50, totalPages: 1 },
  };
}

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
  return { queryClient, wrapper };
}

describe('chat message-history cache', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('stores the rendered cumulative history under message-history/channelId/pages', async () => {
    vi.mocked(chatApi.getMessages).mockImplementation(async (_channelId, requestedPage) =>
      page([message(`page-${requestedPage}`, `2026-01-0${requestedPage}T00:00:00Z`)]),
    );
    const { queryClient, wrapper } = setup();
    const { result } = renderHook(() => useMessageHistory('channel-1', 2), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(queryClient.getQueryData(['message-history', 'channel-1', 2])).toEqual({
      records: [
        message('page-1', '2026-01-01T00:00:00Z'),
        message('page-2', '2026-01-02T00:00:00Z'),
      ],
      total: 1,
    });
    expect(chatApi.getMessages).toHaveBeenCalledWith('channel-1', 1);
    expect(chatApi.getMessages).toHaveBeenCalledWith('channel-1', 2);
  });

  it('invalidates the rendered message-history key after a successful send', async () => {
    vi.mocked(chatApi.sendMessage).mockResolvedValue({
      code: 200,
      message: 'ok',
      data: message('new', '2026-01-03T00:00:00Z'),
    });
    const { queryClient, wrapper } = setup();
    const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
    const { result } = renderHook(() => useSendMessage('channel-1'), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ content: 'hello' });
    });

    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['message-history', 'channel-1'] });
    expect(invalidate).not.toHaveBeenCalledWith({ queryKey: ['messages', 'channel-1'] });
  });
});
