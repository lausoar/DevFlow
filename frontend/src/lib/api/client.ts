import axios from 'axios';
import type { ApiResponse } from '@/types';

// ============================================
// Axios 实例 - 统一请求配置
// ============================================
const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1',
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

// 响应拦截器 - 统一错误处理
apiClient.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response) {
      const { status, data } = error.response;

      switch (status) {
        case 401:
          // Token 过期/无效：清会话并带 redirect 跳登录（登录成功后回跳原页面）
          if (typeof window !== 'undefined') {
            localStorage.removeItem('access_token');
            localStorage.removeItem('refresh_token');
            localStorage.removeItem('devflow-auth');
            document.cookie = 'df_token=; path=/; Max-Age=0';
            const path = window.location.pathname + window.location.search;
            const onAuthPage = window.location.pathname.startsWith('/login')
              || window.location.pathname.startsWith('/register')
              || window.location.pathname.startsWith('/guest');
            if (!onAuthPage) {
              window.location.href = `/login?redirect=${encodeURIComponent(path)}`;
            }
          }
          break;
        case 403:
          console.error('权限不足:', data?.message);
          break;
        case 404:
          console.error('资源不存在:', data?.message);
          break;
        case 500:
          console.error('服务器错误:', data?.message);
          break;
      }
    }
    return Promise.reject(error);
  }
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
  // 后端业务错误以 HTTP 200 + { code != 200 } 返回，必须显式转为异常，
  // 否则调用方的 catch 永远收不到（如"用户名或密码错误"）
  if (envelope && typeof envelope === 'object' && 'code' in envelope && envelope.code !== 200) {
    // 保持与 axios 错误一致的形状，兼容 e?.response?.data?.message 取法
    throw { response: { data: envelope, status: envelope.code }, message: envelope.message };
  }
  return envelope;
}
