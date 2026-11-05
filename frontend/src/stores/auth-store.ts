import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { User } from '@/types';

// ============================================
// 认证状态管理
// ============================================
interface AuthState {
  user: User | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;

  // Actions
  setAuth: (user: User, accessToken: string) => void;
  setUser: (user: User) => void;
  logout: () => void;
  setLoading: (loading: boolean) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      isLoading: true,

      // 设置认证信息
      setAuth: (user, accessToken) => {
        if (typeof window !== 'undefined') {
          localStorage.setItem('access_token', accessToken);
        }
        set({
          user,
          accessToken,
          isAuthenticated: true,
          isLoading: false,
        });
      },

      // 更新用户信息
      setUser: (user) => set({ user }),

      // 退出登录
      logout: () => {
        if (typeof window !== 'undefined') {
          localStorage.removeItem('access_token');
          localStorage.removeItem('refresh_token');
        }
        set({
          user: null,
          accessToken: null,
          isAuthenticated: false,
          isLoading: false,
        });
      },

      // 设置加载状态
      setLoading: (isLoading) => set({ isLoading }),
    }),
    {
      // 持久化 token 到 localStorage
      name: 'devflow-auth',
      partialize: (state) => ({
        accessToken: state.accessToken,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
