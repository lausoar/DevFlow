'use client';

import { useEffect, useState, useSyncExternalStore } from 'react';
import { Button } from '@/components/ui/button';
import { AlertTriangle, Copy, Check, RotateCcw } from 'lucide-react';

/**
 * 路由级错误边界。
 *
 * 之前没有任何 error.tsx：页面抛错时用户只看到一片空白或 Next 默认页，
 * 也无法把"哪次请求出的错"告诉客服。这里展示 error.digest 与最近一次
 * 请求的 X-Request-Id（由 api client 记录），并给出可复制的追踪号。
 */
export default function ErrorPage({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const [copied, setCopied] = useState(false);
  // 用 useSyncExternalStore 读取失败请求 ID：服务端返回空串，客户端读取 storage，
  // 避免 effect 内同步 setState 造成级联渲染与 hydration mismatch
  const requestId = useSyncExternalStore(
    () => () => {},
    () => sessionStorage.getItem('devflow-last-error-request-id') || '',
    () => '',
  );

  useEffect(() => {
    // 控制台留一份完整错误，便于开发排查；生产可在此接入错误上报
    console.error('页面渲染异常', error);
  }, [error]);

  const traceId = [error.digest, requestId].filter(Boolean).join(' / ');

  const copyTrace = async () => {
    if (!traceId) return;
    try {
      await navigator.clipboard.writeText(traceId);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // 剪贴板不可用时用户仍可手动选中
    }
  };

  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-4 px-6 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-danger-muted">
        <AlertTriangle className="h-6 w-6 text-danger" />
      </div>
      <div className="space-y-1">
        <h1 className="text-lg font-semibold">页面出错了</h1>
        <p className="text-sm text-muted-foreground">
          可以重试一次；如果持续出现，请把下面的追踪号发给管理员。
        </p>
      </div>
      {traceId && (
        <button
          onClick={copyTrace}
          className="flex items-center gap-1.5 rounded-md border border-hairline bg-surface-1 px-3 py-1.5 font-mono text-[11px] text-ink-muted hover:bg-surface-2"
          title="复制追踪号"
        >
          {copied ? <Check className="h-3 w-3 text-success" /> : <Copy className="h-3 w-3" />}
          {traceId}
        </button>
      )}
      <Button onClick={reset} className="gap-1.5">
        <RotateCcw className="h-4 w-4" /> 重试
      </Button>
    </div>
  );
}
