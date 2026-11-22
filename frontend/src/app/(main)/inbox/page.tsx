'use client';

import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useInbox, useApproveDeployment, useRejectDeployment } from '@/lib/hooks/use-inbox';
import { Inbox, ListTodo, Rocket, Check, X, Loader2, ArrowRight } from 'lucide-react';

/**
 * 收件箱：只回答"现在该我处理什么"。
 *
 * 行动优先级是固定的：审批 > 分诊 > 指派任务。桌面端保持紧凑的信息行，
 * 移动端动作按钮单独占一行并放大触控区；页面始终挂在应用主布局内。
 */
export default function InboxPage() {
  const { data: res, isLoading } = useInbox();
  const approve = useApproveDeployment();
  const reject = useRejectDeployment();
  const inbox = res?.data;
  const total = inbox?.total ?? 0;

  return (
    <>
      <div className="mx-auto w-full max-w-4xl space-y-5">
        <header className="flex flex-wrap items-end justify-between gap-3 border-b border-hairline pb-4">
          <div className="space-y-1">
            <h1 className="flex items-center gap-2 text-headline text-ink">
              <Inbox className="h-5 w-5 text-brand" aria-hidden="true" />
              收件箱
            </h1>
            <p className="text-[13px] text-ink-subtle">
              需要你决定的事排在最前面
            </p>
          </div>
          {total > 0 && (
            <span className="rounded-full bg-brand/10 px-2.5 py-1 text-xs font-medium text-brand">
              {total} 件待处理
            </span>
          )}
        </header>

        {isLoading ? (
          <div className="flex h-48 items-center justify-center text-ink-subtle" role="status">
            <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
            正在整理待办事项…
          </div>
        ) : (
          <div className="space-y-5">
            <Section
              id="approvals"
              title="等我审批"
              icon={<Rocket className="h-4 w-4" aria-hidden="true" />}
              count={inbox?.approvals.length ?? 0}
              empty="没有待审批的部署"
            >
              {inbox?.approvals.map((item) => (
                <article
                  key={item.id}
                  className="flex flex-col gap-3 border-b border-hairline/60 px-4 py-3 last:border-b-0 sm:flex-row sm:items-center sm:justify-between"
                >
                  <div className="min-w-0 space-y-0.5">
                    <h3 className="truncate text-sm font-medium text-ink">{item.title}</h3>
                    <p className="text-xs text-ink-subtle">通过后会继续执行部署</p>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button
                      size="sm"
                      className="h-9 flex-1 gap-1 sm:flex-none"
                      disabled={approve.isPending || reject.isPending}
                      onClick={() => approve.mutate({ id: item.id })}
                    >
                      {approve.isPending ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                      ) : (
                        <Check className="h-3.5 w-3.5" aria-hidden="true" />
                      )}
                      通过
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="h-9 flex-1 gap-1 sm:flex-none"
                      disabled={approve.isPending || reject.isPending}
                      onClick={() => reject.mutate({ id: item.id })}
                    >
                      {reject.isPending ? (
                        <Loader2 className="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
                      ) : (
                        <X className="h-3.5 w-3.5" aria-hidden="true" />
                      )}
                      驳回
                    </Button>
                  </div>
                </article>
              ))}
            </Section>

            <Section
              id="triage"
              title="待分诊"
              icon={<Inbox className="h-4 w-4" aria-hidden="true" />}
              count={inbox?.triage.length ?? 0}
              empty="没有待分诊的外部提交"
            >
              {inbox?.triage.map((item) => (
                <Link
                  key={item.id}
                  href={`/projects/${item.projectId}?tab=triage`}
                  className="group flex items-center gap-3 border-b border-hairline/60 px-4 py-3 last:border-b-0 hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <span className="min-w-0 flex-1 truncate text-sm text-ink group-hover:text-brand">
                    {item.title}
                  </span>
                  {item.submitterName && (
                    <span className="shrink-0 text-[11px] text-ink-subtle">{item.submitterName}</span>
                  )}
                  <ArrowRight className="h-3.5 w-3.5 shrink-0 text-ink-subtle transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                </Link>
              ))}
            </Section>

            <Section
              id="assignments"
              title="指派给我"
              icon={<ListTodo className="h-4 w-4" aria-hidden="true" />}
              count={inbox?.assignments.length ?? 0}
              empty="没有指派给你的未完成任务"
            >
              {inbox?.assignments.map((item) => (
                <Link
                  key={item.id}
                  href={`/projects/${item.projectId}?tab=tasks`}
                  className="group flex items-center gap-3 border-b border-hairline/60 px-4 py-3 last:border-b-0 hover:bg-surface-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <span className="min-w-0 flex-1 truncate text-sm text-ink group-hover:text-brand">
                    {item.title}
                  </span>
                  {item.priority && (
                    <Badge variant="outline" className="shrink-0 text-[10px]">{item.priority}</Badge>
                  )}
                  <ArrowRight className="h-3.5 w-3.5 shrink-0 text-ink-subtle transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                </Link>
              ))}
            </Section>
          </div>
        )}
      </div>
    </>
  );
}

function Section({
  id,
  title,
  icon,
  count,
  empty,
  children,
}: {
  id: string;
  title: string;
  icon: React.ReactNode;
  count: number;
  empty: string;
  children: React.ReactNode;
}) {
  return (
    <section aria-labelledby={`inbox-${id}`} className="overflow-hidden rounded-md border border-hairline bg-card">
      <div
        id={`inbox-${id}`}
        className="flex items-center gap-2 border-b border-hairline bg-surface-1 px-4 py-2.5 text-xs font-medium text-ink-muted"
      >
        {icon}
        <h2>{title}</h2>
        {count > 0 && <span className="ml-auto text-[11px] text-ink-subtle">{count}</span>}
      </div>
      {count === 0 ? (
        <p className="px-4 py-6 text-center text-xs text-ink-subtle">{empty}</p>
      ) : (
        children
      )}
    </section>
  );
}
