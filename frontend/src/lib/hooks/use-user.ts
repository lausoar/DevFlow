import { useMutation } from '@tanstack/react-query';
import { userApi } from '@/lib/api/user';
import type { User } from '@/types';

// ============================================
// 用户个人信息相关 Hooks
// ============================================

/** 修改个人信息 */
export function useUpdateProfile() {
  return useMutation({
    mutationFn: (data: Partial<User>) => userApi.updateProfile(data),
  });
}

/** 修改密码 */
export function useChangePassword() {
  return useMutation({
    mutationFn: (data: { oldPassword: string; newPassword: string }) =>
      userApi.changePassword(data),
  });
}

/** 更新头像 */
export function useUpdateAvatar() {
  return useMutation({
    mutationFn: (avatarUrl: string) => userApi.updateAvatar(avatarUrl),
  });
}
