'use client';

import { useEffect, useSyncExternalStore } from 'react';

/**
 * 根级错误边界：必须自带 html/body（此时根布局已失效）。
 * 样式尽量内联，避免依赖可能同样失败的样式表。
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const requestId = useSyncExternalStore(
    () => () => {},
    () => sessionStorage.getItem('devflow-last-error-request-id') || '',
    () => '',
  );
  useEffect(() => {
    console.error('应用级异常', error);
  }, [error]);

  const traceId = [error.digest, requestId].filter(Boolean).join(' / ');

  return (
    <html lang="zh-CN">
      <body style={{ margin: 0, fontFamily: 'system-ui, sans-serif', background: '#fff' }}>
        <div style={{
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 12,
          padding: 24,
          textAlign: 'center',
          color: '#111',
        }}>
          <h1 style={{ fontSize: 20, margin: 0 }}>应用启动失败</h1>
          <p style={{ fontSize: 14, color: '#666', margin: 0 }}>
            请刷新页面重试；如果反复出现，请把追踪号发给管理员。
          </p>
          {traceId && (
            <code style={{ fontSize: 12, color: '#555' }}>{traceId}</code>
          )}
          <button
            onClick={reset}
            style={{
              marginTop: 8,
              padding: '8px 16px',
              borderRadius: 8,
              border: '1px solid #ddd',
              background: '#f5f5f5',
              cursor: 'pointer',
              fontSize: 14,
            }}
          >
            重试
          </button>
        </div>
      </body>
    </html>
  );
}
