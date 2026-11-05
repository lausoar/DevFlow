import { request } from './client';
import type { LoginRequest, RegisterRequest, AuthResponse, User } from '@/types';

// ============================================
// 认证 API
// ============================================
export const authApi = {
  /** 用户登录 */
  login: (data: LoginRequest) =>
    request<AuthResponse>('POST', '/auth/login', data),

  /** 用户注册 */
  register: (data: RegisterRequest) =>
    request<AuthResponse>('POST', '/auth/register', data),

  /** 刷新 Token */
  refreshToken: (refreshToken: string) =>
    request<AuthResponse>('POST', '/auth/refresh', { refreshToken }),

  /** 获取当前用户信息 */
  getCurrentUser: () =>
    request<User>('GET', '/auth/me'),

  /** 退出登录 */
  logout: (refreshToken?: string) =>
    request<void>('POST', '/auth/logout', refreshToken ? { refreshToken } : undefined),
};
