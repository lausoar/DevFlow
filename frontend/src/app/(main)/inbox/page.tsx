'use client';

import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useInbox, useApproveDeployment, useRejectDeployment } from '@/lib/hooks/use-inbox';
import { Inbox, ListTodo, Rocket, Check, X, Loader2, ArrowRight } from 'lucide-react';

/**
 * 移动端收件箱：安装为 PWA 后的第一屏。
 *
 * 三条路径（捕捉 → 收件箱 → 审批）都在这里收口：看有什么等我处理、
 * 直接审批部署、跳去分诊或任务。不做全功能，只做"现在该做什么"。
 */
export default function InboxPage() {
  const { data: res, isLoading } = useInbox();
  const approve = useApproveDeployment();
  const reject = useRejectDeployment();
  const inbox = res?.data;

  return (
    <div className="min-h-dvh bg-canvas">
      <div className="sticky top-0 z-10 flex items-center gap-2 border-b border-hairline bg-canvas/95 px-4 py-3 backdrop-blur-xl">
        <Inbox className="h-5 w-5 text-brand" />
        <h1 className="text-base font-semibold">收件箱</h1>
        {inbox && inbox.total > 0 && (
          <Badge variant="outline" className="ml-auto text-[10px]">{inbox.total} 件待处理</Badge>
        )}
      </div>

      <div className="space-y-4 p-4">
        {isLoading ? (
          <div className="flex h-40 items-center justify-center">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <>
            <Section
              title="等我审批"
              icon={<Rocket className="h-4 w-4" />}
              empty="没有待审批的部署"
              count={inbox?.approvals.length ?? 0}
            >
              {inbox?.approvals.map((item) => (
                <div key={item.id} className="space-y-2 border-b border-hairline/60 px-3 py-2.5 last:border-b-0">
                  <div className="text-sm">{item.title}</div>
                  <div className="flex gap-2">
                    <Button
                      size="lg"
                      className="h-10 flex-1 gap-1"
                      disabled={approve.isPending}
                      onClick={() => approve.mutate({ id: item.id })}
                    >
                      <Check className="h-4 w-4" /> 通过
                    </Button>
                    <Button
                      size="lg"
                      variant="outline"
                      className="h-10 flex-1 gap-1"
                      disabled={reject.isPending}
                      onClick={() => reject.mutate({ id: item.id })}
                    >
                      <X className="h-4 w-4" /> 驳回
                    </Button>
                  </div>
                </div>
              ))}
            </Section>

            <Section
              title="待分诊"
              icon={<Inbox className="h-4 w-4" />}
              empty="没有待分诊的外部提交"
              count={inbox?.triage.length ?? 0}
            >
              {inbox?.triage.map((item) => (
                <Link
                  key={item.id}
                  href={`/projects/${item.projectId}?tab=triage`}
                  className="flex items-center gap-2 border-b border-hairline/60 px-3 py-3 text-sm last:border-b-0 hover:bg-surface-2"
                >
                  <span className="min-w-0 flex-1 truncate">{item.title}</span>
                  {item.submitterName && (
                    <span className="text-[11px] text-muted-foreground">{item.submitterName}</span>
                  )}
                  <ArrowRight className="h-3.5 w-3.5 text-muted-foreground" />
                </Link>
              ))}
            </Section>

            <Section
              title="指派给我"
              icon={<ListTodo className="h-4 w-4" />}
              empty="没有指派给你的未完成任务"
              count={inbox?.assignments.length ?? 0}
            >
              {inbox?.assignments.map((item) => (
                <Link
                  key={item.id}
                  href={`/projects/${item.projectId}?tab=tasks`}
                  className="flex items-center gap-2 border-b border-hairline/60 px-3 py-3 text-sm last:border-b-0 hover:bg-surface-2"
                >
                  <span className="min-w-0 flex-1 truncate">{item.title}</span>
                  {item.priority && (
                    <Badge variant="outline" className="text-[10px]">{item.priority}</Badge>
                  )}
                  <ArrowRight className="h-3.5 w-3.5 text-muted-foreground" />
                </Link>
              ))}
            </Section>
          </>
        )}
      </div>
    </div>
  );
}

function Section({
  title,
  icon,
  empty,
  count,
  children,
}: {
  title: string;
  icon: React.ReactNode;
  empty: string;
  count: number;
  children: React.ReactNode;
}) {
  return (
    <section className="overflow-hidden rounded-lg border border-hairline bg-card">
      <div className="flex items-center gap-1.5 border-b border-hairline px-3 py-2 text-xs font-medium text-ink-muted">
        {icon}
        {title}
        {count > 0 && <span className="ml-auto text-[10px]">{count}</span>}
      </div>
      {count === 0 ? (
        <p className="px-3 py-5 text-center text-xs text-muted-foreground">{empty}</p>
      ) : (
        children
      )}
    </section>
  );
}
