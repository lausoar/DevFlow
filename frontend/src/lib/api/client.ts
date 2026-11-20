import axios, { type AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse, AuthResponse } from '@/types';
import { useAuthStore } from '@/stores/auth-store';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1';

type RetryableRequest = InternalAxiosRequestConfig & { _retry?: boolean };

let refreshPromise: Promise<string> | null = null;

function clearSessionAndRedirect() {
  if (typeof window === 'undefined') return;
  localStorage.setItem('devflow-logged-out', '1');
  useAuthStore.getState().logout();
  localStorage.removeItem('devflow-auth');
  const onAuthPage = window.location.pathname.startsWith('/login')
    || window.location.pathname.startsWith('/register')
    || window.location.pathname.startsWith('/guest');
  if (!onAuthPage) {
    const path = window.location.pathname + window.location.search;
    window.location.href = `/login?redirect=${encodeURIComponent(path)}`;
  }
}

async function performRefresh(failedAccessToken: string | null): Promise<string> {
  if (localStorage.getItem('devflow-logged-out') === '1') throw new Error('Logged out');
  const latestAccessToken = localStorage.getItem('access_token');
  if (latestAccessToken && latestAccessToken !== failedAccessToken) {
    const state = useAuthStore.getState();
    const latestRefreshToken = localStorage.getItem('refresh_token');
    if (state.user && latestRefreshToken) {
      state.setAuth(state.user, latestAccessToken, latestRefreshToken);
    }
    return latestAccessToken;
  }

  const refreshToken = localStorage.getItem('refresh_token');
  if (!refreshToken) throw new Error('No refresh token');
  const response = await axios.post<ApiResponse<AuthResponse>>(
    `${API_BASE_URL}/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' } },
  );
  const auth = response.data.data;
  if (localStorage.getItem('devflow-logged-out') === '1') throw new Error('Logged out during refresh');
  useAuthStore.getState().setAuth(auth.user, auth.accessToken, auth.refreshToken);
  return auth.accessToken;
}

async function refreshAccessToken(failedAccessToken: string | null): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      if (navigator.locks) {
        return navigator.locks.request('devflow-auth-refresh', () => performRefresh(failedAccessToken));
      }
      return performRefresh(failedAccessToken);
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

function createResponseInterceptor(client: AxiosInstance) {
  return async (error: AxiosError) => {
    const originalRequest = error.config as RetryableRequest | undefined;
    const status = error.response?.status;
    const requestUrl = originalRequest?.url || '';
    const canRefresh = status === 401
      && originalRequest
      && !originalRequest._retry
      && !requestUrl.startsWith('/auth/');

    if (canRefresh) {
      originalRequest._retry = true;
      try {
        const authorization = originalRequest.headers.Authorization;
        const failedAccessToken = typeof authorization === 'string' && authorization.startsWith('Bearer ')
          ? authorization.substring(7)
          : localStorage.getItem('access_token');
        const token = await refreshAccessToken(failedAccessToken);
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return client.request(originalRequest);
      } catch (refreshError) {
        clearSessionAndRedirect();
        return Promise.reject(refreshError);
      }
    }

    if (status === 401) {
      clearSessionAndRedirect();
    } else {
      const message = (error.response?.data as { message?: string } | undefined)?.message;
      if (status === 403) {
        console.error('权限不足:', message);
      } else if (status === 404) {
        console.error('资源不存在:', message);
      } else if (status === 500) {
        console.error('服务器错误:', message);
      }
    }
    return Promise.reject(error);
  };
}

// ============================================
// Axios 实例 - 统一请求配置
// ============================================
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截器 - 自动注入 JWT Token
apiClient.interceptors.request.use(
  (config) => {
    // 客户端获取 token，避免 SSR 问题
    if (typeof window !== 'undefined') {
      const token = localStorage.getItem('access_token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
        // 同步到 cookie：文件下载由 <img>/<a> 标签发起带不了 Authorization 头，后端从 cookie 回退认证
        document.cookie = `df_token=${token}; path=/; SameSite=Lax`;
      } else {
        document.cookie = 'df_token=; path=/; Max-Age=0';
      }
    }
    return config;
  },
  (error) => Promise.reject(error)
);

apiClient.interceptors.response.use(
  (response) => response.data,
  createResponseInterceptor(apiClient)
);

export default apiClient;

// ============================================
// AI 服务请求实例
// ============================================
export const aiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_AI_BASE_URL || 'http://localhost:8000/api/v1',
  // LLM 长回复常见耗时 30-60s，放宽超时避免误报"服务不可用"
  timeout: 90000,
  headers: {
    'Content-Type': 'application/json',
  },
});

aiClient.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('access_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

aiClient.interceptors.response.use(
  (response) => response.data,
  createResponseInterceptor(aiClient)
);

// ============================================
// 通用请求方法
// ============================================
export async function request<T>(
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH',
  url: string,
  data?: unknown,
  params?: Record<string, unknown>
): Promise<ApiResponse<T>> {
  const response = await apiClient.request<ApiResponse<T>>({
    method,
    url,
    data,
    params,
  });
  const envelope = response as unknown as ApiResponse<T>;
  const pageData = envelope.data as Record<string, unknown> | undefined;
  if (pageData && typeof pageData.current === 'number'
    && typeof pageData.size === 'number' && typeof pageData.pages === 'number') {
    // MyBatis-Plus Page 的字段名与前端 PageResult 不同，统一在边界处映射。
    envelope.data = {
      ...pageData,
      page: pageData.current,
      pageSize: pageData.size,
      totalPages: pageData.pages,
    } as T;
  }
  // 后端业务错误以 HTTP 200 + { code != 200 } 返回，必须显式转为异常，
  // 否则调用方的 catch 永远收不到（如"用户名或密码错误"）
  if (envelope && typeof envelope === 'object' && 'code' in envelope && envelope.code !== 200) {
    // 保持与 axios 错误一致的形状，兼容 e?.response?.data?.message 取法
    throw { response: { data: envelope, status: envelope.code }, message: envelope.message };
  }
  return envelope;
}
