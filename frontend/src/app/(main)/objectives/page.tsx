'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Progress } from '@/components/ui/progress';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Card, CardContent } from '@/components/ui/card';
import { useObjectives, useObjectiveRollup, useCreateObjective } from '@/lib/hooks/use-objectives';
import { useProjects } from '@/lib/hooks/use-projects';
import { useAssignProjectToObjective } from '@/lib/hooks/use-objectives';
import { useAuthStore } from '@/stores/auth-store';
import { Target, Plus, Loader2, Calendar, FolderKanban, Clock } from 'lucide-react';
import type { Objective } from '@/lib/api/objective';

const STATUS_LABELS: Record<string, string> = {
  ACTIVE: '进行中',
  ACHIEVED: '已达成',
  ARCHIVED: '已归档',
};

/**
 * 目标（OKR）页面。
 *
 * 只呈现"项目/需求对齐到目标后的真实投入与产出"：工时来自 worklog，
 * 完成数来自状态事件，需求完成率来自各项目自己的工作流分类——
 * 没有手工填写的进度条，避免数字与交付脱节。
 */
export default function ObjectivesPage() {
  const user = useAuthStore((s) => s.user);
  const enterpriseId = user?.enterpriseId ?? null;
  const { data: objectivesRes, isLoading } = useObjectives(enterpriseId);
  const createObjective = useCreateObjective(enterpriseId);
  const objectives = objectivesRes?.data || [];

  // 选中项按"用户点过哪个"派生，默认落在第一个目标上——
  // 不用 effect 回填，避免级联渲染，也避免列表刷新后选中项跳走
  const [pickedId, setPickedId] = useState<string | null>(null);
  const selectedId = pickedId ?? objectives[0]?.id ?? null;
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({ name: '', period: '', startDate: '', endDate: '', description: '' });

  const handleCreate = async () => {
    if (!form.name.trim()) return;
    const created = await createObjective.mutateAsync({
      name: form.name.trim(),
      period: form.period.trim() || undefined,
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined,
      description: form.description.trim() || undefined,
    });
    setForm({ name: '', period: '', startDate: '', endDate: '', description: '' });
    setCreateOpen(false);
    if (created.data?.id) setPickedId(created.data.id);
  };

  return (
    <>
      <div className="flex h-full flex-col">
        <div className="flex flex-wrap items-center gap-3 border-b px-4 py-4 sm:px-6">
          <div className="flex items-center gap-2">
            <Target className="h-5 w-5 text-brand" />
            <h1 className="text-lg font-semibold">目标</h1>
          </div>
          <p className="hidden text-xs text-muted-foreground sm:block">
            项目与需求对齐目标后，投入（工时）与产出（完成）自动汇总
          </p>
          <Button size="sm" className="ml-auto gap-1" onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" /> 新建目标
          </Button>
        </div>

        <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-auto p-4 sm:p-6 lg:flex-row">
          {/* 目标列表 */}
          <div className="w-full shrink-0 space-y-2 lg:w-72">
            {isLoading ? (
              <div className="flex h-24 items-center justify-center">
                <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
              </div>
            ) : objectives.length === 0 ? (
              <p className="rounded-lg border border-dashed px-4 py-8 text-center text-sm text-muted-foreground">
                还没有目标。创建后把项目挂上去，投入与产出会自动汇总。
              </p>
            ) : (
              objectives.map((objective) => (
                <button
                  key={objective.id}
                  onClick={() => setPickedId(objective.id)}
                  className={`w-full rounded-lg border px-3 py-2.5 text-left transition-colors ${
                    selectedId === objective.id ? 'border-brand bg-brand/5' : 'hover:bg-muted/50'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="min-w-0 flex-1 truncate text-sm font-medium">{objective.name}</span>
                    <Badge variant="outline" className="text-[10px]">
                      {STATUS_LABELS[objective.status] || objective.status}
                    </Badge>
                  </div>
                  <div className="mt-1 flex items-center gap-2 text-[11px] text-muted-foreground">
                    {objective.period && <span>{objective.period}</span>}
                    {objective.startDate && (
                      <span className="flex items-center gap-0.5">
                        <Calendar className="h-3 w-3" />
                        {objective.startDate}{objective.endDate ? ` ~ ${objective.endDate}` : ''}
                      </span>
                    )}
                  </div>
                </button>
              ))
            )}
          </div>

          {/* 目标详情 */}
          <div className="min-w-0 flex-1">
            {selectedId ? (
              <ObjectiveDetail objectiveId={selectedId} enterpriseId={enterpriseId} />
            ) : (
              <div className="flex h-64 flex-col items-center justify-center gap-2 text-muted-foreground">
                <Target className="h-10 w-10 opacity-30" />
                <p className="text-sm">选择或创建一个目标</p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* 新建目标 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>新建目标</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="space-y-1">
              <Label>目标名称 *</Label>
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="例如「Q4 交付效率提升 30%」"
              />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-1">
                <Label>周期</Label>
                <Input
                  value={form.period}
                  onChange={(e) => setForm({ ...form, period: e.target.value })}
                  placeholder="2026Q4"
                />
              </div>
              <div className="space-y-1">
                <Label>说明</Label>
                <Input
                  value={form.description}
                  onChange={(e) => setForm({ ...form, description: e.target.value })}
                  placeholder="可选"
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-1">
                <Label>开始日期</Label>
                <Input
                  type="date"
                  value={form.startDate}
                  onChange={(e) => setForm({ ...form, startDate: e.target.value })}
                />
              </div>
              <div className="space-y-1">
                <Label>结束日期</Label>
                <Input
                  type="date"
                  value={form.endDate}
                  onChange={(e) => setForm({ ...form, endDate: e.target.value })}
                />
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
              <Button onClick={handleCreate} disabled={createObjective.isPending || !form.name.trim()}>
                {createObjective.isPending ? '创建中...' : '创建'}
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

function ObjectiveDetail({ objectiveId, enterpriseId }: { objectiveId: string; enterpriseId: string | null }) {
  const { data: rollupRes, isLoading } = useObjectiveRollup(objectiveId);
  const { data: projectsRes } = useProjects({ page: 1, pageSize: 100 });
  const assignProject = useAssignProjectToObjective(enterpriseId);
  const rollup = rollupRes?.data;
  const [attachProjectId, setAttachProjectId] = useState('');
  const [attachOpen, setAttachOpen] = useState(false);

  if (isLoading || !rollup) {
    return (
      <div className="flex h-40 items-center justify-center">
        <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const objective: Objective = rollup.objective;
  const allProjects = projectsRes?.data?.records || [];
  const attached = new Set(rollup.projects.map((p) => p.id));
  const attachable = allProjects.filter((p) => !attached.has(p.id));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-start gap-2">
        <div className="min-w-0 flex-1">
          <h2 className="text-base font-semibold">{objective.name}</h2>
          {objective.description && (
            <p className="mt-0.5 text-xs text-muted-foreground">{objective.description}</p>
          )}
        </div>
        <Button size="sm" variant="outline" className="gap-1" onClick={() => setAttachOpen(true)}>
          <Plus className="h-3.5 w-3.5" /> 挂项目
        </Button>
      </div>

      {/* 汇总卡片 */}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <SummaryCard label="关联项目" value={rollup.projectCount} />
        <SummaryCard label="投入工时" value={`${rollup.worklogHours} h`} icon={<Clock className="h-3 w-3" />} />
        <SummaryCard label="周期内完成" value={`${rollup.completedInPeriod} 项`} />
        <SummaryCard label="对齐需求" value={`${rollup.requirements.done}/${rollup.requirements.total}`} />
      </div>

      {/* 项目明细 */}
      <Card>
        <CardContent className="p-0">
          {rollup.projects.length === 0 ? (
            <p className="px-4 py-8 text-center text-sm text-muted-foreground">
              还没有项目挂到这个目标上。
            </p>
          ) : (
            <div className="divide-y">
              {rollup.projects.map((project) => (
                <div key={project.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
                  <FolderKanban className="h-4 w-4 shrink-0 text-muted-foreground" />
                  <span className="min-w-0 flex-1 truncate text-sm font-medium">{project.name}</span>
                  <span className="text-[11px] text-muted-foreground">
                    {project.doneTasks}/{project.totalTasks} 任务 · {project.worklogHours}h
                  </span>
                  <div className="w-28">
                    <Progress value={project.progress} className="h-1.5" />
                  </div>
                  <span className="w-10 text-right text-[11px] text-muted-foreground">{project.progress}%</span>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>

      {/* 需求对齐进度 */}
      <Card>
        <CardContent className="space-y-2 p-4">
          <div className="flex items-center justify-between text-xs">
            <span className="font-medium">对齐需求完成度</span>
            <span className="text-muted-foreground">
              {rollup.requirements.done}/{rollup.requirements.total}（{rollup.requirements.progress}%）
            </span>
          </div>
          <Progress value={rollup.requirements.progress} className="h-1.5" />
          <p className="text-[11px] text-muted-foreground">
            完成判定使用各项目自己的需求工作流 DONE 分类，不是写死的状态名。
          </p>
        </CardContent>
      </Card>

      {/* 挂项目 */}
      <Dialog open={attachOpen} onOpenChange={setAttachOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>把项目挂到目标</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            {attachable.length === 0 ? (
              <p className="text-sm text-muted-foreground">所有项目都已挂在该目标下。</p>
            ) : (
              <>
                <Label>选择项目</Label>
                <select
                  value={attachProjectId}
                  onChange={(e) => setAttachProjectId(e.target.value)}
                  className="flex h-9 w-full rounded-md border border-input bg-background px-2 text-sm"
                >
                  <option value="">请选择</option>
                  {attachable.map((p) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
              </>
            )}
            <div className="flex justify-end gap-2">
              <Button variant="outline" onClick={() => setAttachOpen(false)}>取消</Button>
              <Button
                disabled={!attachProjectId || assignProject.isPending}
                onClick={async () => {
                  await assignProject.mutateAsync({ projectId: attachProjectId, objectiveId });
                  setAttachProjectId('');
                  setAttachOpen(false);
                }}
              >
                挂到目标
              </Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function SummaryCard({ label, value, icon }: { label: string; value: string | number; icon?: React.ReactNode }) {
  return (
    <div className="rounded-lg border bg-card px-3 py-2">
      <div className="flex items-center gap-1 text-[11px] text-muted-foreground">
        {icon}
        {label}
      </div>
      <div className="mt-0.5 text-lg font-semibold">{value}</div>
    </div>
  );
}
