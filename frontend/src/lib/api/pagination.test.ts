import { afterEach, describe, expect, it, vi } from 'vitest';
import { request } from './client';
import apiClient from './client';

afterEach(() => {
  vi.restoreAllMocks();
});

describe('MyBatis Page compatibility', () => {
  it('maps current/size/pages to PageResult fields', async () => {
    vi.spyOn(apiClient, 'request').mockResolvedValue({
      code: 200,
      message: 'ok',
      data: { current: 2, size: 20, pages: 3, total: 45, records: [] },
    });

    const response = await request('GET', '/notifications');

    expect(response.data).toMatchObject({
      page: 2,
      pageSize: 20,
      totalPages: 3,
      current: 2,
      size: 20,
      pages: 3,
      total: 45,
      records: [],
    });
  });
});
