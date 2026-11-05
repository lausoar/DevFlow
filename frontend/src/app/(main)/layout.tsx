'use client';

import { useEffect, useState } from 'react';
import { useAuthStore } from '@/stores';
import { NotificationSocket } from '@/lib/ws/notification-socket';
import { useRouter } from 'next/navigation';
import { Loader2 } from 'lucide-react';

// ============================================
// 主布局 - 认证守卫（未登录跳转登录页）
// ============================================
export default function MainLayout({ children }: { children: React.ReactNode }) {
  const { accessToken } = useAuthStore();
  const router = useRouter();
  const [authChecked, setAuthChecked] = useState(false);
  const [userLoggedOut, setUserLoggedOut] = useState(false);
  // zustand persist 从 localStorage 恢复 token 是异步的，
  // 恢复完成前 accessToken 为 null，此时不能判定"未登录"。
  // 注意：persist API 仅客户端存在，初始值必须为 false 以保证 SSR 一致
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    if (useAuthStore.persist.hasHydrated()) {
      setHydrated(true);
      return;
    }
    const unsub = useAuthStore.persist.onFinishHydration(() => setHydrated(true));
    return unsub;
  }, []);

  useEffect(() => {
    if (!hydrated) return; // 等待 token 恢复完成

    // 检测遗留的 mock token，清除后要求重新登录
    const isMockToken = accessToken === 'mock-jwt-token';
    if (isMockToken && typeof window !== 'undefined') {
      localStorage.removeItem('access_token');
      localStorage.removeItem('devflow-auth');
    }

    const hasToken = accessToken && !isMockToken;

    // 用户主动退出后，直接跳转登录页
    const loggedOut = typeof window !== 'undefined'
      && localStorage.getItem('devflow-logged-out') === '1';
    if (loggedOut) {
      setUserLoggedOut(true);
      setAuthChecked(true);
      return;
    }

    // 未登录：跳转登录页（带上当前地址，登录后回跳，如分享链接落地页）
    if (!hasToken) {
      const current = typeof window !== 'undefined'
        ? window.location.pathname + window.location.search
        : '';
      const suffix = current && current !== '/' ? `?redirect=${encodeURIComponent(current)}` : '';
      router.replace(`/login${suffix}`);
      return;
    }

    setAuthChecked(true);
  }, [accessToken, router, hydrated]);

  useEffect(() => {
    // 用户主动退出后，跳转登录页
    if (userLoggedOut) {
      router.replace('/login');
    }
  }, [userLoggedOut, router]);

  // 等待登录态检查完成
  if (!authChecked) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-6 w-6 animate-spin text-brand" />
          <p className="text-sm text-muted-foreground">正在连接服务...</p>
        </div>
      </div>
    );
  }

  // 用户主动退出，等待跳转（router.replace 在 useEffect 中处理）
  if (userLoggedOut) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <Loader2 className="h-6 w-6 animate-spin text-brand" />
      </div>
    );
  }

  return (
    <>
      <NotificationSocket />
      {children}
    </>
  );
}
