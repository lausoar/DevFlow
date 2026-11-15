'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Progress } from '@/components/ui/progress';
import {
  Loader2,
  Plus,
  CheckCircle2,
  XCircle,
  MinusCircle,
  Bug as BugIcon,
  ClipboardCheck,
} from 'lucide-react';
import {
  useWalkthroughs,
  useWalkthroughDetail,
  useCreateWalkthrough,
  useAddWalkthroughItems,
  useSetWalkthroughResult,
  useConvertWalkthroughItem,
  useCloseWalkthrough,
} from '@/lib/hooks/use-walkthroughs';
import type { WalkthroughResult } from '@/lib/api/walkthrough';

/**
 * UI 走查面板（嵌在需求详情里）。
 *
 * 走查单锁定设计稿版本，走查项四态记录；失败项可一键转缺陷，
 * 让"视觉还原度"从一句口头判断变成可计数、可闭环的清单。
 */
export function WalkthroughPanel({
  requirementId,
  canManage,
}: {
  requirementId: string;
  canManage: boolean;
}) {
  const { data: listRes, isLoading } = useWalkthroughs(requirementId);
  const createWalkthrough = useCreateWalkthrough(requirementId);
  const walkthroughs = listRes?.data || [];

  const [openId, setOpenId] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [title, setTitle] = useState('');

  const handleCreate = async () => {
    await createWalkthrough.mutateAsync({ title: title.trim() || undefined });
    setTitle('');
    setCreateOpen(false);
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1.5 text-sm font-medium">
          <ClipboardCheck className="h-3.5 w-3.5" />
          UI 走查
        </span>
        {canManage && (
          <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1 h-3 w-3" /> 新建走查
          </Button>
        )}
      </div>

      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
              <DialogHeader>
                <DialogTitle>新建 UI 走查单</DialogTitle>
              </DialogHeader>
              <div className="space-y-3">
                <div>
                  <Label>标题</Label>
                  <Input
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="留空则使用「需求标题 · UI 走查」"
                  />
                </div>
                <p className="text-xs text-muted-foreground">
                  走查单会自动锁定该需求主设计稿的当前版本，作为验收基线。
                </p>
              </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button onClick={handleCreate} disabled={createWalkthrough.isPending}>
              {createWalkthrough.isPending ? '创建中...' : '创建'}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {isLoading ? (
        <div className="flex h-12 items-center justify-center">
          <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
        </div>
      ) : walkthroughs.length === 0 ? (
        <p className="py-2 text-xs text-muted-foreground">
          还没有走查单。{canManage ? '点「新建走查」把验收清单列出来。' : ''}
        </p>
      ) : (
        <div className="space-y-1.5">
          {walkthroughs.map((w) => (
            <button
              key={w.walkthrough.id}
              onClick={() => setOpenId(w.walkthrough.id)}
              className="flex w-full items-center gap-2 rounded-lg border px-3 py-2 text-left hover:bg-muted/50"
            >
              <span className="flex-1 truncate text-sm">{w.walkthrough.title}</span>
              <Badge variant="outline" className="text-[10px]">
                {w.walkthrough.status === 'CLOSED' ? '已关闭' : '进行中'}
              </Badge>
              <span className="text-[11px] text-muted-foreground">
                {w.checked}/{w.total} 已检查
              </span>
              <span className={`text-[11px] ${w.passRate < 100 ? 'text-warning' : 'text-success'}`}>
                通过率 {w.passRate}%
              </span>
            </button>
          ))}
        </div>
      )}

      {openId && (
        <WalkthroughDetailDialog
          walkthroughId={openId}
          requirementId={requirementId}
          canManage={canManage}
          onClose={() => setOpenId(null)}
        />
      )}
    </div>
  );
}

function WalkthroughDetailDialog({
  walkthroughId,
  requirementId,
  canManage,
  onClose,
}: {
  walkthroughId: string;
  requirementId: string;
  canManage: boolean;
  onClose: () => void;
}) {
  const { data: detailRes, isLoading } = useWalkthroughDetail(walkthroughId);
  const addItems = useAddWalkthroughItems(walkthroughId);
  const setResult = useSetWalkthroughResult(walkthroughId);
  const toBug = useConvertWalkthroughItem(walkthroughId);
  const closeWalkthrough = useCloseWalkthrough(requirementId, walkthroughId);

  const [newItems, setNewItems] = useState('');
  const detail = detailRes?.data;

  const handleAddItems = async () => {
    const titles = newItems
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean);
    if (titles.length === 0) return;
    await addItems.mutateAsync(titles);
    setNewItems('');
  };

  const RESULT_META: Record<WalkthroughResult, { label: string; className: string }> = {
    PENDING: { label: '待检查', className: 'bg-surface-2 text-ink-subtle' },
    PASS: { label: '通过', className: 'bg-success-muted text-success' },
    FAIL: { label: '不通过', className: 'bg-danger-muted text-danger' },
    NA: { label: '不适用', className: 'bg-surface-2 text-ink-tertiary' },
  };

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ClipboardCheck className="h-4 w-4" />
            {detail?.walkthrough.title || 'UI 走查'}
            {detail?.walkthrough.designVersion != null && (
              <Badge variant="outline" className="text-[10px]">
                基线设计稿 v{detail.walkthrough.designVersion}
              </Badge>
            )}
          </DialogTitle>
        </DialogHeader>

        {isLoading || !detail ? (
          <div className="flex h-24 items-center justify-center">
            <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <div className="space-y-4">
            {/* 进度 */}
            <div className="space-y-1.5">
              <div className="flex items-center justify-between text-xs">
                <span className="text-muted-foreground">
                  已检查 {detail.checked}/{detail.total} · 不通过 {detail.fail} · 已转缺陷 {detail.convertedBugs}
                </span>
                <span className="font-medium">通过率 {detail.passRate}%</span>
              </div>
              <Progress value={detail.total > 0 ? (detail.checked / detail.total) * 100 : 0} className="h-1.5" />
            </div>

            {/* 走查项 */}
            <div className="max-h-72 space-y-1 overflow-y-auto">
              {detail.items.length === 0 && (
                <p className="py-3 text-center text-xs text-muted-foreground">还没有走查项</p>
              )}
              {detail.items.map((item) => {
                const meta = RESULT_META[item.result];
                return (
                  <div key={item.id} className="flex items-center gap-2 rounded-md border px-2.5 py-1.5">
                    <span className="flex-1 text-xs">{item.title}</span>
                    <Badge variant="outline" className={`text-[10px] ${meta.className} border-transparent`}>
                      {meta.label}
                    </Badge>
                    {item.bugId && (
                      <Badge variant="outline" className="text-[10px]">已转缺陷</Badge>
                    )}
                    {canManage && (
                      <div className="flex gap-0.5">
                        <Button
                          variant="ghost" size="icon" className="h-6 w-6" title="通过"
                          onClick={() => setResult.mutate({ itemId: item.id, result: 'PASS' })}
                        >
                          <CheckCircle2 className="h-3 w-3 text-success" />
                        </Button>
                        <Button
                          variant="ghost" size="icon" className="h-6 w-6" title="不通过"
                          onClick={() => setResult.mutate({ itemId: item.id, result: 'FAIL' })}
                        >
                          <XCircle className="h-3 w-3 text-danger" />
                        </Button>
                        <Button
                          variant="ghost" size="icon" className="h-6 w-6" title="不适用"
                          onClick={() => setResult.mutate({ itemId: item.id, result: 'NA' })}
                        >
                          <MinusCircle className="h-3 w-3 text-muted-foreground" />
                        </Button>
                        {item.result === 'FAIL' && !item.bugId && (
                          <Button
                            variant="ghost" size="icon" className="h-6 w-6" title="转缺陷"
                            onClick={() => toBug.mutate({ itemId: item.id })}
                          >
                            <BugIcon className="h-3 w-3 text-warning" />
                          </Button>
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            {/* 批量添加 */}
            {canManage && detail.walkthrough.status === 'OPEN' && (
              <div className="space-y-1.5">
                <Label className="text-xs">批量添加走查项（一行一项）</Label>
                <textarea
                  value={newItems}
                  onChange={(e) => setNewItems(e.target.value)}
                  placeholder={'按钮间距是否与设计稿一致\n主色是否使用设计 Token\n空态文案是否正确'}
                  className="h-20 w-full resize-none rounded-md border bg-muted/30 p-2 text-xs"
                />
                <Button
                  size="sm"
                  className="h-7 text-xs"
                  onClick={handleAddItems}
                  disabled={!newItems.trim() || addItems.isPending}
                >
                  <Plus className="mr-1 h-3 w-3" />
                  {addItems.isPending ? '添加中...' : '添加'}
                </Button>
              </div>
            )}
          </div>
        )}

        <div className="flex justify-end gap-2 pt-2">
          <Button variant="outline" onClick={onClose}>关闭</Button>
          {canManage && detail?.walkthrough.status === 'OPEN' && (
            <Button
              onClick={() => {
                if (confirm('关闭后不再接受新的检查结果，确认关闭？')) closeWalkthrough.mutate();
              }}
            >
              关闭走查单
            </Button>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
