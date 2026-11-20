'use client';

import Link from 'next/link';
import { FileText, ExternalLink } from 'lucide-react';
import { MarkdownView } from '@/components/common/markdown-view';
import type { CitationSource } from '@/lib/api/ai';
import type { CitationLink } from '@/lib/markdown/rehype-text';

/** 来源 → 文档中心跳转地址：带版本与偏移，文档页据此定位并高亮原文。 */
export function citationHref(source: CitationSource): string {
  const params = new URLSearchParams();
  if (source.doc_id) params.set('docId', source.doc_id);
  if (source.document_version != null) params.set('v', String(source.document_version));
  if (source.chunk_index != null) params.set('chunk', String(source.chunk_index));
  if (source.char_start != null) params.set('start', String(source.char_start));
  if (source.char_end != null) params.set('end', String(source.char_end));
  return `/docs?${params.toString()}`;
}

/**
 * 带引用角标的答案。
 *
 * 答案正文保持 Markdown 渲染，[n] 变成可点击角标；下方列出全部来源。
 * 模型可能漏标或编造编号：只把真实存在的来源编号变成链接，其余原样保留。
 */
export function AnswerWithCitations({
  answer,
  sources,
  className,
}: {
  answer: string;
  sources?: CitationSource[];
  className?: string;
}) {
  const valid = (sources ?? []).filter((s) => s && typeof s.index === 'number');
  const links: Record<number, CitationLink> = {};
  for (const source of valid) {
    links[source.index] = {
      href: citationHref(source),
      title: source.title || '来源文档',
    };
  }

  return (
    <div className={className}>
      <MarkdownView content={answer} citations={links} />
      {valid.length > 0 && <SourceList sources={valid} />}
    </div>
  );
}

/** 来源列表：可点进文档核对原文；正文摘要够核对出处即可。 */
export function SourceList({ sources }: { sources: CitationSource[] }) {
  return (
    <div className="mt-3 border-t border-hairline/60 pt-2">
      <div className="mb-1 text-[11px] font-medium text-muted-foreground">
        引用来源（{sources.length}）
      </div>
      <ul className="space-y-1">
        {sources.map((source) => (
          <li key={`${source.doc_id}-${source.index}-${source.chunk_index ?? 0}`}>
            <Link
              href={citationHref(source)}
              className="group flex items-start gap-1.5 rounded px-1 py-0.5 text-[11px] text-muted-foreground hover:bg-muted/60 hover:text-foreground"
            >
              <span className="mt-0.5 inline-flex h-3.5 min-w-3.5 items-center justify-center rounded bg-brand/10 px-0.5 text-[9px] font-medium text-brand">
                {source.index}
              </span>
              <FileText className="mt-0.5 h-3 w-3 shrink-0" />
              <span className="min-w-0">
                <span className="line-clamp-1 font-medium">{source.title || '未命名文档'}</span>
                {source.content && (
                  <span className="line-clamp-1 text-[10px] text-muted-foreground/80">{source.content}</span>
                )}
              </span>
              <ExternalLink className="mt-0.5 ml-auto h-3 w-3 shrink-0 opacity-0 group-hover:opacity-100" />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
