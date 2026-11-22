'use client';

import { useEffect, useState, useSyncExternalStore } from 'react';
import { useAuthStore } from '@/stores';
import { MainLayout as AppMainLayout } from '@/components/layout';
import { NotificationSocket } from '@/lib/ws/notification-socket';
import { usePathname, useRouter } from 'next/navigation';
import { Loader2 } from 'lucide-react';

// ============================================
// 主布局 - 认证守卫（未登录跳转登录页）
// ============================================
export default function MainLayout({ children }: { children: React.ReactNode }) {
  const { accessToken } = useAuthStore();
  const router = useRouter();
  const pathname = usePathname();
  // zustand persist 从 localStorage 恢复 token 是异步的，恢复完成前
  // accessToken 为 null，此时不能判定"未登录"。hasHydrated() 在服务端为
  // false、客户端为 true，正好是 useSyncExternalStore 的两份快照，
  // 比「effect 里 setState(true)」少一次级联渲染，也不会 hydration mismatch。
  const hydrated = useSyncExternalStore(
    (onChange) => useAuthStore.persist.onFinishHydration(onChange),
    () => useAuthStore.persist.hasHydrated(),
    () => false,
  );

  // 鉴权状态全部在渲染期派生，副作用（清 mock token、跳转）只留在 effect 里。
  // 原先需要两个 state + 一个 effect 来"算"这两个布尔值，现在直接算出来。
  const isMockToken = accessToken === 'mock-jwt-token';
  const loggedOutFlag = hydrated && typeof window !== 'undefined'
    && localStorage.getItem('devflow-logged-out') === '1';
  const authChecked = hydrated;
  const userLoggedOut = loggedOutFlag || !accessToken || isMockToken;

  useEffect(() => {
    if (!hydrated) return;

    if (isMockToken && typeof window !== 'undefined') {
      localStorage.removeItem('access_token');
      localStorage.removeItem('devflow-auth');
    }

    if (loggedOutFlag) {
      router.replace('/login');
      return;
    }

    if (!accessToken || isMockToken) {
      // 未登录：跳登录页（带上当前地址，登录后回跳，如分享链接落地页）
      const current = typeof window !== 'undefined'
        ? window.location.pathname + window.location.search
        : '';
      const suffix = current && current !== '/' ? `?redirect=${encodeURIComponent(current)}` : '';
      router.replace(`/login${suffix}`);
    }
  }, [hydrated, isMockToken, loggedOutFlag, accessToken, router]);

  // 等待登录态检查完成
  if (!authChecked) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-6 w-6 animate-spin text-brand" />
          <p className="text-[13px] text-ink-subtle">正在连接服务...</p>
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
      {pathname === '/projects/join' ? children : <AppMainLayout>{children}</AppMainLayout>}
    </>
  );
}
