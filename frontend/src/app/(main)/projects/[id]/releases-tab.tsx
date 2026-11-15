'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Plus, Rocket, Loader2, FileText, CheckCircle2, Trash2, Copy } from 'lucide-react';
import {
  useReleases,
  useCreateRelease,
  useDeleteRelease,
  useGenerateNotes,
  usePublishRelease,
  useReleaseStats,
} from '@/lib/hooks/use-releases';
import type { ProjectRelease } from '@/lib/api/release';
import { MarkdownView } from '@/components/common/markdown-view';

/**
 * 版本 / 发布标签页。
 *
 * 这是"部署上线"环节在本平台的起点：版本是需求/任务/缺陷的聚合键，
 * 发布说明由后端按版本自动汇总，发布是一个明确的、留痕的动作。
 */
export function ReleasesTab({ projectId, canManage }: { projectId: string; canManage: boolean }) {
  const { data: res, isLoading } = useReleases(projectId);
  const createRelease = useCreateRelease(projectId);
  const deleteRelease = useDeleteRelease(projectId);
  const releases = res?.data || [];

  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({ version: '', name: '', description: '', releaseDate: '' });
  const [detail, setDetail] = useState<ProjectRelease | null>(null);

  const handleCreate = async () => {
    if (!form.version.trim()) return;
    await createRelease.mutateAsync({
      version: form.version.trim(),
      name: form.name.trim() || form.version.trim(),
      description: form.description.trim() || undefined,
      releaseDate: form.releaseDate || undefined,
    });
    setForm({ version: '', name: '', description: '', releaseDate: '' });
    setCreateOpen(false);
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-sm font-medium">版本与发布</h3>
          <p className="text-xs text-muted-foreground">
            版本用于把需求 / 任务 / 缺陷聚合到一起，发布说明可按版本自动生成
          </p>
        </div>
        {canManage && (
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger className="inline-flex items-center gap-1.5 rounded-md bg-brand px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-brand/90">
              <Plus className="h-4 w-4" />
              新建版本
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>新建版本</DialogTitle>
              </DialogHeader>
              <div className="space-y-3">
                <div>
                  <Label>版本号 *</Label>
                  <Input
                    value={form.version}
                    onChange={(e) => setForm({ ...form, version: e.target.value })}
                    placeholder="例如 v1.2.0"
                  />
                </div>
                <div>
                  <Label>名称</Label>
                  <Input
                    value={form.name}
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                    placeholder="留空则使用版本号"
                  />
                </div>
                <div>
                  <Label>计划发布日期</Label>
                  <Input
                    type="date"
                    value={form.releaseDate}
                    onChange={(e) => setForm({ ...form, releaseDate: e.target.value })}
                  />
                </div>
                <div>
                  <Label>说明</Label>
                  <textarea
                    value={form.description}
                    onChange={(e) => setForm({ ...form, description: e.target.value })}
                    placeholder="本版本的目标（可选）"
                    className="flex min-h-[70px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                  />
                </div>
              </div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
                <Button onClick={handleCreate} disabled={!form.version.trim() || createRelease.isPending}>
                  {createRelease.isPending ? '创建中...' : '创建'}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        )}
      </div>

      {isLoading ? (
        <div className="flex h-40 items-center justify-center">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
        </div>
      ) : releases.length === 0 ? (
        <div className="flex h-40 flex-col items-center justify-center gap-2 text-muted-foreground">
          <Rocket className="h-10 w-10 opacity-30" />
          <p className="text-sm">还没有版本。先建一个版本，再把需求/任务挂上去。</p>
        </div>
      ) : (
        <div className="space-y-2">
          {releases.map((r) => (
            <ReleaseRow
              key={r.id}
              release={r}
              canManage={canManage}
              onOpen={() => setDetail(r)}
              onDelete={() => {
                if (confirm(`删除版本 ${r.version}？已挂的工作项会解除关联`)) deleteRelease.mutate(r.id);
              }}
            />
          ))}
        </div>
      )}

      <ReleaseDetailDialog
        release={detail}
        canManage={canManage}
        onClose={() => setDetail(null)}
      />
    </div>
  );
}

function ReleaseRow({
  release,
  canManage,
  onOpen,
  onDelete,
}: {
  release: ProjectRelease;
  canManage: boolean;
  onOpen: () => void;
  onDelete: () => void;
}) {
  const statusConfig: Record<string, { label: string; className: string }> = {
    UNRELEASED: { label: '未发布', className: 'bg-warning-muted text-warning' },
    RELEASED: { label: '已发布', className: 'bg-success-muted text-success' },
    ARCHIVED: { label: '已归档', className: 'bg-surface-2 text-ink-subtle' },
  };
  const st = statusConfig[release.status] || statusConfig.UNRELEASED;

  return (
    <div className="flex items-center gap-3 rounded-lg border px-3 py-2.5">
      <Rocket className="h-4 w-4 shrink-0 text-brand" />
      <button className="flex-1 text-left" onClick={onOpen}>
        <span className="font-mono text-xs text-muted-foreground">{release.version}</span>
        <span className="ml-2 text-sm font-medium">{release.name}</span>
        {release.releaseDate && (
          <span className="ml-2 text-xs text-muted-foreground">计划 {release.releaseDate}</span>
        )}
      </button>
      <Badge variant="outline" className={`text-[10px] ${st.className} border-transparent`}>
        {st.label}
      </Badge>
      {canManage && release.status !== 'RELEASED' && (
        <Button variant="ghost" size="icon" className="h-6 w-6" onClick={onDelete} title="删除版本">
          <Trash2 className="h-3 w-3" />
        </Button>
      )}
    </div>
  );
}

function ReleaseDetailDialog({
  release,
  canManage,
  onClose,
}: {
  release: ProjectRelease | null;
  canManage: boolean;
  onClose: () => void;
}) {
  const generateNotes = useGenerateNotes(release?.projectId || '');
  const publish = usePublishRelease(release?.projectId || '');
  const { data: statsRes } = useReleaseStats(release?.id || null);
  const stats = statsRes?.data;
  const [notes, setNotes] = useState<string>('');

  if (!release) return null;

  const effectiveNotes = notes || release.releaseNotes || '';
  const unfinished = (stats?.openTasks || 0) + (stats?.openBugs || 0);

  const handleGenerate = async () => {
    const res = await generateNotes.mutateAsync({ id: release.id, overwrite: true });
    setNotes(res.data.releaseNotes);
  };

  const handlePublish = () => {
    if (!confirm('标记为已发布后不可撤销，确认发布？')) return;
    publish.mutate(release.id);
  };

  return (
    <Dialog open={!!release} onOpenChange={(open) => { if (!open) { setNotes(''); onClose(); } }}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Rocket className="h-4 w-4" />
            {release.version} · {release.name}
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* 发布前检查 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-xs text-muted-foreground">发布前检查</CardTitle>
            </CardHeader>
            <CardContent className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
              <Stat label="需求" value={stats?.requirements ?? 0} />
              <Stat label="任务" value={stats?.tasks ?? 0} warn={stats?.openTasks} />
              <Stat label="缺陷" value={stats?.bugs ?? 0} warn={stats?.openBugs} />
              <Stat label="未完成合计" value={unfinished} danger={unfinished > 0} />
            </CardContent>
          </Card>

          {/* 发布说明 */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">发布说明</span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs"
                  onClick={handleGenerate}
                  disabled={generateNotes.isPending}
                >
                  <FileText className="mr-1 h-3 w-3" />
                  {generateNotes.isPending ? '生成中...' : '按版本自动生成'}
                </Button>
                {effectiveNotes && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 text-xs"
                    onClick={() => {
                      navigator.clipboard?.writeText(effectiveNotes);
                    }}
                  >
                    <Copy className="mr-1 h-3 w-3" />
                    复制
                  </Button>
                )}
              </div>
            </div>
            <div className="max-h-72 overflow-y-auto rounded-lg border p-3">
              {effectiveNotes ? (
                <MarkdownView content={effectiveNotes} />
              ) : (
                <p className="text-xs text-muted-foreground">
                  还没有发布说明。点击「按版本自动生成」会把这个版本下的需求、任务、缺陷汇总成变更日志。
                </p>
              )}
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose}>关闭</Button>
          {canManage && release.status !== 'RELEASED' && (
            <Button onClick={handlePublish} disabled={publish.isPending}>
              <CheckCircle2 className="mr-1.5 h-4 w-4" />
              {publish.isPending ? '发布中...' : '标记为已发布'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Stat({
  label,
  value,
  warn,
  danger,
}: {
  label: string;
  value: number;
  warn?: number;
  danger?: boolean;
}) {
  return (
    <div>
      <p className="text-xs text-muted-foreground">{label}</p>
      <p className={`text-lg font-semibold ${danger ? 'text-danger' : ''}`}>{value}</p>
      {warn != null && warn > 0 && (
        <p className="text-[11px] text-warning">其中 {warn} 项未完成</p>
      )}
    </div>
  );
}
