'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import {
  Palette,
  Plus,
  ExternalLink,
  History,
  Loader2,
  Trash2,
  Star,
} from 'lucide-react';
import {
  useRequirementDesigns,
  useCreateDesign,
  useChangeDesignStatus,
  useAddDesignVersion,
  useDeleteDesign,
  useDesignVersions,
} from '@/lib/hooks/use-design';
import { DESIGN_STATUS_CONFIG, nextDesignStatuses, type DesignAsset, type DesignStatus } from '@/lib/api/design';

/**
 * 需求详情里的设计稿面板。
 *
 * 这里的价值不是"看图"，而是三条契约：
 * 1. 主设计稿唯一——开发照哪一版做是明确的；
 * 2. 评审状态可流转且每次变化都通知相关人员；
 * 3. 版本历史可追溯，改稿不会悄悄覆盖已通过的基线。
 */
export function DesignAssetPanel({
  requirementId,
  projectId,
  canManage,
}: {
  requirementId: string;
  projectId: string;
  canManage: boolean;
}) {
  const { data: listRes, isLoading } = useRequirementDesigns(requirementId);
  const createDesign = useCreateDesign(requirementId, projectId);
  const changeStatus = useChangeDesignStatus(projectId, requirementId);
  const addVersion = useAddDesignVersion(projectId, requirementId);
  const deleteDesign = useDeleteDesign(projectId, requirementId);

  const designs: DesignAsset[] = listRes?.data || [];

  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({ title: '', embedUrl: '', provider: 'FIGMA', isPrimary: true });
  const [versionOf, setVersionOf] = useState<DesignAsset | null>(null);
  const [versionForm, setVersionForm] = useState({ snapshotUrl: '', note: '' });
  const [versionsOf, setVersionsOf] = useState<DesignAsset | null>(null);

  const handleCreate = async () => {
    if (!form.title.trim()) return;
    await createDesign.mutateAsync({
      title: form.title.trim(),
      embedUrl: form.embedUrl.trim() || undefined,
      provider: form.provider as DesignAsset['provider'],
      isPrimary: form.isPrimary ? 1 : 0,
    });
    setForm({ title: '', embedUrl: '', provider: 'FIGMA', isPrimary: designs.length === 0 });
    setCreateOpen(false);
  };

  const handleAddVersion = async () => {
    if (!versionOf) return;
    await addVersion.mutateAsync({
      id: versionOf.id,
      data: {
        snapshotUrl: versionForm.snapshotUrl.trim() || undefined,
        note: versionForm.note.trim() || undefined,
      },
    });
    setVersionOf(null);
    setVersionForm({ snapshotUrl: '', note: '' });
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1.5 text-sm font-medium">
          <Palette className="h-3.5 w-3.5" />
          设计稿
          {designs.length > 0 && (
            <span className="text-xs font-normal text-muted-foreground">（{designs.length}）</span>
          )}
        </span>
        {canManage && (
          <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1 h-3 w-3" /> 关联设计稿
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="flex h-10 items-center justify-center">
          <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
        </div>
      ) : designs.length === 0 ? (
        <p className="rounded-md border border-dashed px-3 py-4 text-center text-xs text-muted-foreground">
          还没有关联设计稿。粘贴 Figma / 蓝湖 / MasterGo 链接即可把设计接入需求主线。
        </p>
      ) : (
        <div className="space-y-1.5">
          {designs.map((design) => {
            const statusConfig = DESIGN_STATUS_CONFIG[design.status] || DESIGN_STATUS_CONFIG.DESIGNING;
            const nextStatuses = nextDesignStatuses(design.status);
            return (
              <div key={design.id} className="rounded-md border px-3 py-2">
                <div className="flex items-center gap-2">
                  {design.isPrimary === 1 && (
                    <Star className="h-3 w-3 fill-warning text-warning" aria-label="主设计稿" />
                  )}
                  <span className="min-w-0 flex-1 truncate text-xs font-medium">{design.title}</span>
                  <Badge variant="outline" className={`text-[10px] ${statusConfig.className}`}>
                    {statusConfig.label}
                  </Badge>
                  <span className="text-[10px] text-muted-foreground">v{design.currentVersion ?? 1}</span>
                </div>
                <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                  {design.embedUrl && (
                    <a
                      href={design.embedUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[11px] text-brand hover:bg-brand/10"
                    >
                      <ExternalLink className="h-3 w-3" /> 打开设计稿
                    </a>
                  )}
                  <button
                    className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[11px] text-muted-foreground hover:bg-muted"
                    onClick={() => setVersionsOf(design)}
                  >
                    <History className="h-3 w-3" /> 版本
                  </button>
                  {canManage && (
                    <button
                      className="inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[11px] text-muted-foreground hover:bg-muted"
                      onClick={() => {
                        setVersionOf(design);
                        setVersionForm({ snapshotUrl: design.embedUrl || '', note: '' });
                      }}
                    >
                      <Plus className="h-3 w-3" /> 新版本
                    </button>
                  )}
                  {canManage && nextStatuses.map((status) => (
                    <button
                      key={status}
                      className="rounded px-1.5 py-0.5 text-[11px] text-brand hover:bg-brand/10"
                      disabled={changeStatus.isPending}
                      onClick={() => changeStatus.mutate({ id: design.id, status })}
                    >
                      → {DESIGN_STATUS_CONFIG[status].label}
                    </button>
                  ))}
                  {canManage && (
                    <button
                      className="ml-auto rounded p-1 text-muted-foreground hover:text-destructive"
                      title="删除设计稿"
                      onClick={() => {
                        if (confirm(`确定删除设计稿「${design.title}」吗？`)) {
                          deleteDesign.mutate(design.id);
                        }
                      }}
                    >
                      <Trash2 className="h-3 w-3" />
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* 关联设计稿 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>关联设计稿</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label>标题 *</Label>
              <Input
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="例如「登录页 v1」"
              />
            </div>
            <div className="space-y-1">
              <Label>来源</Label>
              <select
                value={form.provider}
                onChange={(e) => setForm({ ...form, provider: e.target.value })}
                className="flex h-9 w-full rounded-md border border-input bg-background px-2 text-sm"
              >
                <option value="FIGMA">Figma</option>
                <option value="LANHU">蓝湖</option>
                <option value="MASTERGO">MasterGo</option>
                <option value="OTHER">其他</option>
              </select>
            </div>
            <div className="space-y-1">
              <Label>预览链接</Label>
              <Input
                value={form.embedUrl}
                onChange={(e) => setForm({ ...form, embedUrl: e.target.value })}
                placeholder="https://www.figma.com/file/..."
              />
            </div>
            <label className="flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={form.isPrimary}
                onChange={(e) => setForm({ ...form, isPrimary: e.target.checked })}
              />
              设为主设计稿（同一需求只保留一张）
            </label>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
              <Button
                onClick={handleCreate}
                disabled={createDesign.isPending || !form.title.trim()}
              >
                {createDesign.isPending ? '关联中...' : '关联'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* 新版本 */}
      <Dialog open={!!versionOf} onOpenChange={(open) => !open && setVersionOf(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>提交新版本 · {versionOf?.title}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <p className="text-xs text-muted-foreground">
              提交后版本号 +1 并回到「设计中」；已通过的基线与关联任务不会自动变更。
            </p>
            <div className="space-y-1">
              <Label>快照链接</Label>
              <Input
                value={versionForm.snapshotUrl}
                onChange={(e) => setVersionForm({ ...versionForm, snapshotUrl: e.target.value })}
                placeholder="该版本的设计稿链接"
              />
            </div>
            <div className="space-y-1">
              <Label>说明</Label>
              <Input
                value={versionForm.note}
                onChange={(e) => setVersionForm({ ...versionForm, note: e.target.value })}
                placeholder="改了什么，例如「调整登录按钮层级」"
              />
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setVersionOf(null)}>取消</Button>
              <Button onClick={handleAddVersion} disabled={addVersion.isPending}>
                {addVersion.isPending ? '提交中...' : '提交新版本'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      {/* 版本历史 */}
      <Dialog open={!!versionsOf} onOpenChange={(open) => !open && setVersionsOf(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>版本历史 · {versionsOf?.title}</DialogTitle>
          </DialogHeader>
          {versionsOf && <DesignVersionList assetId={versionsOf.id} />}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function DesignVersionList({ assetId }: { assetId: string }) {
  const { data: res, isLoading } = useDesignVersions(assetId);
  const versions = res?.data || [];
  if (isLoading) {
    return (
      <div className="flex h-12 items-center justify-center">
        <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (versions.length === 0) {
    return <p className="py-6 text-center text-xs text-muted-foreground">还没有版本记录</p>;
  }
  return (
    <div className="max-h-80 space-y-1.5 overflow-y-auto">
      {versions.map((version) => (
        <div key={version.id} className="rounded-md border px-3 py-2">
          <div className="flex items-center gap-2">
            <Badge variant="outline" className="text-[10px]">v{version.version}</Badge>
            <span className="min-w-0 flex-1 truncate text-xs">{version.note || '（无说明）'}</span>
            <span className="text-[10px] text-muted-foreground">
              {new Date(version.createdAt).toLocaleString('zh-CN')}
            </span>
          </div>
          {version.snapshotUrl && (
            <a
              href={version.snapshotUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="mt-1 inline-flex items-center gap-1 text-[11px] text-brand hover:underline"
            >
              <ExternalLink className="h-3 w-3" /> 查看该版本
            </a>
          )}
        </div>
      ))}
    </div>
  );
}
