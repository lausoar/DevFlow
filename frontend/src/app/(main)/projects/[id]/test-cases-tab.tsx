'use client';

import { useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  useTestCases,
  useCreateTestCase,
  useUpdateTestCase,
  useDeleteTestCase,
  useExecuteTestCase,
  useToBug,
} from '@/lib/hooks/use-test-cases';
import { useRequirements } from '@/lib/hooks/use-requirements';
import { useMyPermissions } from '@/lib/hooks/use-permissions';
import {
  Plus,
  Search,
  FlaskConical,
  Pencil,
  Trash2,
  Play,
  ExternalLink,
  CheckCircle2,
  XCircle,
  MinusCircle,
  SkipForward,
  CircleDashed,
  Loader2,
  X,
  Download,
} from 'lucide-react';
import { downloadCsv } from '@/lib/csv';
import type { TestCase, TestCaseView, TestCaseStep } from '@/types';

// ============================================
// 测试用例标签页
// 用例库（步骤/前置/预期/关联需求）+ 执行记录 + 失败转缺陷
// 权限：testcase:manage = 测试人员及以上可管理；成员只读
// ============================================

const RESULT_CONFIG: Record<string, { label: string; cls: string }> = {
  PASS: { label: '通过', cls: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
  FAIL: { label: '失败', cls: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' },
  BLOCKED: { label: '阻塞', cls: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400' },
  SKIP: { label: '跳过', cls: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400' },
  UNEXECUTED: { label: '未执行', cls: 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-500' },
};

const PRIORITY_LABELS: Record<string, string> = { HIGH: '高', MEDIUM: '中', LOW: '低' };

interface StepRow {
  action: string;
  expected?: string;
}

/** 解析用例步骤 JSON */
function parseSteps(steps?: string): TestCaseStep[] {
  try {
    const parsed = steps ? JSON.parse(steps) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/** 步骤文本摘要（卡片/缺陷描述用） */
function stepsSummary(steps?: string): string {
  const list = parseSteps(steps);
  if (list.length === 0) return '无';
  return list
    .map((s, i) => `${i + 1}. ${s.action}${s.expected ? `（预期：${s.expected}）` : ''}`)
    .join('\n');
}

export function TestCasesTab({ projectId }: { projectId: string }) {
  const [keyword, setKeyword] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [editCase, setEditCase] = useState<TestCaseView | null>(null);
  const [execCase, setExecCase] = useState<TestCaseView | null>(null);

  const { data: res, isLoading } = useTestCases(projectId, keyword || undefined);
  const cases: TestCaseView[] = res?.data?.cases || [];
  const stats = res?.data?.stats;

  const myPerms = useMyPermissions(projectId);
  const canManage = !!myPerms.data?.data?.project?.includes('testcase:manage');

  const createCase = useCreateTestCase(projectId);
  const updateCase = useUpdateTestCase(projectId);
  const deleteCase = useDeleteTestCase(projectId);
  const executeCase = useExecuteTestCase(projectId);
  const toBug = useToBug(projectId);

  return (
    <div className="flex h-full flex-col">
      {/* 统计卡 */}
      {stats && (
        <div className="mb-4 grid grid-cols-3 gap-3 sm:grid-cols-6">
          {[
            { label: '总用例', value: stats.total, cls: 'text-foreground' },
            { label: '通过', value: stats.passed, cls: 'text-green-600' },
            { label: '失败', value: stats.failed, cls: 'text-red-600' },
            { label: '阻塞', value: stats.blocked, cls: 'text-amber-600' },
            { label: '未执行', value: stats.unexecuted, cls: 'text-muted-foreground' },
            { label: '通过率', value: `${stats.passRate}%`, cls: 'text-blue-600' },
          ].map((s) => (
            <Card key={s.label} className="p-3">
              <p className="text-xs text-muted-foreground">{s.label}</p>
              <p className={`mt-1 text-xl font-bold ${s.cls}`}>{s.value}</p>
            </Card>
          ))}
        </div>
      )}

      {/* 工具栏 */}
      <div className="mb-4 flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="搜索用例..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className="pl-9"
          />
        </div>
        {/* 导出 CSV */}
        <Button
          variant="outline"
          className="gap-1.5"
          onClick={() =>
            downloadCsv(
              `测试用例-${new Date().toISOString().slice(0, 10)}`,
              ['标题', '优先级', '前置条件', '步骤', '预期结果', '最新结果', '执行次数', '最近执行人'],
              cases.map((c) => [
                c.title,
                { CRITICAL: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' }[c.priority] || c.priority,
                c.precondition || '',
                stepsSummary(c.steps),
                c.expectedResult || '',
                RESULT_CONFIG[c.latestResult]?.label || c.latestResult,
                c.executionCount,
                c.latestExecutorName || '',
              ]),
            )
          }
          title="导出用例列表"
        >
          <Download className="h-4 w-4" />
          导出
        </Button>
        {canManage && (
          <Button className="gap-1.5" onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" />
            新建用例
          </Button>
        )}
      </div>

      {/* 用例列表 */}
      {isLoading ? (
        <div className="flex flex-1 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : cases.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-muted-foreground">
          <FlaskConical className="h-12 w-12 opacity-30" />
          <p className="text-sm">暂无测试用例</p>
          {canManage && (
            <Button variant="link" size="sm" onClick={() => setCreateOpen(true)}>
              创建第一个用例
            </Button>
          )}
        </div>
      ) : (
        <div className="flex-1 space-y-2 overflow-auto pb-4 pr-1">
          {cases.map((c) => {
            const resultMeta = RESULT_CONFIG[c.latestResult] || RESULT_CONFIG.UNEXECUTED;
            const ResultIcon =
              c.latestResult === 'PASS' ? CheckCircle2
              : c.latestResult === 'FAIL' ? XCircle
              : c.latestResult === 'BLOCKED' ? MinusCircle
              : c.latestResult === 'SKIP' ? SkipForward
              : CircleDashed;
            return (
              <CaseRow
                key={c.id}
                c={c}
                resultMeta={resultMeta}
                ResultIcon={ResultIcon}
                canManage={canManage}
                onExecute={() => setExecCase(c)}
                onEdit={() => setEditCase(c)}
                onDelete={async () => {
                  if (!confirm(`确定要删除用例「${c.title}」吗？`)) return;
                  await deleteCase.mutateAsync(c.id);
                }}
                onToBug={async () => {
                  if (!confirm(`将用例「${c.title}」转为缺陷？会自动生成一条 Bug 并关联。`)) return;
                  try {
                    const r = await toBug.mutateAsync({ id: c.id });
                    alert(`已创建缺陷：${r.data?.title || ''}`);
                  } catch (e) {
                    alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '转缺陷失败');
                  }
                }}
                toBugPending={toBug.isPending}
              />
            );
          })}
        </div>
      )}

      {/* 新建/编辑弹窗（共用表单） */}
      <CaseFormDialog
        open={createOpen || !!editCase}
        onOpenChange={(o) => {
          if (!o) {
            setCreateOpen(false);
            setEditCase(null);
          }
        }}
        projectId={projectId}
        editing={editCase}
        createCase={createCase}
        updateCase={updateCase}
      />

      {/* 执行弹窗 */}
      <ExecuteDialog
        c={execCase}
        onOpenChange={(o) => !o && setExecCase(null)}
        executeCase={executeCase}
        onToBug={async (id, note) => {
          const r = await toBug.mutateAsync({ id, note });
          alert(`已创建缺陷：${r.data?.title || ''}`);
        }}
      />
    </div>
  );
}

// ============================================
// 用侧行
// ============================================
function CaseRow({
  c,
  resultMeta,
  ResultIcon,
  canManage,
  onExecute,
  onEdit,
  onDelete,
  onToBug,
  toBugPending,
}: {
  c: TestCaseView;
  resultMeta: { label: string; cls: string };
  ResultIcon: React.ElementType;
  canManage: boolean;
  onExecute: () => void;
  onEdit: () => void;
  onDelete: () => void;
  onToBug: () => void;
  toBugPending: boolean;
}) {
  return (
    <Card className="group ring-foreground/20 hover:shadow-md transition-shadow">
      <CardContent className="flex items-center gap-3 p-3">
        <FlaskConical className="h-4 w-4 shrink-0 text-muted-foreground" />
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <h4 className="truncate text-sm font-medium">{c.title}</h4>
            <Badge variant="outline" className="text-[10px]">
              {PRIORITY_LABELS[c.priority] || c.priority}
            </Badge>
          </div>
          <div className="mt-0.5 flex items-center gap-2 text-[11px] text-muted-foreground">
            {c.latestExecutedAt && (
              <>
                <span>最近执行：{c.latestExecutorName || '-'}</span>
                <span>·</span>
                <span>{new Date(c.latestExecutedAt).toLocaleDateString('zh-CN')}</span>
                <span>·</span>
              </>
            )}
            <span>执行 {c.executionCount} 次</span>
          </div>
        </div>
        <Badge variant="outline" className={`shrink-0 border-0 text-[10px] ${resultMeta.cls}`}>
          <ResultIcon className="mr-1 h-3 w-3" />
          {resultMeta.label}
        </Badge>
        {canManage && (
          <div className="flex shrink-0 gap-0.5 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100">
            <Button variant="ghost" size="icon" className="h-7 w-7" title="执行" onClick={onExecute}>
              <Play className="h-3 w-3" />
            </Button>
            <Button variant="ghost" size="icon" className="h-7 w-7" title="编辑" onClick={onEdit}>
              <Pencil className="h-3 w-3" />
            </Button>
            {c.latestResult === 'FAIL' && (
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                title="转缺陷"
                disabled={toBugPending}
                onClick={onToBug}
              >
                <ExternalLink className="h-3 w-3" />
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-red-500 hover:text-red-600"
              title="删除"
              onClick={onDelete}
            >
              <Trash2 className="h-3 w-3" />
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================
// 新建/编辑用例弹窗（步骤动态行）
// ============================================
function CaseFormDialog({
  open,
  onOpenChange,
  projectId,
  editing,
  createCase,
  updateCase,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string;
  editing: TestCaseView | null;
  createCase: ReturnType<typeof useCreateTestCase>;
  updateCase: ReturnType<typeof useUpdateTestCase>;
}) {
  const { data: reqRes } = useRequirements(projectId);
  const requirements = reqRes?.data?.records || [];

  const [title, setTitle] = useState('');
  const [precondition, setPrecondition] = useState('');
  const [stepRows, setStepRows] = useState<StepRow[]>([{ action: '', expected: '' }]);
  const [expectedResult, setExpectedResult] = useState('');
  const [priority, setPriority] = useState<'HIGH' | 'MEDIUM' | 'LOW'>('MEDIUM');
  const [requirementId, setRequirementId] = useState('');

  // 编辑时回显（弹窗打开瞬间同步一次）
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  if (open && editing && loadedFor !== editing.id) {
    setLoadedFor(editing.id);
    setTitle(editing.title);
    setPrecondition(editing.precondition || '');
    setStepRows(parseSteps(editing.steps).length > 0 ? parseSteps(editing.steps).map((x) => ({ action: x.action, expected: x.expected || '' })) : [{ action: '', expected: '' }]);
    setExpectedResult(editing.expectedResult || '');
    setPriority((editing.priority as 'HIGH' | 'MEDIUM' | 'LOW') || 'MEDIUM');
    setRequirementId(editing.requirementId || '');
  }
  if (!open && loadedFor) {
    setLoadedFor(null);
  }

  const handleSave = async () => {
    if (!title.trim()) return;
    const stepsJson = JSON.stringify(
      stepRows.filter((r) => r.action.trim()).map((r) => ({ action: r.action.trim(), expected: (r.expected || '').trim() })),
    );
    const data = {
      title: title.trim(),
      precondition: precondition.trim() || undefined,
      steps: stepsJson,
      expectedResult: expectedResult.trim() || undefined,
      priority,
      requirementId: requirementId || undefined,
    };
    if (editing) {
      await updateCase.mutateAsync({ id: editing.id, data });
    } else {
      await createCase.mutateAsync(data);
    }
    onOpenChange(false);
    setTitle('');
    setPrecondition('');
    setStepRows([{ action: '', expected: '' }]);
    setExpectedResult('');
    setPriority('MEDIUM');
    setRequirementId('');
    setLoadedFor(null);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>{editing ? '编辑用例' : '新建测试用例'}</DialogTitle>
          <DialogDescription>描述前置条件与操作步骤，执行时记录通过/失败</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-1">
            <Label>用例标题 *</Label>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="例如：导出报表-中文文件名" />
          </div>

          <div className="space-y-1">
            <Label>前置条件</Label>
            <Input
              value={precondition}
              onChange={(e) => setPrecondition(e.target.value)}
              placeholder="例如：已登录且有可导出的项目"
            />
          </div>

          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <Label>操作步骤</Label>
              <Button
                variant="outline"
                size="sm"
                className="h-7 gap-1 text-xs"
                onClick={() => setStepRows([...stepRows, { action: '', expected: '' }])}
              >
                <Plus className="h-3 w-3" /> 加步骤
              </Button>
            </div>
            <div className="space-y-2">
              {stepRows.map((row, i) => (
                <div key={i} className="flex items-start gap-2">
                  <span className="pt-2 text-xs text-muted-foreground">{i + 1}.</span>
                  <div className="flex-1 space-y-1">
                    <Input
                      value={row.action}
                      onChange={(e) => {
                        const next = [...stepRows];
                        next[i] = { ...next[i], action: e.target.value };
                        setStepRows(next);
                      }}
                      placeholder={`步骤 ${i + 1}：操作`}
                      className="h-8 text-xs"
                    />
                    <Input
                      value={row.expected}
                      onChange={(e) => {
                        const next = [...stepRows];
                        next[i] = { ...next[i], expected: e.target.value };
                        setStepRows(next);
                      }}
                      placeholder="该步预期结果（可选）"
                      className="h-8 text-xs"
                    />
                  </div>
                  {stepRows.length > 1 && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="mt-1 h-7 w-7 shrink-0"
                      onClick={() => setStepRows(stepRows.filter((_, j) => j !== i))}
                    >
                      <X className="h-3.5 w-3.5" />
                    </Button>
                  )}
                </div>
              ))}
            </div>
          </div>

          <div className="space-y-1">
            <Label>预期结果（整体）</Label>
            <Input
              value={expectedResult}
              onChange={(e) => setExpectedResult(e.target.value)}
              placeholder="例如：导出的 PDF 内容与页面一致"
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label>优先级</Label>
              <select
                value={priority}
                onChange={(e) => setPriority(e.target.value as 'HIGH' | 'MEDIUM' | 'LOW')}
                className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="HIGH">高</option>
                <option value="MEDIUM">中</option>
                <option value="LOW">低</option>
              </select>
            </div>
            <div className="space-y-1">
              <Label>关联需求（可选）</Label>
              <select
                value={requirementId}
                onChange={(e) => setRequirementId(e.target.value)}
                className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="">不关联</option>
                {requirements.map((r) => (
                  <option key={r.id} value={r.id}>{r.title}</option>
                ))}
              </select>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button onClick={handleSave} disabled={createCase.isPending || updateCase.isPending || !title.trim()}>
            {(createCase.isPending || updateCase.isPending) ? '保存中...' : editing ? '保存修改' : '创建用例'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ============================================
// 执行弹窗
// ============================================
function ExecuteDialog({
  c,
  onOpenChange,
  executeCase,
  onToBug,
}: {
  c: TestCaseView | null;
  onOpenChange: (open: boolean) => void;
  executeCase: ReturnType<typeof useExecuteTestCase>;
  onToBug: (id: string, note?: string) => Promise<void>;
}) {
  const [result, setResult] = useState('PASS');
  const [note, setNote] = useState('');
  const [bugCreated, setBugCreated] = useState<string | null>(null);
  const [converting, setConverting] = useState(false);

  // 打开时重置
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  if (c && loadedFor !== c.id) {
    setLoadedFor(c.id);
    setResult('PASS');
    setNote('');
    setBugCreated(null);
  }
  if (!c && loadedFor) {
    setLoadedFor(null);
  }

  const steps = parseSteps(c?.steps);
  const lastFailNote = c?.latestResult === 'FAIL' ? c?.title : null;

  const handleExecute = async () => {
    if (!c) return;
    await executeCase.mutateAsync({ id: c.id, result, note: note.trim() || undefined });
    onOpenChange(false);
  };

  const handleToBug = async () => {
    if (!c) return;
    setConverting(true);
    try {
      await onToBug(c.id, note.trim() || undefined);
      setBugCreated('ok');
    } finally {
      setConverting(false);
    }
  };

  return (
    <Dialog open={!!c} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] max-w-xl overflow-y-auto">
        <DialogHeader>
          <DialogTitle>执行用例</DialogTitle>
          <DialogDescription>{c?.title}</DialogDescription>
        </DialogHeader>

        <div className="space-y-3 text-sm">
          {c?.precondition && (
            <p className="rounded-md border bg-muted/30 p-2 text-xs">
              <span className="font-medium">前置条件：</span>
              {c.precondition}
            </p>
          )}
          {steps.length > 0 && (
            <div>
              <p className="mb-1 text-xs font-medium">操作步骤</p>
              <ol className="list-inside list-decimal space-y-1 rounded-md border bg-muted/30 p-2 text-xs">
                {steps.map((s, i) => (
                  <li key={i}>
                    {s.action}
                    {s.expected && <span className="text-muted-foreground">（预期：{s.expected}）</span>}
                  </li>
                ))}
              </ol>
            </div>
          )}
          {c?.expectedResult && (
            <p className="rounded-md border bg-muted/30 p-2 text-xs">
              <span className="font-medium">预期结果：</span>
              {c.expectedResult}
            </p>
          )}
          {lastFailNote && c?.latestResult === 'FAIL' && (
            <p className="text-xs text-red-500">当前状态为失败，建议填写备注并转缺陷跟进</p>
          )}
        </div>

        <div className="flex items-center gap-2">
          {(['PASS', 'FAIL', 'BLOCKED', 'SKIP'] as const).map((r) => (
            <button
              key={r}
              onClick={() => setResult(r)}
              className={`flex-1 rounded-md border px-2 py-1.5 text-xs font-medium transition-colors ${
                result === r ? 'border-primary bg-primary/10 text-primary' : 'hover:bg-muted/50'
              }`}
            >
              {RESULT_CONFIG[r].label}
            </button>
          ))}
        </div>

        <Input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="执行备注（可选，例如实际结果或失败原因）"
          className="text-xs"
        />

        {result === 'FAIL' && (
          <Button
            variant="outline"
            size="sm"
            className="w-full gap-1 text-xs"
            disabled={converting}
            onClick={handleToBug}
          >
            <ExternalLink className="h-3 w-3" />
            {converting ? '转换中...' : '一键转缺陷（自动生成 Bug 并关联）'}
          </Button>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>取消</Button>
          <Button onClick={handleExecute} disabled={executeCase.isPending}>
            {executeCase.isPending ? '提交中...' : '记录结果'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
