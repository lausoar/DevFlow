import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/stores/auth-store';
import type { ApiResponse, AuthResponse, User } from '@/types';
import apiClient, { aiClient } from './client';

const originalApiAdapter = apiClient.defaults.adapter;
const originalAiAdapter = aiClient.defaults.adapter;
const originalLocks = Object.getOwnPropertyDescriptor(navigator, 'locks');

const testUser: User = {
  id: 'user-1',
  username: 'developer',
  email: 'developer@example.com',
  role: 'MEMBER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function response<T>(config: InternalAxiosRequestConfig, data: T): AxiosResponse<T> {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  };
}

function unauthorized(config: InternalAxiosRequestConfig): AxiosError {
  const error = new AxiosError('Unauthorized', AxiosError.ERR_BAD_REQUEST, config);
  error.response = {
    data: { message: 'token expired' },
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
  };
  return error;
}

function businessAdapter(source: string, validAccessToken = 'new-access') {
  return vi.fn(async (config: InternalAxiosRequestConfig) => {
    if (config.headers.Authorization !== `Bearer ${validAccessToken}`) {
      throw unauthorized(config);
    }
    return response(config, {
      code: 200,
      message: 'ok',
      data: { source },
    });
  });
}

describe('API client refresh interceptors', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.history.replaceState({}, '', '/login');
    useAuthStore.getState().setAuth(testUser, 'old-access', 'old-refresh');
  });

  afterEach(() => {
    apiClient.defaults.adapter = originalApiAdapter;
    aiClient.defaults.adapter = originalAiAdapter;
    if (originalLocks) {
      Object.defineProperty(navigator, 'locks', originalLocks);
    } else {
      Reflect.deleteProperty(navigator, 'locks');
    }
    vi.restoreAllMocks();
    window.localStorage.clear();
    useAuthStore.setState({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      isLoading: false,
    });
  });

  it('shares one refresh across concurrent API and AI 401s and replays both with the new token', async () => {
    const apiAdapter = businessAdapter('api');
    const aiAdapter = businessAdapter('ai');
    apiClient.defaults.adapter = apiAdapter;
    aiClient.defaults.adapter = aiAdapter;

    let resolveRefresh!: (value: AxiosResponse<ApiResponse<AuthResponse>>) => void;
    const refreshResponse = new Promise<AxiosResponse<ApiResponse<AuthResponse>>>((resolve) => {
      resolveRefresh = resolve;
    });
    const refresh = vi.spyOn(axios, 'post').mockReturnValue(refreshResponse);

    const apiRequest = apiClient.get('/projects');
    const aiRequest = aiClient.get('/agent/runs');
    await vi.waitFor(() => {
      expect(apiAdapter).toHaveBeenCalledTimes(1);
      expect(aiAdapter).toHaveBeenCalledTimes(1);
      expect(refresh).toHaveBeenCalledTimes(1);
    });

    expect(refresh).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/auth/refresh',
      { refreshToken: 'old-refresh' },
      { headers: { 'Content-Type': 'application/json' } },
    );
    resolveRefresh(response({ headers: new AxiosHeaders() }, {
      code: 200,
      message: 'ok',
      data: {
        user: testUser,
        accessToken: 'new-access',
        refreshToken: 'new-refresh',
      },
    }));

    await expect(apiRequest).resolves.toEqual({
      code: 200,
      message: 'ok',
      data: { source: 'api' },
    });
    await expect(aiRequest).resolves.toEqual({
      code: 200,
      message: 'ok',
      data: { source: 'ai' },
    });
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(apiAdapter).toHaveBeenCalledTimes(2);
    expect(aiAdapter).toHaveBeenCalledTimes(2);
    expect(apiAdapter.mock.calls[1][0].headers.Authorization).toBe('Bearer new-access');
    expect(aiAdapter.mock.calls[1][0].headers.Authorization).toBe('Bearer new-access');
    expect(window.localStorage.getItem('access_token')).toBe('new-access');
    expect(window.localStorage.getItem('refresh_token')).toBe('new-refresh');
  });

  it('reuses a token rotated by another tab and synchronizes the auth store', async () => {
    const apiAdapter = businessAdapter('api', 'rotated-access');
    apiClient.defaults.adapter = apiAdapter;
    const locksRequest = vi.fn(async (_name: string, callback: () => Promise<string>) => {
      window.localStorage.setItem('access_token', 'rotated-access');
      window.localStorage.setItem('refresh_token', 'rotated-refresh');
      return callback();
    });
    Object.defineProperty(navigator, 'locks', {
      configurable: true,
      value: { request: locksRequest },
    });
    const refresh = vi.spyOn(axios, 'post').mockRejectedValue(new Error('unexpected refresh'));

    await expect(apiClient.get('/projects')).resolves.toEqual({
      code: 200,
      message: 'ok',
      data: { source: 'api' },
    });

    expect(locksRequest).toHaveBeenCalledWith('devflow-auth-refresh', expect.any(Function));
    expect(refresh).not.toHaveBeenCalled();
    expect(apiAdapter).toHaveBeenCalledTimes(2);
    expect(apiAdapter.mock.calls[1][0].headers.Authorization).toBe('Bearer rotated-access');
    expect(useAuthStore.getState()).toMatchObject({
      user: testUser,
      accessToken: 'rotated-access',
      isAuthenticated: true,
    });
    expect(window.localStorage.getItem('refresh_token')).toBe('rotated-refresh');
  });

  it('does not restore a session when logout completes while refresh is pending', async () => {
    const apiAdapter = businessAdapter('api', 'late-access');
    apiClient.defaults.adapter = apiAdapter;

    let resolveRefresh!: (value: AxiosResponse<ApiResponse<AuthResponse>>) => void;
    const refreshResponse = new Promise<AxiosResponse<ApiResponse<AuthResponse>>>((resolve) => {
      resolveRefresh = resolve;
    });
    const refresh = vi.spyOn(axios, 'post').mockReturnValue(refreshResponse);
    const apiRequest = apiClient.get('/projects');
    await vi.waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));

    window.localStorage.setItem('devflow-logged-out', '1');
    useAuthStore.getState().logout();
    resolveRefresh(response({ headers: new AxiosHeaders() }, {
      code: 200,
      message: 'ok',
      data: {
        user: testUser,
        accessToken: 'late-access',
        refreshToken: 'late-refresh',
      },
    }));

    await expect(apiRequest).rejects.toThrow('Logged out during refresh');
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(apiAdapter).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState()).toMatchObject({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      isLoading: false,
    });
    expect(window.localStorage.getItem('access_token')).toBeNull();
    expect(window.localStorage.getItem('refresh_token')).toBeNull();
    expect(window.localStorage.getItem('devflow-auth')).toBeNull();
    expect(window.localStorage.getItem('devflow-logged-out')).toBe('1');
  });

  it('does not recurse on a refresh 401 and clears the authenticated session', async () => {
    const apiAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => {
      throw unauthorized(config);
    });
    apiClient.defaults.adapter = apiAdapter;
    const refreshConfig = { headers: new AxiosHeaders() };
    const refresh = vi.spyOn(axios, 'post').mockRejectedValue(unauthorized(refreshConfig));

    await expect(apiClient.get('/projects')).rejects.toMatchObject({
      response: { status: 401 },
    });

    expect(refresh).toHaveBeenCalledTimes(1);
    expect(apiAdapter).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState()).toMatchObject({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      isLoading: false,
    });
    expect(window.localStorage.getItem('access_token')).toBeNull();
    expect(window.localStorage.getItem('refresh_token')).toBeNull();
    expect(window.localStorage.getItem('devflow-auth')).toBeNull();
  });
});
