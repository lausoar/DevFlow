'use client';

import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

/**
 * 统一的 Markdown 渲染组件。
 *
 * 安全前提：禁用原始 HTML（react-markdown 默认不渲染 raw HTML），
 * 链接一律 noopener/noreferrer、图片走 CSS 限宽，避免文档内容引入脚本或撑破布局。
 */
export function MarkdownView({
  content,
  className,
}: {
  content: string;
  className?: string;
}) {
  if (!content || !content.trim()) {
    return <p className="text-sm text-muted-foreground">（空）</p>;
  }
  return (
    <div className={`markdown-body text-sm leading-relaxed ${className ?? ''}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          h1: ({ children }) => <h1 className="mb-3 mt-5 text-xl font-bold first:mt-0">{children}</h1>,
          h2: ({ children }) => <h2 className="mb-2 mt-5 text-lg font-semibold first:mt-0">{children}</h2>,
          h3: ({ children }) => <h3 className="mb-2 mt-4 text-base font-semibold first:mt-0">{children}</h3>,
          h4: ({ children }) => <h4 className="mb-1.5 mt-3 text-sm font-semibold first:mt-0">{children}</h4>,
          p: ({ children }) => <p className="my-2 whitespace-pre-wrap first:mt-0">{children}</p>,
          ul: ({ children }) => <ul className="my-2 ml-5 list-disc space-y-1">{children}</ul>,
          ol: ({ children }) => <ol className="my-2 ml-5 list-decimal space-y-1">{children}</ol>,
          li: ({ children }) => <li className="leading-relaxed">{children}</li>,
          blockquote: ({ children }) => (
            <blockquote className="my-2 border-l-4 border-muted-foreground/30 pl-3 text-muted-foreground">
              {children}
            </blockquote>
          ),
          code: ({ className: codeClass, children }) => {
            const isBlock = !!codeClass;
            if (isBlock) {
              return <code className={`${codeClass} font-mono text-xs`}>{children}</code>;
            }
            return (
              <code className="rounded bg-muted px-1 py-0.5 font-mono text-[0.85em]">{children}</code>
            );
          },
          pre: ({ children }) => (
            <pre className="my-3 overflow-x-auto rounded-lg bg-muted p-3 text-xs">{children}</pre>
          ),
          a: ({ href, children }) => (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="text-primary underline underline-offset-2"
            >
              {children}
            </a>
          ),
          img: ({ src, alt }) => (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={typeof src === 'string' ? src : undefined} alt={alt || ''} className="my-3 max-w-full rounded-lg" />
          ),
          hr: () => <hr className="my-4 border-border" />,
          table: ({ children }) => (
            <div className="my-3 overflow-x-auto">
              <table className="w-full border-collapse text-xs">{children}</table>
            </div>
          ),
          th: ({ children }) => (
            <th className="border border-border bg-muted px-2 py-1 text-left font-medium">{children}</th>
          ),
          td: ({ children }) => <td className="border border-border px-2 py-1">{children}</td>,
          input: ({ checked, type }) =>
            type === 'checkbox' ? (
              <input type="checkbox" checked={checked} readOnly className="mr-1 align-middle" />
            ) : null,
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}
