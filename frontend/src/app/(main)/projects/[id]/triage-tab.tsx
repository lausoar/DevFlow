'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Inbox, Check, X, GitMerge, Loader2, AlertTriangle } from 'lucide-react';
import {
  useTriageItems,
  useTriageCounts,
  useTriageSimilar,
  useAcceptTriage,
  useDeclineTriage,
  useMergeTriage,
} from '@/lib/hooks/use-triage';
import {
  TRIAGE_SOURCE_LABELS,
  TRIAGE_STATUS_LABELS,
  type TriageItem,
  type TriageStatus,
} from '@/lib/api/triage';

const FILTERS: Array<{ value: TriageStatus | 'ALL'; label: string }> = [
  { value: 'PENDING', label: '待处理' },
  { value: 'ACCEPTED', label: '已接受' },
  { value: 'DECLINED', label: '已拒绝' },
  { value: 'MERGED', label: '已合并' },
];

/**
 * 分诊收件箱：外部输入（游客 Bug / 建议）进入正式列表前的闸门。
 *
 * 三个动作都保留源记录：接受转为正式条目、拒绝记录结论、合并指向已有 Bug——
 * 不会出现"处理完就查无此事"的断档。
 */
export function TriageTab({ projectId, canManage }: { projectId: string; canManage: boolean }) {
  const [status, setStatus] = useState<TriageStatus | 'ALL'>('PENDING');
  const { data: itemsRes, isLoading } = useTriageItems(projectId, status === 'ALL' ? undefined : status);
  const { data: countsRes } = useTriageCounts(projectId);
  const accept = useAcceptTriage(projectId);
  const decline = useDeclineTriage(projectId);
  const merge = useMergeTriage(projectId);

  const items: TriageItem[] = itemsRes?.data || [];
  const counts = countsRes?.data;
  const [mergeTarget, setMergeTarget] = useState<TriageItem | null>(null);

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="flex items-center gap-1.5 text-sm font-medium">
          <Inbox className="h-4 w-4" />
          分诊收件箱
        </span>
        <div className="ml-auto flex flex-wrap gap-1">
          {FILTERS.map((filter) => {
            const count = filter.value === 'ALL' ? undefined : counts?.[filter.value];
            return (
              <button
                key={filter.value}
                onClick={() => setStatus(filter.value)}
                className={`rounded-md px-2 py-1 text-xs transition-colors ${
                  status === filter.value
                    ? 'bg-brand text-primary-foreground'
                    : 'bg-muted text-muted-foreground hover:bg-muted/80'
                }`}
              >
                {filter.label}
                {count != null && count > 0 ? ` ${count}` : ''}
              </button>
            );
          })}
        </div>
      </div>

      {isLoading ? (
        <div className="flex h-32 items-center justify-center">
          <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
        </div>
      ) : items.length === 0 ? (
        <div className="flex h-32 flex-col items-center justify-center gap-1 text-muted-foreground">
          <Inbox className="h-8 w-8 opacity-30" />
          <p className="text-sm">没有待分诊的条目</p>
        </div>
      ) : (
        <div className="divide-y rounded-lg border">
          {items.map((item) => {
            const pending = item.status === 'PENDING';
            return (
              <div key={item.id} className="flex flex-wrap items-center gap-2 px-3 py-2.5">
                <Badge variant="outline" className="text-[10px]">
                  {TRIAGE_SOURCE_LABELS[item.source] || item.source}
                </Badge>
                <span className="min-w-0 flex-1 truncate text-sm">{item.title}</span>
                {item.submitterName && (
                  <span className="text-[11px] text-muted-foreground">{item.submitterName}</span>
                )}
                {item.overdue && pending && (
                  <span className="flex items-center gap-0.5 text-[10px] text-danger">
                    <AlertTriangle className="h-3 w-3" /> 超时
                  </span>
                )}
                <Badge variant="outline" className="text-[10px]">
                  {TRIAGE_STATUS_LABELS[item.status] || item.status}
                </Badge>
                {pending && canManage && (
                  <div className="flex items-center gap-1">
                    <Button
                      size="sm"
                      className="h-6 gap-0.5 text-[11px]"
                      disabled={accept.isPending}
                      onClick={() => accept.mutate(item.id)}
                    >
                      <Check className="h-3 w-3" /> 接受
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-6 gap-0.5 text-[11px]"
                      disabled={merge.isPending}
                      onClick={() => setMergeTarget(item)}
                    >
                      <GitMerge className="h-3 w-3" /> 合并
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-6 gap-0.5 text-[11px] text-muted-foreground"
                      disabled={decline.isPending}
                      onClick={() => {
                        const reason = prompt('拒绝原因（可选）') ?? undefined;
                        decline.mutate({ id: item.id, reason });
                      }}
                    >
                      <X className="h-3 w-3" /> 拒绝
                    </Button>
                  </div>
                )}
                {item.resultRefId && !pending && (
                  <span className="text-[10px] text-muted-foreground">
                    → {item.resultRefId.slice(-8)}
                  </span>
                )}
              </div>
            );
          })}
        </div>
      )}

      {/* 合并到已有缺陷 */}
      <Dialog open={!!mergeTarget} onOpenChange={(open) => !open && setMergeTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>合并到已有缺陷 · {mergeTarget?.title}</DialogTitle>
          </DialogHeader>
          {mergeTarget && (
            <MergePanel
              item={mergeTarget}
              busy={merge.isPending}
              onSubmit={async (targetBugId) => {
                await merge.mutateAsync({ id: mergeTarget.id, targetBugId });
                setMergeTarget(null);
              }}
            />
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function MergePanel({
  item,
  busy,
  onSubmit,
}: {
  item: TriageItem;
  busy: boolean;
  onSubmit: (targetBugId: string) => Promise<void>;
}) {
  const { data: similarRes, isLoading } = useTriageSimilar(item.id);
  const similar = similarRes?.data || [];
  const [manualId, setManualId] = useState('');
  const [selected, setSelected] = useState('');

  return (
    <div className="space-y-3">
      <p className="text-xs text-muted-foreground">
        合并后本条记录保留并指向目标缺陷，不会删除任何数据。
      </p>
      <div className="space-y-1">
        <div className="text-xs font-medium">相似的现有缺陷</div>
        {isLoading ? (
          <div className="flex h-10 items-center justify-center">
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          </div>
        ) : similar.length === 0 ? (
          <p className="text-xs text-muted-foreground">没有找到相似缺陷，可在下方手动填写 ID。</p>
        ) : (
          <div className="space-y-1">
            {similar.map((bug) => (
              <button
                key={bug.id}
                onClick={() => setSelected(bug.id)}
                className={`flex w-full items-center gap-2 rounded border px-2 py-1.5 text-left text-xs ${
                  selected === bug.id ? 'border-brand bg-brand/5' : 'hover:bg-muted/50'
                }`}
              >
                <span className="min-w-0 flex-1 truncate">{bug.title}</span>
                <Badge variant="outline" className="text-[10px]">{bug.status}</Badge>
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="space-y-1">
        <div className="text-xs font-medium">或手动指定缺陷 ID</div>
        <input
          value={manualId}
          onChange={(e) => setManualId(e.target.value)}
          placeholder="目标 Bug ID"
          className="flex h-8 w-full rounded-md border border-input bg-background px-2 text-xs"
        />
      </div>
      <div className="flex justify-end">
        <Button
          disabled={busy || !(selected || manualId.trim())}
          onClick={() => onSubmit((selected || manualId).trim())}
        >
          {busy ? '合并中...' : '确认合并'}
        </Button>
      </div>
    </div>
  );
}
