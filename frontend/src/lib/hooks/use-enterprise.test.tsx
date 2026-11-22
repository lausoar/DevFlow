import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react';
import type { PropsWithChildren } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { enterpriseApi, type Enterprise } from '@/lib/api/enterprise';
import type { ApiResponse } from '@/types';
import { useCurrentEnterprise } from './use-enterprise';

vi.mock('@/lib/api/enterprise', () => ({
  enterpriseApi: { getCurrent: vi.fn() },
}));

function enterpriseResponse(): ApiResponse<Enterprise> {
  return {
    code: 200,
    message: 'ok',
    data: {
      id: 'enterprise-1',
      name: 'Enterprise',
      inviteCode: 'invite-code',
      ownerId: 'user-1',
      createdAt: '2026-01-01T00:00:00Z',
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

describe('useCurrentEnterprise', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('retries a temporary failure and recovers the current enterprise', async () => {
    vi.mocked(enterpriseApi.getCurrent)
      .mockRejectedValueOnce({ response: { status: 500 } })
      .mockResolvedValueOnce(enterpriseResponse());
    const { wrapper } = setup();
    const { result } = renderHook(() => useCurrentEnterprise(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true), { timeout: 5000 });

    expect(enterpriseApi.getCurrent).toHaveBeenCalledTimes(2);
    expect(result.current.data?.data.id).toBe('enterprise-1');
  });

  it('does not retry when the user has no enterprise', async () => {
    vi.mocked(enterpriseApi.getCurrent).mockRejectedValue({ response: { status: 404 } });
    const { wrapper } = setup();
    const { result } = renderHook(() => useCurrentEnterprise(), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(enterpriseApi.getCurrent).toHaveBeenCalledTimes(1);
  });
});
