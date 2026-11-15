'use client';

import { useSyncExternalStore } from 'react';

/**
 * 「只在浏览器里为真」的布尔值。
 *
 * 替代 `useEffect(() => setMounted(true), [])` 这种写法：
 * 服务端快照恒为 false、客户端快照恒为 true，React 在 hydrate 之后
 * 自动重渲染一次。既避免了 setState-in-effect 引起的级联渲染，
 * 也不会产生 hydration mismatch。
 */
const noopSubscribe = () => () => {};

export function useIsMounted(): boolean {
  return useSyncExternalStore(
    noopSubscribe,
    () => true,
    () => false,
  );
}

/**
 * 读取当前 URL 的查询参数（只在客户端有值）。
 *
 * 服务端快照固定为 null，客户端取真实值。适合「深链带参数进入页面」
 * 这类初始化场景，避免在 useEffect 里 setState。
 *
 * 注意：本 hook 不订阅 URL 变化（平台内跳转都走整页/路由重渲染），
 * 需要响应同一页面内的 query 变化时请用 next/navigation 的 useSearchParams。
 */
export function useSearchParam(key: string): string | null {
  return useSyncExternalStore(
    noopSubscribe,
    () => new URLSearchParams(window.location.search).get(key),
    () => null,
  );
}
