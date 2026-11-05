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
  const accessToken = useAuthStore((s) => s.accessToken);
  const queryClient = useQueryClient();
  const prevToken = useRef<string | null | undefined>(undefined);
  const mounted = useRef(false);

  useEffect(() => {
    // 首次挂载只记录当前 token（页面刷新时内存缓存本就是空的）
    if (!mounted.current) {
      mounted.current = true;
      prevToken.current = accessToken;
      return;
    }
    if (prevToken.current !== accessToken) {
      prevToken.current = accessToken;
      // resetQueries：清空所有缓存数据，活动中的查询自动重新拉取
      // （clear+removeQueries 会让进行中的 observer 卡在 pending，导致页面永远"加载中"）
      queryClient.resetQueries();
    }
  }, [accessToken, queryClient]);

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
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
    >
      <QueryClientProvider client={queryClient}>
        <AuthCacheInvalidation />
        {children}
      </QueryClientProvider>
    </NextThemesProvider>
  );
}
