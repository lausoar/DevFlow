'use client';

import type { ReactNode } from 'react';

// ============================================
// 富文本描述渲染（Bug/需求详情共用）
// 描述按 ![xx](url) 切分：文本段保留换行，图片段渲染为内嵌图片（点击看原图）
// ============================================
export function RichDescription({
  text,
  className = 'rounded-md bg-muted/50 p-3 text-sm',
}: {
  text: string;
  className?: string;
}) {
  const re = /!\[[^\]]*\]\(([^)\s]+)\)/g;
  const nodes: ReactNode[] = [];
  let last = 0;
  let m: RegExpExecArray | null;
  let key = 0;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) {
      nodes.push(
        <span key={`t${key++}`} className="whitespace-pre-wrap">{text.slice(last, m.index)}</span>
      );
    }
    const url = m[1];
    nodes.push(
      // eslint-disable-next-line @next/next/no-img-element
      <img
        key={`i${key++}`}
        src={url}
        alt="描述截图"
        className="my-1.5 max-h-72 max-w-full cursor-zoom-in rounded-md border"
        onClick={() => window.open(url, '_blank')}
      />
    );
    last = m.index + m[0].length;
  }
  if (last < text.length) {
    nodes.push(
      <span key={`t${key++}`} className="whitespace-pre-wrap">{text.slice(last)}</span>
    );
  }
  if (nodes.length === 0) {
    return <div className={className + ' text-muted-foreground'}>暂无描述</div>;
  }
  return <div className={className}>{nodes}</div>;
}
