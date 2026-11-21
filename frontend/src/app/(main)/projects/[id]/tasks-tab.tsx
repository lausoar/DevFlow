'use client';

import { useState, useEffect } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { useInfiniteTasks, useTasks, useCreateTask, useUpdateTaskStatus, useUpdateTask, useDeleteTask } from '@/lib/hooks/use-tasks';
import { useSprints, useCreateSprint, useActivateSprint, useCompleteSprint } from '@/lib/hooks/use-sprints';
import {
  useTaskExtras,
  useTaskChecklist,
  useTaskDependencies,
  useAddChecklistItem,
  useUpdateChecklistItem,
  useDeleteChecklistItem,
  useAddDependency,
  useRemoveDependency,
  useWorklogs,
  useAddWorklog,
  useDeleteWorklog,
} from '@/lib/hooks/use-task-extras';
import { Progress } from '@/components/ui/progress';
import { Loader2 } from 'lucide-react';
import { Plus, Search, GripVertical, Calendar, User, Zap, ChevronDown, ChevronRight, Pencil, Trash2, MessageSquare, X, Link2, Timer, Download } from 'lucide-react';
import { exportApi } from '@/lib/api/export';
import { useProject, useProjectMembers } from '@/lib/hooks/use-projects';
import { useWorkflow } from '@/lib/hooks/use-workflow';
import type { WorkflowStatusDef } from '@/lib/api/workflow';
import {
  DndContext,
  PointerSensor,
  MouseSensor,
  useSensor,
  useSensors,
  useDraggable,
  useDroppable,
  pointerWithin,
  type DragEndEvent,
} from '@dnd-kit/core';
import type { Task, TaskStatus, Priority, Sprint } from '@/types';
import type { TaskChecklistItem } from '@/lib/api/task';
import { TaskCommentsPanel } from './task-comments-panel';
import { CustomFieldsSection } from './custom-fields-section';
import { SavedFiltersPanel } from './saved-filters-panel';
import { SubscribeButton } from '@/components/common/subscribe-button';
import { queryApi } from '@/lib/api/query';

// ============================================
// 看板列定义
// ============================================
/** 兜底列：工作流尚未配置 / 请求失败时使用，与后端内置默认流程一致 */
const FALLBACK_COLUMNS: WorkflowStatusDef[] = [
  { code: 'TODO', name: '待办', category: 'TODO', color: 'bg-surface-4', next: ['DOING'] },
  { code: 'DOING', name: '进行中', category: 'IN_PROGRESS', color: 'bg-info', next: ['TESTING', 'TODO'] },
  { code: 'TESTING', name: '测试中', category: 'IN_PROGRESS', color: 'bg-warning', next: ['DONE', 'DOING'] },
  { code: 'DONE', name: '已完成', category: 'DONE', color: 'bg-success', next: ['TESTING'] },
];

/** 状态标签 → 判断是否"完成"（按工作流分类，而不是状态码） */
const isDoneStatus = (columns: WorkflowStatusDef[], status: string) =>
  columns.some((c) => c.code === status && c.category === 'DONE');

const labelOf = (columns: WorkflowStatusDef[], status: string) =>
  columns.find((c) => c.code === status)?.name || status;

// 优先级配置
const PRIORITY_CONFIG: Record<string, { label: string; color: string }> = {
  CRITICAL: { label: '紧急', color: 'bg-danger-muted text-danger ' },
  HIGH: { label: '高', color: 'bg-orange-100 text-orange-700 ' },
  MEDIUM: { label: '中', color: 'bg-info-muted text-info ' },
  LOW: { label: '低', color: 'bg-surface-2 text-ink-subtle ' },
};

// ============================================
// 任务看板标签页
// ============================================
export function TasksTab({ projectId }: { projectId: string }) {
  const [search, setSearch] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [sprintOpen, setSprintOpen] = useState(false);
  const [sprintExpanded, setSprintExpanded] = useState(true);
  const [editTask, setEditTask] = useState<Task | null>(null);

  const {
    data: taskPages,
    isLoading,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
  } = useInfiniteTasks(projectId, { keyword: search.trim() || undefined });
  // 看板列由项目工作流决定（支持自定义状态）；未加载完成时用兜底列保证不空白
  const { data: workflowRes } = useWorkflow(projectId, 'TASK');
  const COLUMNS: WorkflowStatusDef[] =
    workflowRes?.data?.statuses && workflowRes.data.statuses.length > 0
      ? workflowRes.data.statuses
      : FALLBACK_COLUMNS;
  const createTask = useCreateTask(projectId);
  const updateStatus = useUpdateTaskStatus(projectId);
  const updateTask = useUpdateTask(projectId);
  const deleteTask = useDeleteTask(projectId);

  // Sprint 相关
  const { data: sprintsRes } = useSprints(projectId);
  const { data: extrasRes } = useTaskExtras(projectId);
  const taskExtras = extrasRes?.data;
  const createSprint = useCreateSprint(projectId);
  const activateSprint = useActivateSprint(projectId);
  const completeSprint = useCompleteSprint(projectId);

  const sprints = sprintsRes?.data || [];
  const activeSprint = sprints.find((s) => s.status === 'ACTIVE');

  // 创建 Sprint 表单
  const [newSprint, setNewSprint] = useState({ name: '', goal: '' });
  const handleCreateSprint = async () => {
    if (!newSprint.name.trim()) return;
    await createSprint.mutateAsync(newSprint);
    setNewSprint({ name: '', goal: '' });
    setSprintOpen(false);
  };

  const tasks = taskPages?.pages.flatMap((page) => page.data?.records || []) || [];
  const latestTaskPage = taskPages?.pages[taskPages.pages.length - 1]?.data;
  const taskTotal = latestTaskPage?.total ?? tasks.length;
  const hiddenTaskCount = Math.max(0, taskTotal - tasks.length);

  // 成员名解析（导出列用）
  const { data: taskMembersRes } = useProjectMembers(projectId);
  const { data: projectRes } = useProject(projectId);
  const taskMemberMap: Record<string, string> = {};
  (taskMembersRes?.data || []).forEach((m) => {
    taskMemberMap[m.userId] = m.realName || m.username || '';
  });

  // 导出已加载且命中当前服务端搜索、保存视图的看板任务。
  // 未加载完时按钮文案与分页提示都会明确这一范围。
  // 执行保存视图：用 /query 取回命中任务的 id 集合，再看板内按 id 过滤。
  // 这样看板本身（拖拽、分页）不受影响，只是把列表缩到视图范围内。
  const [viewIds, setViewIds] = useState<Set<string> | null>(null);
  const [viewLabel, setViewLabel] = useState<string | null>(null);
  const [viewLoading, setViewLoading] = useState(false);
  const handleApplyView = async (expression: string) => {
    setViewLoading(true);
    try {
      const pageSize = 200;
      const first = await queryApi.run({
        entity: 'TASK',
        expression,
        projectId,
        page: 1,
        pageSize,
      });
      const records = [...(first.data.records || [])];
      const totalPages = Math.max(1, Math.ceil(first.data.total / pageSize));
      for (let page = 2; page <= totalPages; page += 1) {
        const next = await queryApi.run({ entity: 'TASK', expression, projectId, page, pageSize });
        records.push(...(next.data.records || []));
      }
      const ids = new Set(records.map((r) => String((r as { id?: string }).id)));
      setViewIds(ids);
      setViewLabel(expression);
    } catch (e) {
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '视图执行失败');
    } finally {
      setViewLoading(false);
    }
  };

  // 导出走服务端：取全量数据、统一转义并写审计，不再只导出已加载的那一页
  const handleExportTasks = () => {
    exportApi.downloadIssues(projectId, 'tasks', {
      keyword: search.trim() || undefined,
      expression: viewLabel || undefined,
    }, projectRes?.data?.name).catch(() => {
      alert('导出失败，请稍后重试');
    });
  };

  // 搜索由服务端完成；保存视图只在已加载的搜索结果内按 id 过滤。
  const filteredTasks = tasks.filter((t) => !viewIds || viewIds.has(t.id));

  // 按状态分组（列来自工作流配置）
  const tasksByStatus: Record<string, Task[]> = {};
  COLUMNS.forEach((c) => { tasksByStatus[c.code] = []; });
  filteredTasks.forEach((t) => {
    // 未知状态（历史数据 / 已被删除的状态）单独归到"其他"，避免任务在看板上凭空消失
    if (!tasksByStatus[t.status]) {
      tasksByStatus[t.status] = [];
      if (!COLUMNS.some((c) => c.code === t.status)) {
        COLUMNS.push({ code: t.status, name: t.status, category: 'TODO', color: 'bg-muted', next: [] });
      }
    }
    tasksByStatus[t.status].push(t);
  });

  // 处理状态变更
  const handleStatusChange = (taskId: string, newStatus: string) => {
    // 软提醒：前置任务未完成时不阻止，但明确确认
    const undone = taskExtras?.undoneBlockers?.[taskId] || 0;
    if (isDoneStatus(COLUMNS, newStatus) && undone > 0) {
      if (!confirm(`该任务还有 ${undone} 个前置任务未完成，确定要标记为完成吗？`)) return;
    }
    updateStatus.mutate({ id: taskId, status: newStatus });
  };

  // 看板拖拽：落点是列（列 id = 状态值）或列内另一张卡片
  // PointerSensor 覆盖标准指针设备；MouseSensor 兼容部分环境只派发鼠标事件的场景
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(MouseSensor, { activationConstraint: { distance: 6 } }),
  );
  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over) return;
    const taskId = String(active.id);
    const task = tasks.find((t) => t.id === taskId);
    if (!task) return;
    const overId = String(over.id);
    const targetStatus = COLUMNS.some((c) => c.code === overId)
      ? overId
      : tasks.find((t) => t.id === overId)?.status;
    if (targetStatus && targetStatus !== task.status) {
      handleStatusChange(taskId, targetStatus);
    }
  };

  // 处理删除任务
  const handleDeleteTask = async (taskId: string) => {
    if (!confirm('确定要删除此任务吗？')) return;
    await deleteTask.mutateAsync(taskId);
  };

  // 创建任务表单（默认挂到当前活跃迭代，避免"新建的任务永远不在迭代里"）
  const [newTask, setNewTask] = useState<{
    title: string;
    description: string;
    priority: Priority;
    sprintId: string;
    assigneeId: string;
  }>({
    title: '',
    description: '',
    priority: 'MEDIUM',
    sprintId: '',
    assigneeId: '',
  });

  const handleCreate = async () => {
    if (!newTask.title.trim()) return;
    await createTask.mutateAsync({
      title: newTask.title,
      description: newTask.description || undefined,
      priority: newTask.priority,
      sprintId: newTask.sprintId || undefined,
      assigneeId: newTask.assigneeId || undefined,
    });
    // 保留本次选择的迭代，便于连续录入同一批次任务
    setNewTask({ title: '', description: '', priority: 'MEDIUM', sprintId: newTask.sprintId, assigneeId: '' });
    setCreateOpen(false);
  };

  // 新建任务默认落点：活跃迭代优先，其次第一个规划中的迭代
  const defaultSprintId = activeSprint?.id || sprints.find((sp) => sp.status === 'PLANNING')?.id || '';

  return (
    <div className="flex h-full flex-col">
      {/* 工具栏 */}
      <div className="mb-4 flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="搜索任务..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        {viewLoading ? (
          <Button variant="outline" size="sm" className="gap-1" disabled>
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            加载视图
          </Button>
        ) : (
          <SavedFiltersPanel projectId={projectId} entity="TASK" onRun={handleApplyView} />
        )}
        <Button
          variant="outline"
          size="sm"
          className="gap-1"
          onClick={handleExportTasks}
          title="从服务端导出全部任务（含审计记录）"
        >
          <Download className="h-3.5 w-3.5" />
          导出
        </Button>
        <Dialog
          open={createOpen}
          onOpenChange={(open) => {
            setCreateOpen(open);
            if (open) setNewTask((prev) => ({ ...prev, sprintId: prev.sprintId || defaultSprintId }));
          }}
        >
          <DialogTrigger className="inline-flex items-center gap-1.5 rounded-md bg-brand px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-brand/90">
            <Plus className="h-4 w-4" />
            新建任务
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>新建任务</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <div>
                <Label>标题</Label>
                <Input
                  value={newTask.title}
                  onChange={(e) => setNewTask({ ...newTask, title: e.target.value })}
                  placeholder="输入任务标题"
                />
              </div>
              <div>
                <Label>描述</Label>
                <textarea
                  value={newTask.description}
                  onChange={(e) => setNewTask({ ...newTask, description: e.target.value })}
                  placeholder="输入任务描述（可选）"
                  className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                />
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <div>
                  <Label>优先级</Label>
                  <select
                    value={newTask.priority}
                    onChange={(e) => setNewTask({ ...newTask, priority: e.target.value as Priority })}
                    className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <option value="LOW">低</option>
                    <option value="MEDIUM">中</option>
                    <option value="HIGH">高</option>
                    <option value="CRITICAL">紧急</option>
                  </select>
                </div>
                <div>
                  <Label>所属迭代</Label>
                  <select
                    value={newTask.sprintId}
                    onChange={(e) => setNewTask({ ...newTask, sprintId: e.target.value })}
                    className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <option value="">不放入迭代</option>
                    {sprints
                      .filter((sp) => sp.status !== 'COMPLETED')
                      .map((sp) => (
                        <option key={sp.id} value={sp.id}>{sp.name}</option>
                      ))}
                  </select>
                </div>
                <div>
                  <Label>负责人</Label>
                  <select
                    value={newTask.assigneeId}
                    onChange={(e) => setNewTask({ ...newTask, assigneeId: e.target.value })}
                    className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <option value="">未指派</option>
                    {(taskMembersRes?.data || []).map((m) => (
                      <option key={m.userId} value={m.userId}>{m.realName || m.username}</option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={() => setCreateOpen(false)}>
                  取消
                </Button>
                <Button onClick={handleCreate} disabled={!newTask.title.trim()}>
                  创建
                </Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      </div>

      {/* 当前生效的视图 */}
      {viewIds && (
        <div className="mb-3 flex items-center gap-2 rounded-md border border-primary/30 bg-brand/5 px-3 py-1.5 text-xs">
          <span className="font-medium">视图生效</span>
          <code className="flex-1 truncate text-muted-foreground">{viewLabel}</code>
          <Button
            variant="ghost" size="sm" className="h-6 text-xs"
            onClick={() => { setViewIds(null); setViewLabel(null); }}
          >
            清除
          </Button>
        </div>
      )}

      {/* Sprint 管理面板 */}
      <div className="mb-4 rounded-xl border bg-card">
        <button
          onClick={() => setSprintExpanded(!sprintExpanded)}
          className="flex w-full items-center gap-2 px-4 py-3 text-left"
        >
          {sprintExpanded ? (
            <ChevronDown className="h-4 w-4 text-muted-foreground" />
          ) : (
            <ChevronRight className="h-4 w-4 text-muted-foreground" />
          )}
          <Zap className="h-4 w-4 text-brand" />
          <span className="text-sm font-medium">迭代管理</span>
          {activeSprint && (
            <Badge variant="default" className="ml-2 text-[10px]">
              {activeSprint.name}
            </Badge>
          )}
          <span className="ml-auto text-xs text-muted-foreground">
            共 {sprints.length} 个迭代
          </span>
        </button>

        {sprintExpanded && (
          <div className="border-t px-4 py-3">
            {/* 活跃 Sprint 进度 */}
            {activeSprint && (
              <div className="mb-3 rounded-lg bg-brand/5 p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-sm font-medium">{activeSprint.name}</span>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-muted-foreground">
                      {activeSprint.goal || '无目标'}
                    </span>
                    <Button
                      size="sm"
                      variant="outline"
                      className="h-6 text-xs"
                      onClick={() => {
                        const unfinished = tasks.filter(
                          (t) => t.sprintId === activeSprint.id && t.status !== 'DONE'
                        ).length;
                        const msg = unfinished > 0
                          ? `完成迭代「${activeSprint.name}」？其中 ${unfinished} 个未完成任务将被移出迭代`
                          : `确定完成迭代「${activeSprint.name}」吗？`;
                        if (confirm(msg)) completeSprint.mutate(activeSprint.id);
                      }}
                    >
                      完成
                    </Button>
                  </div>
                </div>
                {/* Sprint 进度条 - 根据任务完成情况计算 */}
                <div className="flex items-center gap-2">
                  <Progress
                    value={
                      tasks.filter((t) => t.sprintId === activeSprint.id && t.status === 'DONE').length /
                      Math.max(tasks.filter((t) => t.sprintId === activeSprint.id).length, 1) * 100
                    }
                    className="h-1.5 flex-1"
                  />
                  <span className="text-xs text-muted-foreground">
                    {tasks.filter((t) => t.sprintId === activeSprint.id && t.status === 'DONE').length}
                    /
                    {tasks.filter((t) => t.sprintId === activeSprint.id).length}
                  </span>
                </div>
              </div>
            )}

            {/* Sprint 列表（限高滚动，迭代多时不挤压看板） */}
            <div className="mb-3 max-h-56 space-y-1.5 overflow-y-auto pr-1">
              {sprints.map((sprint) => (
                <div
                  key={sprint.id}
                  className="flex items-center gap-3 rounded-lg px-3 py-2 hover:bg-muted/50"
                >
                  <span
                    className={`h-2 w-2 rounded-full ${
                      sprint.status === 'ACTIVE'
                        ? 'bg-success'
                        : sprint.status === 'COMPLETED'
                        ? 'bg-surface-4'
                        : 'bg-info'
                    }`}
                  />
                  <span className="flex-1 text-sm">{sprint.name}</span>
                  <Badge
                    variant="outline"
                    className="text-[10px]"
                  >
                    {sprint.status === 'PLANNING' ? '规划中' : sprint.status === 'ACTIVE' ? '进行中' : '已完成'}
                  </Badge>
                  {sprint.status === 'PLANNING' && (
                    <Button
                      size="sm"
                      variant="ghost"
                      className="h-6 text-xs"
                      onClick={() => activateSprint.mutate(sprint.id)}
                    >
                      激活
                    </Button>
                  )}
                </div>
              ))}
              {sprints.length === 0 && (
                <p className="py-2 text-center text-xs text-muted-foreground">暂无迭代</p>
              )}
            </div>

            {/* 创建 Sprint */}
            <Dialog open={sprintOpen} onOpenChange={setSprintOpen}>
              <DialogTrigger className="inline-flex w-full items-center justify-center gap-1.5 rounded-md border border-dashed py-2 text-xs text-muted-foreground hover:bg-muted/50 hover:text-foreground">
                <Plus className="h-3.5 w-3.5" />
                新建迭代
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>新建迭代</DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                  <div>
                    <Label>名称</Label>
                    <Input
                      value={newSprint.name}
                      onChange={(e) => setNewSprint({ ...newSprint, name: e.target.value })}
                      placeholder="例如：迭代 1"
                    />
                  </div>
                  <div>
                    <Label>目标</Label>
                    <textarea
                      value={newSprint.goal}
                      onChange={(e) => setNewSprint({ ...newSprint, goal: e.target.value })}
                      placeholder="本次迭代的目标（可选）"
                      className="flex min-h-[60px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    />
                  </div>
                  <div className="flex justify-end gap-2">
                    <Button variant="outline" onClick={() => setSprintOpen(false)}>
                      取消
                    </Button>
                    <Button onClick={handleCreateSprint} disabled={!newSprint.name.trim()}>
                      创建
                    </Button>
                  </div>
                </div>
              </DialogContent>
            </Dialog>
          </div>
        )}
      </div>

      {/* 看板与导出基于已加载结果；剩余任务可逐页继续加载。 */}
      {hiddenTaskCount > 0 && (
        <div className="mb-3 flex flex-wrap items-center gap-3 rounded-md border bg-muted/30 px-3 py-2 text-xs text-muted-foreground">
          <span>
            已加载 {tasks.length} / {taskTotal} 条任务，剩余 {hiddenTaskCount} 条。
            当前看板仅显示已加载结果；导出由服务端按当前条件生成。
          </span>
          <Button
            variant="outline"
            size="sm"
            className="ml-auto h-7 gap-1.5"
            disabled={!hasNextPage || isFetchingNextPage}
            onClick={() => fetchNextPage()}
          >
            {isFetchingNextPage && <Loader2 className="h-3.5 w-3.5 animate-spin" />}
            {isFetchingNextPage ? '加载中...' : `加载更多（剩余 ${hiddenTaskCount} 条）`}
          </Button>
        </div>
      )}

      {/* 看板 */}
      {isLoading ? (
        <div className="flex flex-1 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : (
        <DndContext sensors={sensors} collisionDetection={pointerWithin} onDragEnd={handleDragEnd}>
          <div className="flex flex-col gap-4 overflow-y-auto pb-4 md:flex-row md:overflow-x-auto">
            {COLUMNS.map((col) => (
              <KanbanColumn
                key={col.code}
                status={col.code as TaskStatus}
                label={col.name}
                color={col.color || 'bg-surface-4'}
                tasks={tasksByStatus[col.code] || []}
                onStatusChange={handleStatusChange}
                onEdit={setEditTask}
                onDelete={handleDeleteTask}
                extras={taskExtras}
                memberNames={taskMemberMap}
                columns={COLUMNS}
              />
            ))}
          </div>
        </DndContext>
      )}

      {/* 编辑任务 Dialog */}
      <TaskEditDialog
        task={editTask}
        projectId={projectId}
        open={!!editTask}
        onOpenChange={(open) => { if (!open) setEditTask(null); }}
      />
    </div>
  );
}

// ============================================
// 编辑任务 Dialog（含评论面板）
// ============================================
function TaskEditDialog({
  task,
  projectId,
  open,
  onOpenChange,
}: {
  task: Task | null;
  projectId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const updateTask = useUpdateTask(projectId);
  const checklistQuery = useTaskChecklist(open && task ? task.id : null);
  const depsQuery = useTaskDependencies(open && task ? task.id : null);
  const worklogsQuery = useWorklogs(open && task ? task.id : null);
  // 表单与评论面板的初值以「哪个任务 + 是否打开」为 key：
  // 换任务或重新打开时在渲染期重置（React 官方推荐的 adjust-state-during-render），
  // 列表后台刷新不会覆盖正在输入的内容，也不需要 effect 回填。
  type TaskForm = {
    title: string;
    description: string;
    priority: Priority;
    dueDate: string;
    assigneeId: string;
    sprintId: string;
  };
  const toForm = (t: Task | null): TaskForm => ({
    title: t?.title || '',
    description: t?.description || '',
    priority: (t?.priority || 'MEDIUM') as Priority,
    dueDate: t?.dueDate ? t.dueDate.slice(0, 10) : '',
    assigneeId: t?.assigneeId || '',
    sprintId: t?.sprintId || '',
  });
  const dialogKey = `${task?.id ?? ''}|${open}`;
  const [draft, setDraft] = useState<{ key: string; form: TaskForm; showComments: boolean }>(
    () => ({ key: dialogKey, form: toForm(task), showComments: false }),
  );
  const current = draft.key === dialogKey ? draft : { key: dialogKey, form: toForm(task), showComments: false };
  const form = current.form;
  const showComments = current.showComments;
  const setForm = (next: TaskForm) => setDraft({ ...current, form: next });
  const setShowComments = (next: boolean) => setDraft({ ...current, showComments: next });

  // 迭代与项目成员（用于负责人/迭代选择）
  const { data: dialogSprintsRes } = useSprints(projectId);
  const dialogSprints = dialogSprintsRes?.data || [];
  const { data: dialogMembersRes } = useProjectMembers(projectId);
  const dialogMembers = dialogMembersRes?.data || [];

  if (!task) return null;

  const handleSave = async () => {
    if (!form.title.trim()) return;
    try {
      await updateTask.mutateAsync({
      id: task.id,
      data: {
        title: form.title,
        description: form.description || undefined,
        priority: form.priority,
        dueDate: form.dueDate || undefined,
        // 空串表示"清除"：后端会把空串解释为置空，而不是跳过
        assigneeId: form.assigneeId,
        sprintId: form.sprintId,
        // 乐观锁：带上打开弹窗时的版本号。别人先改了 → 服务端返回 409，
        // 而不是让我们的旧表单静默覆盖他的修改。
        expectedVersion: task.version,
        },
      });
      onOpenChange(false);
    } catch (e) {
      // 409 = 别人先改了：把服务端的说明原样给用户，并让他知道要刷新
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      alert(msg || '保存失败，请重试');
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <div className="flex items-center justify-between">
            <DialogTitle>{form.title || '编辑任务'}</DialogTitle>
            <div className="flex items-center gap-1.5">
              <SubscribeButton targetType="TASK" targetId={task.id} />
              <Button
                variant={showComments ? 'default' : 'outline'}
                size="sm"
                className="gap-1 h-7 text-xs"
                onClick={() => setShowComments(!showComments)}
              >
                <MessageSquare className="h-3 w-3" />
                评论
              </Button>
            </div>
          </div>
        </DialogHeader>

          <div className={showComments ? 'grid grid-cols-1 gap-4 lg:grid-cols-2' : ''}>
          {/* 左侧/全宽：编辑表单 */}
          <div className="space-y-4">
            <div>
              <Label>标题</Label>
              <Input
                value={form.title}
                onChange={(e) => setForm({ ...form, title: e.target.value })}
                placeholder="输入任务标题"
              />
            </div>
            <div>
              <Label>描述</Label>
              <textarea
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                placeholder="输入任务描述（可选）"
                className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </div>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div>
                <Label>优先级</Label>
                <select
                  value={form.priority}
                  onChange={(e) => setForm({ ...form, priority: e.target.value as Priority })}
                  className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="LOW">低</option>
                  <option value="MEDIUM">中</option>
                  <option value="HIGH">高</option>
                  <option value="CRITICAL">紧急</option>
                </select>
              </div>
              <div>
                <Label>截止日期</Label>
                <Input
                  type="date"
                  value={form.dueDate}
                  onChange={(e) => setForm({ ...form, dueDate: e.target.value })}
                />
              </div>
              <div>
                <Label>负责人</Label>
                <select
                  value={form.assigneeId}
                  onChange={(e) => setForm({ ...form, assigneeId: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">未指派</option>
                  {dialogMembers.map((m) => (
                    <option key={m.userId} value={m.userId}>{m.realName || m.username}</option>
                  ))}
                </select>
              </div>
              <div>
                <Label>所属迭代</Label>
                <select
                  value={form.sprintId}
                  onChange={(e) => setForm({ ...form, sprintId: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">不放入迭代</option>
                  {dialogSprints.map((sp) => (
                    <option key={sp.id} value={sp.id}>{sp.name}</option>
                  ))}
                </select>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => onOpenChange(false)}>
                取消
              </Button>
              <Button onClick={handleSave} disabled={updateTask.isPending || !form.title.trim()}>
                {updateTask.isPending ? '保存中...' : '保存'}
              </Button>
            </DialogFooter>
          </div>

          {/* 右侧：评论面板 */}
          {showComments && (
            <div className="border rounded-lg overflow-hidden">
              <TaskCommentsPanel taskId={task.id} />
            </div>
          )}
        </div>

        {/* 扩展字段（项目自定义） */}
        <CustomFieldsSection projectId={projectId} issueType="TASK" issueId={task.id} editable />

        {/* 清单（子任务） */}
        <TaskChecklistSection taskId={task.id} />

        {/* 任务依赖 */}
        <TaskDependenciesSection taskId={task.id} projectId={projectId} />

        {/* 工时登记 */}
        <TaskWorklogSection taskId={task.id} />
      </DialogContent>
    </Dialog>
  );
}

// ============================================
// 看板列组件
// ============================================
function KanbanColumn({
  status,
  label,
  color,
  tasks,
  onStatusChange,
  onEdit,
  onDelete,
  extras,
  memberNames,
  columns,
}: {
  status: TaskStatus;
  columns: WorkflowStatusDef[];
  label: string;
  color: string;
  tasks: Task[];
  onStatusChange: (taskId: string, newStatus: string) => void;
  onEdit: (task: Task) => void;
  onDelete: (taskId: string) => void;
    extras?: {
    checklist: Record<string, { total: number; done: number }>;
    undoneBlockers: Record<string, number>;
    worklogHours: Record<string, number>;
  };
  memberNames?: Record<string, string>;
}) {
  // 整列作为拖拽落点
  const { setNodeRef, isOver } = useDroppable({ id: status });

  return (
    <div
      ref={setNodeRef}
      className={`flex w-full flex-col rounded-xl bg-muted/50 md:w-auto md:min-w-[220px] md:flex-1 ${
        isOver ? 'ring-2 ring-primary/50' : ''
      }`}
    >
      {/* 列头 */}
      <div className="flex items-center gap-2 px-3 py-2.5">
        <span className={`h-2.5 w-2.5 rounded-full ${color}`} />
        <h3 className="text-sm font-medium">{label}</h3>
        <span className="ml-auto rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
          {tasks.length}
        </span>
      </div>

      {/* 任务卡片列表 */}
        <div className="flex flex-col gap-2 px-2 pb-2 md:flex-1 md:overflow-auto">
        {tasks.length === 0 ? (
          <div className="flex h-20 items-center justify-center text-xs text-muted-foreground">
            暂无任务
          </div>
        ) : (
          tasks.map((task) => (
            <TaskCard
              key={task.id}
              task={task}
              onStatusChange={onStatusChange}
              onEdit={onEdit}
              onDelete={onDelete}
              extras={extras}
              memberNames={memberNames}
              columns={columns}
            />
          ))
        )}
      </div>
    </div>
  );
}

// ============================================
// 任务卡片组件
// ============================================
function TaskCard({
  task,
  onStatusChange,
  onEdit,
  onDelete,
  extras,
  memberNames,
  columns,
}: {
  task: Task;
  columns: WorkflowStatusDef[];
  onStatusChange: (taskId: string, newStatus: string) => void;
  onEdit: (task: Task) => void;
  onDelete: (taskId: string) => void;
    extras?: {
    checklist: Record<string, { total: number; done: number }>;
    undoneBlockers: Record<string, number>;
    worklogHours: Record<string, number>;
  };
  /** userId → 展示名（负责人显示用） */
  memberNames?: Record<string, string>;
}) {
  const priority = PRIORITY_CONFIG[task.priority] || PRIORITY_CONFIG.MEDIUM;

  // 可流转目标由工作流配置决定（不再写死 TODO→DOING→TESTING→DONE）
  const currentColumn = columns.find((c) => c.code === task.status);
  const nextCandidates = (currentColumn?.next || []).filter((code) => code !== task.status);
  const nextStatus = nextCandidates[0] ?? null;
  const prevStatus = nextCandidates.length > 1 ? nextCandidates[nextCandidates.length - 1] : null;
  const nextLabel = nextStatus ? labelOf(columns, nextStatus) : null;

  // 整卡可拖拽（PointerSensor 带 6px 激活距离，不影响点击按钮）
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: task.id });

  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      style={
        transform
          ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`, zIndex: 50, position: 'relative' }
          : undefined
      }
      className={isDragging ? 'opacity-40' : ''}
    >
    <Card className="group cursor-grab hover:shadow-md transition-shadow">
      <CardContent className="p-3">
        {/* 优先级 + 操作 */}
        <div className="mb-2 flex items-center justify-between">
          <Badge variant="outline" className={`text-[10px] ${priority.color} border-transparent`}>
            {priority.label}
          </Badge>
            <div className="flex gap-0.5 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100">
            <Button
              variant="ghost"
              size="icon"
              className="h-5 w-5"
              onClick={() => onEdit(task)}
              title="编辑"
            >
              <Pencil className="h-3 w-3" />
            </Button>
            {prevStatus && (
              <Button
                variant="ghost"
                size="icon"
                className="h-5 w-5"
                onClick={() => onStatusChange(task.id, prevStatus)}
                title="回退状态"
              >
                ←
              </Button>
            )}
            {nextStatus && (
              <Button
                variant="ghost"
                size="icon"
                className="h-5 w-5"
                onClick={() => onStatusChange(task.id, nextStatus)}
                title="推进状态"
              >
                →
              </Button>
            )}
            <Button
              variant="ghost"
              size="icon"
              className="h-5 w-5 text-danger hover:text-danger"
              onClick={() => onDelete(task.id)}
              title="删除"
            >
              <Trash2 className="h-3 w-3" />
            </Button>
          </div>
        </div>

        {/* 标题 */}
        <h4 className="text-sm font-medium leading-snug">{task.title}</h4>

        {/* 清单进度 / 依赖徽章 */}
        {(() => {
          if (!extras) return null;
          const cl = extras.checklist[task.id];
          const blockers = extras.undoneBlockers[task.id] || 0;
          if (!(cl && cl.total > 0) && blockers === 0) return null;
          return (
            <div className="mt-1.5 flex items-center gap-2">
              {cl && cl.total > 0 && (
                <span className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] ${
                  cl.done === cl.total ? 'bg-success-muted text-success' : 'bg-muted text-muted-foreground'
                }`}>
                  ☑ {cl.done}/{cl.total}
                </span>
              )}
              {blockers > 0 && (
                <span className="inline-flex items-center gap-1 rounded bg-danger-muted px-1.5 py-0.5 text-[10px] text-danger">
                  ⛔ {blockers} 个前置未完成
                </span>
              )}
              {(() => {
                const h = extras.worklogHours?.[task.id];
                return h && h > 0 ? (
                  <span className="inline-flex items-center gap-1 rounded bg-info-muted px-1.5 py-0.5 text-[10px] text-info">
                    ⏱ {h}h
                  </span>
                ) : null;
              })()}
            </div>
          );
        })()}

        {/* 元信息 */}
        <div className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
          {task.storyPoints && (
            <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-[10px]">
              {task.storyPoints}
            </span>
          )}
          {task.dueDate && (
            <span className="flex items-center gap-1">
              <Calendar className="h-3 w-3" />
              {new Date(task.dueDate).toLocaleDateString('zh-CN')}
            </span>
          )}
          {task.assigneeId && (
            <span className="ml-auto flex items-center gap-1" title="负责人">
              <User className="h-3 w-3" />
              {memberNames?.[task.assigneeId] || '未知成员'}
            </span>
          )}
        </div>
      </CardContent>
    </Card>
    </div>
  );
}

// ============================================
// 任务清单区块（子任务，编辑弹窗内）
// ============================================
function TaskChecklistSection({ taskId }: { taskId: string }) {
  const queryClient = useQueryClient();
  const { data: res, isLoading } = useTaskChecklist(taskId);
  const addItem = useAddChecklistItem(taskId);
  const updateItem = useUpdateChecklistItem(taskId);
  const deleteItem = useDeleteChecklistItem(taskId);
  const [newTitle, setNewTitle] = useState('');

  const items = res?.data || [];
  const doneCount = items.filter((i: TaskChecklistItem) => i.done === 1).length;
  const percent = items.length > 0 ? Math.round((doneCount / items.length) * 100) : 0;

  const handleAdd = async () => {
    const title = newTitle.trim();
    if (!title) return;
    await addItem.mutateAsync(title);
    setNewTitle('');
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <Label>清单（子任务）</Label>
        <span className={`text-xs ${items.length > 0 && doneCount === items.length ? 'text-success' : 'text-muted-foreground'}`}>
          {items.length > 0 ? `${doneCount}/${items.length} 完成` : '拆解为可勾选的步骤'}
        </span>
      </div>

      {items.length > 0 && (
        <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
          <div className="h-full rounded-full bg-success transition-all" style={{ width: `${percent}%` }} />
        </div>
      )}

      <div className="space-y-1">
        {items.map((item) => (
          <div key={item.id} className="group flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-muted/50">
            <input
              type="checkbox"
              checked={item.done === 1}
              onChange={(e) => updateItem.mutate({ itemId: item.id, data: { done: e.target.checked } })}
              className="h-3.5 w-3.5 accent-green-600"
            />
            <span className={`min-w-0 flex-1 truncate text-sm ${item.done === 1 ? 'text-muted-foreground line-through' : ''}`}>
              {item.title}
            </span>
            <button
              className="shrink-0 text-muted-foreground opacity-0 transition-opacity hover:text-destructive group-hover:opacity-100"
              title="删除"
              onClick={() => deleteItem.mutate(item.id)}
            >
              <Trash2 className="h-3 w-3" />
            </button>
          </div>
        ))}
        {isLoading && <p className="py-1 text-xs text-muted-foreground">加载中...</p>}
      </div>

      <div className="flex items-center gap-2">
        <Input
          value={newTitle}
          onChange={(e) => setNewTitle(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter' && newTitle.trim()) { e.preventDefault(); handleAdd(); } }}
          placeholder="添加一个步骤，回车确认"
          className="h-8 text-xs"
        />
        <Button
          variant="outline"
          size="sm"
          className="h-8 shrink-0 text-xs"
          disabled={!newTitle.trim() || addItem.isPending}
          onClick={handleAdd}
        >
          添加
        </Button>
      </div>
    </div>
  );
}

// ============================================
// 任务依赖区块（编辑弹窗内）
// ============================================
function TaskDependenciesSection({ taskId, projectId }: { taskId: string; projectId: string }) {
  const { data: res, isLoading } = useTaskDependencies(taskId);
  const addDep = useAddDependency(taskId);
  const removeDep = useRemoveDependency(taskId);
  const { data: tasksRes } = useTasks(projectId);
  const [pickId, setPickId] = useState('');

  const view = res?.data;
  const blockedBy = view?.blockedBy || [];
  const blocking = view?.blocking || [];
  const allTasks = tasksRes?.data?.records || [];
  const existingIds = new Set([taskId, ...blockedBy.map((b) => b.taskId), ...blocking.map((b) => b.taskId)]);
  const candidates = allTasks.filter((t) => !existingIds.has(t.id));

  return (
    <div className="space-y-2">
      <Label>任务依赖</Label>

      <p className="text-[11px] text-muted-foreground">前置任务（需先完成）</p>
      {blockedBy.length === 0 ? (
        <p className="text-xs text-muted-foreground">无前置任务</p>
      ) : (
        <div className="space-y-1">
          {blockedBy.map((b) => (
            <div key={b.dependencyId} className="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-muted/50">
              <span className={`inline-flex items-center gap-1 rounded px-1.5 py-0.5 text-[10px] ${
                b.done ? 'bg-success-muted text-success' : 'bg-danger-muted text-danger'
              }`}>
                {b.done ? '✓ 已完成' : '⏳ 未完成'}
              </span>
              <span className="min-w-0 flex-1 truncate text-sm">{b.title}</span>
              <button
                className="shrink-0 text-muted-foreground hover:text-destructive"
                title="解除依赖"
                disabled={removeDep.isPending}
                onClick={() => removeDep.mutate(b.dependencyId)}
              >
                <X className="h-3 w-3" />
              </button>
            </div>
          ))}
        </div>
      )}

      {candidates.length > 0 ? (
        <div className="flex items-center gap-2">
          <select
            value={pickId}
            onChange={(e) => setPickId(e.target.value)}
            className="flex h-8 flex-1 rounded-md border border-input bg-background px-2 text-xs shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          >
            <option value="">添加前置任务…</option>
            {candidates.map((t) => (
              <option key={t.id} value={t.id}>{t.title}</option>
            ))}
          </select>
          <Button
            variant="outline"
            size="sm"
            className="h-8 shrink-0 gap-1 text-xs"
            disabled={!pickId || addDep.isPending}
            onClick={async () => {
              try {
                await addDep.mutateAsync(pickId);
                setPickId('');
              } catch (e) {
                alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '添加依赖失败');
              }
            }}
          >
            <Link2 className="h-3 w-3" /> 添加
          </Button>
        </div>
      ) : (
        <p className="text-[11px] text-muted-foreground">项目内没有可添加的其他任务</p>
      )}

      {blocking.length > 0 && (
        <>
          <p className="pt-1 text-[11px] text-muted-foreground">被以下任务依赖</p>
          <div className="space-y-1">
            {blocking.map((b) => (
              <div key={b.dependencyId} className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-muted/50">
                <Link2 className="h-3 w-3 shrink-0 text-muted-foreground" />
                <span className="min-w-0 flex-1 truncate text-xs">{b.title}</span>
                <Badge variant="outline" className="text-[10px]">{b.status || '-'}</Badge>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

// ============================================
// 工时登记区块（编辑弹窗内）
// ============================================
function TaskWorklogSection({ taskId }: { taskId: string }) {
  const { data: res, isLoading } = useWorklogs(taskId);
  const addWorklog = useAddWorklog(taskId);
  const deleteWorklog = useDeleteWorklog(taskId);
  const [hours, setHours] = useState('');
  const [workDate, setWorkDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [note, setNote] = useState('');

  const logs = res?.data?.logs || [];
  const totalHours = res?.data?.totalHours ?? 0;

  const handleAdd = async () => {
    const h = parseFloat(hours);
    if (!h || h <= 0) return;
    try {
      await addWorklog.mutateAsync({ hours: h, workDate: workDate || undefined, note: note.trim() || undefined });
      setHours('');
      setNote('');
    } catch (e) {
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '登记失败');
    }
  };

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <Label>工时登记</Label>
        <span className="text-xs text-muted-foreground">累计 {totalHours}h</span>
      </div>

      <div className="flex items-center gap-2">
        <Input
          type="number"
          step="0.5"
          min="0.5"
          max="24"
          value={hours}
          onChange={(e) => setHours(e.target.value)}
          placeholder="小时"
          className="h-8 w-20 text-xs"
        />
        <Input
          type="date"
          value={workDate}
          onChange={(e) => setWorkDate(e.target.value)}
          className="h-8 w-36 text-xs"
        />
        <Input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="做了什么（可选）"
          className="h-8 flex-1 text-xs"
        />
        <Button
          variant="outline"
          size="sm"
          className="h-8 shrink-0 gap-1 text-xs"
          disabled={!hours || addWorklog.isPending}
          onClick={handleAdd}
        >
          <Timer className="h-3 w-3" /> 登记
        </Button>
      </div>

      {logs.length > 0 && (
        <div className="space-y-1">
          {logs.map((log) => (
            <div key={log.id} className="group flex items-center gap-2 rounded-md px-2 py-1 hover:bg-muted/50">
              <Timer className="h-3 w-3 shrink-0 text-muted-foreground" />
              <span className="shrink-0 text-xs font-medium">{log.hours}h</span>
              <span className="shrink-0 text-xs text-muted-foreground">{log.workDate}</span>
              <span className="min-w-0 flex-1 truncate text-xs text-muted-foreground">
                {log.userName || '未知'}{log.note ? ` · ${log.note}` : ''}
              </span>
              <button
                className="shrink-0 text-muted-foreground opacity-0 transition-opacity hover:text-destructive group-hover:opacity-100"
                title="删除"
                onClick={async () => {
                  try {
                    await deleteWorklog.mutateAsync(log.id);
                  } catch (e) {
                    alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '删除失败');
                  }
                }}
              >
                <X className="h-3 w-3" />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
