'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { cn } from '@/lib/utils';

/**
 * 注册 Service Worker（仅生产环境）。
 *
 * 用 PWA 的唯一目的是"移动端可安装 + 离线打开收件箱"，因此缓存策略刻意保守：
 * - 永不缓存 /api/（数据必须实时，缓存旧数据比白屏更危险）；
 * - 页面导航走 network-first，断网时才回落到缓存；
 * - sw.js 本身由服务端 no-cache 下发，版本升级后旧缓存立即失效。
 */
export function PwaRegister() {
  useEffect(() => {
    if (typeof window === 'undefined' || !('serviceWorker' in navigator)) return;
    if (process.env.NODE_ENV !== 'production') return;
    // 开发环境的 HMR chunk 名不稳定，注册 SW 只会制造陈旧缓存
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // 注册失败不影响使用：应用仍可在浏览器里正常工作
    });
  }, []);
  return null;
}

/**
 * 顶部"可安装"提示。
 *
 * 浏览器只有在满足安装条件后才会派发 beforeinstallprompt；捕获后由用户主动触发，
 * 不自动弹窗（自动弹窗会被浏览器拦截，也会打断正在做的事）。
 */
export function InstallPrompt({ className, children }: { className?: string; children?: ReactNode }) {
  const [deferred, setDeferred] = useState<Event | null>(null);

  useEffect(() => {
    const handler = (event: Event) => {
      event.preventDefault();
      setDeferred(event);
    };
    window.addEventListener('beforeinstallprompt', handler);
    return () => window.removeEventListener('beforeinstallprompt', handler);
  }, []);

  if (!deferred) return children ? <>{children}</> : null;

  return (
    <button
      className={cn('text-xs text-brand hover:underline', className)}
      onClick={async () => {
        const prompt = deferred as Event & { prompt?: () => Promise<void> };
        await prompt.prompt?.();
        setDeferred(null);
      }}
    >
      安装到桌面
    </button>
  );
}
