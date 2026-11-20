'use client';

import { ThemeProvider as NextThemesProvider } from 'next-themes';
import { QueryClient, QueryClientProvider, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useAuthStore } from '@/stores';

// ============================================
// React Query 配置
// ============================================
function makeQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 1000 * 60 * 5, // 5 分钟内数据视为新鲜
        retry: 1,
        refetchOnWindowFocus: false,
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

function getQueryClient() {
  if (typeof window === 'undefined') {
    // SSR 模式：每次创建新实例
    return makeQueryClient();
  }
  // 客户端复用实例
  if (!browserQueryClient) browserQueryClient = makeQueryClient();
  return browserQueryClient;
}

// ============================================
// 账号切换时清空查询缓存
// 查询缓存（enterprise/projects/workspace 等）与用户无关，
// 同一浏览器切换账号时必须清空，否则新账号会读到旧账号的数据
// ============================================
function AuthCacheInvalidation() {
  const userId = useAuthStore((s) => s.user?.id ?? null);
  const queryClient = useQueryClient();
  const prevUserId = useRef<string | null | undefined>(undefined);
  const mounted = useRef(false);

  useEffect(() => {
    if (!mounted.current) {
      mounted.current = true;
      prevUserId.current = userId;
      return;
    }
    if (prevUserId.current !== userId) {
      prevUserId.current = userId;
      queryClient.resetQueries();
    }
  }, [userId, queryClient]);

  return null;
}

// ============================================
// 全局 Provider 组合
// ============================================
export function Providers({ children }: { children: ReactNode }) {
  const queryClient = getQueryClient();

  return (
    <NextThemesProvider
      attribute="class"
      // 主题类名必须是 light / dark：globals.css 里两套 token 分别挂在
      // `:root, .dark` 与 `.light` 上。显式写死，避免以后换成别的类名导致浅色主题静默失效。
      value={{ light: 'light', dark: 'dark' }}
      defaultTheme="dark"
      enableSystem={false}
      disableTransitionOnChange
    >
      <QueryClientProvider client={queryClient}>
        <AuthCacheInvalidation />
        {children}
      </QueryClientProvider>
    </NextThemesProvider>
  );
}
