import { request } from './client';
import type { User } from '@/types';

// ============================================
// 用户信息 API
// ============================================
export const userApi = {
  /** 获取当前用户信息 */
  getMe: () =>
    request<User>('GET', '/users/me'),

  /** 修改个人信息 */
  updateProfile: (data: Partial<User>) =>
    request<User>('PUT', '/users/me', data),

  /** 修改密码 */
  changePassword: (data: { oldPassword: string; newPassword: string }) =>
    request<void>('PUT', '/users/me/password', data),

  /** 更新头像 */
  updateAvatar: (avatarUrl: string) =>
    request<User>('PUT', '/users/me/avatar', { avatarUrl }),
};
