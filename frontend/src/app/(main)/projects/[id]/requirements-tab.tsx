'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Plus,
  Search,
  Filter,
  MoreHorizontal,
  FileText,
  CheckCircle2,
  Trash2,
  Eye,
  X,
  Link2,
  Pencil,
  CircleDot,
  CircleOff,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react';
import {
  useRequirements,
  useRequirement,
  useCreateRequirement,
  useUpdateRequirement,
  useDeleteRequirement,
  useLinkTask,
  useUnlinkTask,
  useRequirementChanges,
} from '@/lib/hooks/use-requirements';
import { useProjectMembers } from '@/lib/hooks/use-projects';
import { useAuthStore } from '@/stores/auth-store';
import { BugDescriptionField } from './bug-description-field';
import { RichDescription } from './rich-description';
import { WalkthroughPanel } from './walkthrough-panel';
import { DesignAssetPanel } from './design-asset-panel';
import { CustomFieldsSection } from './custom-fields-section';
import { useTasks } from '@/lib/hooks/use-tasks';
import { SavedFiltersPanel } from './saved-filters-panel';
import { RequirementScoreQuadrant } from './requirement-score-quadrant';
import {
  EMPTY_RICE,
  RiceScoreBadge,
  RiceScoreFields,
  previewRiceScore,
  riceDraftOf,
  ricePayload,
  type RiceDraft,
} from './requirement-score-fields';
import { queryApi } from '@/lib/api/query';
import type { Requirement, RequirementStatus, Priority } from '@/types';

// 列表分页大小
const PAGE_SIZE = 20;

// ============================================
// 需求标签页组件
// ============================================
export function RequirementsTab({
  projectId,
  initialRequirementId,
}: {
  projectId: string;
  /** 设计稿通知深链：打开后直接展示该需求详情 */
  initialRequirementId?: string;
}) {
  const [searchKeyword, setSearchKeyword] = useState('');
  const [priorityFilter, setPriorityFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  // 排序：默认按创建时间；按分数排时后端走 (project_id, rice_score) 索引
  const [sortBy, setSortBy] = useState<string>('createdAt');
  // 保存视图命中的需求 ID；非空时按 ID 过滤当前页数据
  const [viewIds, setViewIds] = useState<Set<string> | null>(null);
  const [viewLabel, setViewLabel] = useState('');
  const [viewLoading, setViewLoading] = useState(false);
  // 列表 / 四象限切换：象限图只看当前筛选结果，不额外请求
  const [viewMode, setViewMode] = useState<'list' | 'quadrant'>('list');
  // 页码和它所属的筛选条件存一起：条件变了页码自动回 1，
  // 不需要 effect 回填（effect 版会先按旧页码发一次请求，再回第一页发第二次）
  const filterKey = `${searchKeyword}|${priorityFilter}|${statusFilter}|${sortBy}`;
  const [pageState, setPageState] = useState<{ key: string; page: number }>({ key: filterKey, page: 1 });
  const page = pageState.key === filterKey ? pageState.page : 1;
  const setPage = (updater: number | ((p: number) => number)) =>
    setPageState((prev) => {
      const base = prev.key === filterKey ? prev.page : 1;
      return { key: filterKey, page: typeof updater === 'function' ? updater(base) : updater };
    });
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [detailDialogOpen, setDetailDialogOpen] = useState(!!initialRequirementId);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  // 只存 ID，详情从最新列表派生：操作后弹窗能同步刷新，避免展示旧快照
  const [selectedRequirementId, setSelectedRequirementId] = useState<string | null>(
    initialRequirementId ?? null,
  );
  const [editingRequirementId, setEditingRequirementId] = useState<string | null>(null);

  // 获取需求列表
  const { data: requirementsRes, isLoading } = useRequirements(projectId, {
    keyword: searchKeyword || undefined,
    priority: priorityFilter || undefined,
    status: statusFilter || undefined,
    sortBy: sortBy !== 'createdAt' ? sortBy : undefined,
    sortOrder: 'desc',
    page,
    pageSize: PAGE_SIZE,
  });

  const allRequirements = requirementsRes?.data?.records || [];
  const requirements = viewIds
    ? allRequirements.filter((r) => viewIds.has(r.id))
    : allRequirements;
  const total = viewIds ? requirements.length : requirementsRes?.data?.total || 0;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const selectedRequirement = requirements.find((r) => r.id === selectedRequirementId) ?? null;
  const editingRequirement = requirements.find((r) => r.id === editingRequirementId) ?? null;

  const openEditDialog = (requirementId: string) => {
    setDetailDialogOpen(false);
    setEditingRequirementId(requirementId);
    setEditDialogOpen(true);
  };

  // 视图 = 一段结构化表达式；取齐所有命中项后按 ID 过滤，与任务看板同一套做法
  const handleApplyView = async (expression: string) => {
    setViewLoading(true);
    try {
      const pageSize = 200;
      const first = await queryApi.run({
        entity: 'REQUIREMENT',
        expression,
        projectId,
        page: 1,
        pageSize,
      });
      const records = [...(first.data.records || [])];
      const totalPages = Math.max(1, Math.ceil(first.data.total / pageSize));
      for (let p = 2; p <= totalPages; p += 1) {
        const next = await queryApi.run({ entity: 'REQUIREMENT', expression, projectId, page: p, pageSize });
        records.push(...(next.data.records || []));
      }
      setViewIds(new Set(records.map((r) => String((r as { id?: string }).id))));
      setViewLabel(expression);
    } catch (e) {
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '视图执行失败');
    } finally {
      setViewLoading(false);
    }
  };

  return (
    <div className="flex h-full flex-col">
      {/* 工具栏 */}
      <div className="flex flex-col gap-3 border-b px-4 py-3 sm:flex-row sm:px-6">
        {/* 搜索框 */}
        <div className="relative flex-1 sm:max-w-sm">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="搜索需求..."
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            className="pl-9 h-9"
          />
        </div>

        {/* 状态筛选 */}
        <Select value={statusFilter} onValueChange={(v) => { if (v !== null) setStatusFilter(v); }}>
          <SelectTrigger className="h-9 w-full sm:w-32">
            <SelectValue>
              {statusFilter ? getStatusConfig(statusFilter as RequirementStatus).label : '全部状态'}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">全部状态</SelectItem>
            <SelectItem value="OPEN">开放</SelectItem>
            <SelectItem value="COMPLETED">已完成</SelectItem>
            <SelectItem value="CLOSED">已关闭</SelectItem>
          </SelectContent>
        </Select>

        {/* 优先级筛选 */}
        <Select value={priorityFilter} onValueChange={(v) => { if (v !== null) setPriorityFilter(v); }}>
          <SelectTrigger className="h-9 w-full sm:w-32">
            <Filter className="mr-2 h-4 w-4" />
            <SelectValue>
              {priorityFilter ? getPriorityConfig(priorityFilter as Priority).label : '全部优先级'}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="">全部优先级</SelectItem>
            <SelectItem value="CRITICAL">紧急</SelectItem>
            <SelectItem value="HIGH">高</SelectItem>
            <SelectItem value="MEDIUM">中</SelectItem>
            <SelectItem value="LOW">低</SelectItem>
          </SelectContent>
        </Select>

        {/* 排序：分数排序让"先做哪个"有据可依 */}
        <Select value={sortBy} onValueChange={(v) => { if (v !== null) setSortBy(v); }}>
          <SelectTrigger className="h-9 w-full sm:w-36" title="排序方式">
            <SelectValue>
              {sortBy === 'riceScore' ? '按 RICE 分数' : sortBy === 'effort' ? '按工作量' : '按创建时间'}
            </SelectValue>
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="createdAt">按创建时间</SelectItem>
            <SelectItem value="riceScore">按 RICE 分数</SelectItem>
            <SelectItem value="effort">按工作量</SelectItem>
            <SelectItem value="priority">按优先级</SelectItem>
          </SelectContent>
        </Select>

        <SavedFiltersPanel projectId={projectId} entity="REQUIREMENT" onRun={handleApplyView} />

        <div className="flex rounded-md border p-0.5">
          <button
            onClick={() => setViewMode('list')}
            className={`rounded px-2 py-1 text-xs ${viewMode === 'list' ? 'bg-brand text-primary-foreground' : 'text-muted-foreground'}`}
          >
            列表
          </button>
          <button
            onClick={() => setViewMode('quadrant')}
            className={`rounded px-2 py-1 text-xs ${viewMode === 'quadrant' ? 'bg-brand text-primary-foreground' : 'text-muted-foreground'}`}
          >
            四象限
          </button>
        </div>

        {/* 新建按钮 */}
          <Button size="sm" className="w-full sm:w-auto" onClick={() => setCreateDialogOpen(true)}>
          <Plus className="mr-1.5 h-4 w-4" />
          新建需求
        </Button>
      </div>

      {/* 当前视图提示：命中集合按 ID 过滤，清除后回到普通列表 */}
      {viewIds && (
        <div className="flex items-center gap-2 border-b bg-muted/40 px-4 py-1.5 text-xs sm:px-6">
          <Filter className="h-3.5 w-3.5 text-muted-foreground" />
          <span className="text-muted-foreground">视图：</span>
          {viewLoading ? (
            <span className="text-muted-foreground">执行中...</span>
          ) : (
            <code className="truncate text-[11px]">{viewLabel}</code>
          )}
          <span className="ml-auto text-muted-foreground">命中 {requirements.length} 条</span>
          <Button
            variant="ghost"
            size="sm"
            className="h-6 gap-1 text-[11px]"
            onClick={() => { setViewIds(null); setViewLabel(''); }}
          >
            <X className="h-3 w-3" /> 清除
          </Button>
        </div>
      )}

      {/* 需求列表 / 四象限 */}
      <div className="flex-1 overflow-auto">
        {viewMode === 'quadrant' ? (
          <div className="p-4 sm:p-6">
            <RequirementScoreQuadrant requirements={requirements} />
          </div>
        ) : isLoading ? (
          <div className="flex items-center justify-center h-40">
            <div className="text-sm text-muted-foreground">加载中...</div>
          </div>
        ) : requirements.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-40 text-muted-foreground">
            <FileText className="h-10 w-10 mb-2 opacity-50" />
            <div className="text-sm">暂无需求</div>
            <Button variant="link" size="sm" onClick={() => setCreateDialogOpen(true)}>
              创建第一个需求
            </Button>
          </div>
        ) : (
          <div className="divide-y">
            {requirements.map((req: Requirement) => (
              <RequirementItem
                key={req.id}
                requirement={req}
                onView={() => {
                  setSelectedRequirementId(req.id);
                  setDetailDialogOpen(true);
                }}
                onEdit={() => openEditDialog(req.id)}
                projectId={projectId}
              />
            ))}
          </div>
        )}
      </div>

      {/* 分页 */}
      {total > PAGE_SIZE && (
        <div className="flex items-center justify-between border-t px-4 py-2 text-xs text-muted-foreground sm:px-6">
          <span>共 {total} 条需求</span>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              className="h-7 gap-0.5"
              disabled={page <= 1}
              onClick={() => setPage((p) => p - 1)}
            >
              <ChevronLeft className="h-3.5 w-3.5" /> 上一页
            </Button>
            <span>{page} / {totalPages}</span>
            <Button
              variant="outline"
              size="sm"
              className="h-7 gap-0.5"
              disabled={page >= totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              下一页 <ChevronRight className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      )}

      {/* 创建需求对话框 */}
      <CreateRequirementDialog
        open={createDialogOpen}
        onOpenChange={setCreateDialogOpen}
        projectId={projectId}
      />

      {/* 编辑需求对话框 */}
      <EditRequirementDialog
        open={editDialogOpen}
        onOpenChange={setEditDialogOpen}
        requirement={editingRequirement}
        projectId={projectId}
      />

      {/* 需求详情对话框 */}
      <RequirementDetailDialog
        open={detailDialogOpen}
        onOpenChange={setDetailDialogOpen}
        requirement={selectedRequirement}
        requirementId={selectedRequirementId}
        projectId={projectId}
        onEdit={selectedRequirement ? () => openEditDialog(selectedRequirement.id) : undefined}
      />
    </div>
  );
}

// ============================================
// 需求列表项
// ============================================
function RequirementItem({
  requirement,
  onView,
  onEdit,
  projectId,
}: {
  requirement: Requirement;
  onView: () => void;
  onEdit: () => void;
  projectId: string;
}) {
  const deleteRequirement = useDeleteRequirement(projectId);

  const statusConfig = getStatusConfig(requirement.status);
  const priorityConfig = getPriorityConfig(requirement.priority);

  return (
      <div className="flex items-center gap-4 px-4 py-3 hover:bg-muted/50 transition-colors sm:px-6">
      {/* 状态图标 */}
      <div className={`flex h-8 w-8 items-center justify-center rounded-full ${statusConfig.bgColor}`}>
        <statusConfig.icon className={`h-4 w-4 ${statusConfig.color}`} />
      </div>

      {/* 需求信息 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <button
            onClick={onView}
            className="text-sm font-medium hover:underline truncate text-left"
          >
            {requirement.title}
          </button>
          <Badge variant="outline" className={priorityConfig.color}>
            {priorityConfig.label}
          </Badge>
          <RiceScoreBadge score={requirement.riceScore} />
        </div>
        <div className="flex items-center gap-3 mt-1 text-xs text-muted-foreground">
          <span>#{requirement.id.slice(-8)}</span>
          <span>·</span>
          <span>{requirement.requesterName}</span>
          <span>·</span>
          <span>{formatDate(requirement.createdAt)}</span>
        </div>
      </div>

      {/* 操作按钮 */}
      <DropdownMenu>
        <DropdownMenuTrigger
          className="inline-flex h-8 w-8 items-center justify-center rounded-md hover:bg-accent"
        >
          <MoreHorizontal className="h-4 w-4" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onClick={onView}>
            <Eye className="mr-2 h-4 w-4" />
            查看详情
          </DropdownMenuItem>
          <DropdownMenuItem onClick={onEdit}>
            <Pencil className="mr-2 h-4 w-4" />
            编辑
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            className="text-destructive"
            onClick={() => {
              if (confirm('确定要删除此需求吗？')) {
                deleteRequirement.mutate(requirement.id);
              }
            }}
          >
            <Trash2 className="mr-2 h-4 w-4" />
            删除
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}

// ============================================
// 创建需求对话框
// ============================================
function CreateRequirementDialog({
  open,
  onOpenChange,
  projectId,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  projectId: string;
}) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<Priority>('MEDIUM');
  const [estimatedHours, setEstimatedHours] = useState('');
  const [rice, setRice] = useState<RiceDraft>(EMPTY_RICE);

  const createRequirement = useCreateRequirement(projectId);

  const handleSubmit = () => {
    if (!title.trim()) return;
    const hours = parseFloat(estimatedHours);

    createRequirement.mutate(
      {
        title: title.trim(),
        description: description.trim() || undefined,
        priority,
        estimatedHours: !isNaN(hours) ? hours : undefined,
        ...ricePayload(rice),
      },
      {
        onSuccess: () => {
          setTitle('');
          setDescription('');
          setPriority('MEDIUM');
          setEstimatedHours('');
          setRice(EMPTY_RICE);
          onOpenChange(false);
        },
      }
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>新建需求</DialogTitle>
          <DialogDescription>创建一个新的需求，创建后立即生效</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* 标题 */}
          <div className="space-y-1.5">
            <label className="text-sm font-medium">需求标题 *</label>
            <Input
              placeholder="输入需求标题"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          {/* 描述 */}
          <BugDescriptionField
            projectId={projectId}
            value={description}
            onChange={setDescription}
            label="需求描述"
            placeholder="详细描述需求内容、背景、预期效果，可直接粘贴截图…"
          />

          {/* 优先级和工时 */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label className="text-sm font-medium">优先级</label>
              <Select value={priority} onValueChange={(v) => setPriority(v as Priority)}>
                <SelectTrigger>
                  <SelectValue>{getPriorityConfig(priority).label}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CRITICAL">紧急</SelectItem>
                  <SelectItem value="HIGH">高</SelectItem>
                  <SelectItem value="MEDIUM">中</SelectItem>
                  <SelectItem value="LOW">低</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <label className="text-sm font-medium">预估工时（小时）</label>
              <Input
                type="number"
                step="0.5"
                placeholder="0"
                value={estimatedHours}
                onChange={(e) => setEstimatedHours(e.target.value)}
              />
            </div>
          </div>

          <RiceScoreFields idPrefix="create-rice" value={rice} onChange={setRice} />
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={createRequirement.isPending || !title.trim()}>
            {createRequirement.isPending ? '创建中...' : '创建'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function draftOf(r: Requirement | null) {
  return {
    title: r?.title ?? '',
    description: r?.description ?? '',
    priority: (r?.priority ?? 'MEDIUM') as Priority,
    estimatedHours: r?.estimatedHours ? String(r.estimatedHours) : '',
    rice: riceDraftOf(r),
  };
}

// ============================================
// 编辑需求对话框
// ============================================
function EditRequirementDialog({
  open,
  onOpenChange,
  requirement,
  projectId,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  requirement: Requirement | null;
  projectId: string;
}) {
  const updateRequirement = useUpdateRequirement(projectId);

  // 表单初值以「哪个需求 + 是否打开」为 key：换需求或重新打开时在渲染期重置，
  // 列表后台刷新不会打断正在输入的内容。走的是 React 官方的
  // "adjusting state when a prop changes" 模式，不需要 effect 回填。
  const dialogKey = `${requirement?.id ?? ''}|${open}`;
  const [draft, setDraft] = useState<{
    key: string;
    title: string;
    description: string;
    priority: Priority;
    estimatedHours: string;
    rice: RiceDraft;
  }>(() => ({ key: dialogKey, ...draftOf(requirement) }));
  const form = draft.key === dialogKey ? draft : { key: dialogKey, ...draftOf(requirement) };
  const setField = (patch: Partial<Omit<typeof form, 'key'>>) =>
    setDraft({ ...form, ...patch });
  const title = form.title;
  const description = form.description;
  const priority = form.priority;
  const estimatedHours = form.estimatedHours;
  const rice = form.rice;

  if (!requirement) return null;

  const handleSubmit = () => {
    if (!title.trim()) return;
    const hours = parseFloat(estimatedHours);

    updateRequirement.mutate(
      {
        id: requirement.id,
        data: {
          title: title.trim(),
          description: description.trim() || undefined,
          priority,
          estimatedHours: !isNaN(hours) ? hours : undefined,
          ...ricePayload(rice),
        },
      },
      {
        onSuccess: () => onOpenChange(false),
        onError: (e) => {
          alert(
            (e as { response?: { data?: { message?: string } } })?.response?.data?.message ||
              '保存失败'
          );
        },
      }
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>编辑需求</DialogTitle>
          <DialogDescription>
            #{requirement.id.slice(-8)} · 由 {requirement.requesterName} 提出，仅创建人或企业管理员可修改
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* 标题 */}
          <div className="space-y-1.5">
            <label className="text-sm font-medium">需求标题 *</label>
            <Input
              placeholder="输入需求标题"
              value={title}
              onChange={(e) => setField({ title: e.target.value })}
            />
          </div>

          {/* 描述 */}
          <BugDescriptionField
            projectId={projectId}
            value={description}
            onChange={(v) => setField({ description: v })}
            label="需求描述"
            placeholder="详细描述需求内容、背景、预期效果，可直接粘贴截图…"
          />

          {/* 优先级和工时 */}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <label className="text-sm font-medium">优先级</label>
              <Select value={priority} onValueChange={(v) => setField({ priority: v as Priority })}>
                <SelectTrigger>
                  <SelectValue>{getPriorityConfig(priority).label}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CRITICAL">紧急</SelectItem>
                  <SelectItem value="HIGH">高</SelectItem>
                  <SelectItem value="MEDIUM">中</SelectItem>
                  <SelectItem value="LOW">低</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <label className="text-sm font-medium">预估工时（小时）</label>
              <Input
                type="number"
                step="0.5"
                placeholder="0"
                value={estimatedHours}
                onChange={(e) => setField({ estimatedHours: e.target.value })}
              />
            </div>
          </div>

          <RiceScoreFields
            idPrefix="edit-rice"
            value={rice}
            onChange={(next) => setField({ rice: next })}
          />
          <p className="text-[11px] text-muted-foreground">
            当前 RICE 分数：{previewRiceScore(rice) ?? requirement.riceScore ?? '未评分'}
            {previewRiceScore(rice) !== null && ' （保存后生效）'}
          </p>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={updateRequirement.isPending || !title.trim()}>
            {updateRequirement.isPending ? '保存中...' : '保存'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ============================================
// 需求详情对话框
// ============================================
function RequirementDetailDialog({
  open,
  onOpenChange,
  requirement,
  requirementId,
  projectId,
  onEdit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** 列表页内的同一行（首屏即可渲染） */
  requirement: Requirement | null;
  /** 选中的需求 ID：翻页/筛选后列表里可能没有它，此时用详情接口拉取 */
  requirementId?: string | null;
  projectId: string;
  onEdit?: () => void;
}) {
  // 列表里找不到时（翻页后点开、从搜索跳转）回落到详情接口，避免弹窗空白
  const { data: detailRes } = useRequirement(
    open && !requirement ? projectId : null,
    open && !requirement ? requirementId ?? null : null,
  );
  const effectiveRequirement = requirement ?? detailRes?.data ?? null;
  const linkTask = useLinkTask(projectId, effectiveRequirement?.id || '');
  const unlinkTask = useUnlinkTask(projectId, effectiveRequirement?.id || '');
  const updateRequirement = useUpdateRequirement(projectId);
  const [linkTaskId, setLinkTaskId] = useState('');
  const { data: tasksRes } = useTasks(projectId);
  const allTasks = tasksRes?.data?.records || [];
  const { data: membersRes } = useProjectMembers(projectId);
  const currentUser = useAuthStore((s) => s.user);
  // 变更记录（详情打开时才请求）
  const { data: changesRes } = useRequirementChanges(
    open ? projectId : null,
    open ? effectiveRequirement?.id ?? null : null,
  );
  const changes = changesRes?.data || [];

  if (!effectiveRequirement) return null;

  // 状态流转权限与编辑一致：创建人、企业 SUPER_ADMIN/ADMIN、项目 OWNER/MANAGER
  const myProjectRole = membersRes?.data?.find((m) => m.userId === currentUser?.id)?.role;
  const canManageStatus =
    effectiveRequirement.requesterId === currentUser?.id ||
    currentUser?.role === 'SUPER_ADMIN' ||
    currentUser?.role === 'ADMIN' ||
    myProjectRole === 'OWNER' ||
    myProjectRole === 'MANAGER';

  const handleStatusChange = (status: RequirementStatus) => {
    if (status === effectiveRequirement.status) return;
    updateRequirement.mutate(
      { id: effectiveRequirement.id, data: { status } },
      {
        onError: (e) => {
          alert(
            (e as { response?: { data?: { message?: string } } })?.response?.data?.message ||
              '状态更新失败'
          );
        },
      }
    );
  };

  const statusConfig = getStatusConfig(effectiveRequirement.status);
  const priorityConfig = getPriorityConfig(effectiveRequirement.priority);
  const linkedIds = new Set((effectiveRequirement.linkedTasks || []).map((t) => t.taskId));
  const linkableTasks = allTasks.filter((t) => !linkedIds.has(t.id));

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <div className="flex items-center gap-3">
            <div className={`flex h-10 w-10 items-center justify-center rounded-full ${statusConfig.bgColor}`}>
              <statusConfig.icon className={`h-5 w-5 ${statusConfig.color}`} />
            </div>
            <div>
              <DialogTitle>{effectiveRequirement.title}</DialogTitle>
              <DialogDescription className="mt-1">
                #{effectiveRequirement.id.slice(-8)} · {effectiveRequirement.requesterName} · {formatDate(effectiveRequirement.createdAt)}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* 状态和优先级 */}
          <div className="flex items-center gap-3">
            {canManageStatus ? (
              <Select
                value={effectiveRequirement.status}
                onValueChange={(v) => { if (v) handleStatusChange(v as RequirementStatus); }}
              >
                <SelectTrigger className="h-7 w-24 gap-1 text-xs">
                  <SelectValue>{statusConfig.label}</SelectValue>
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="OPEN">开放</SelectItem>
                  <SelectItem value="COMPLETED">已完成</SelectItem>
                  <SelectItem value="CLOSED">已关闭</SelectItem>
                </SelectContent>
              </Select>
            ) : (
              <Badge className={statusConfig.color}>{statusConfig.label}</Badge>
            )}
            <Badge variant="outline" className={priorityConfig.color}>
              {priorityConfig.label}
            </Badge>
            {effectiveRequirement.estimatedHours && (
              <span className="text-sm text-muted-foreground">
                预估 {effectiveRequirement.estimatedHours} 小时
              </span>
            )}
            {onEdit && (
              <Button
                variant="ghost"
                size="sm"
                className="ml-auto h-7 gap-1 px-2 text-xs text-muted-foreground"
                onClick={onEdit}
              >
                <Pencil className="h-3 w-3" /> 编辑
              </Button>
            )}
          </div>

          {/* 描述 */}
          <div className="space-y-2">
            <h4 className="text-sm font-medium">需求描述</h4>
            <RichDescription text={effectiveRequirement.description?.trim() || ''} />
          </div>

          {/* 关联任务 */}
          <div className="space-y-2">
            <h4 className="text-sm font-medium">关联任务</h4>

            {(effectiveRequirement.linkedTasks || []).length > 0 ? (
              <div className="space-y-1">
                {effectiveRequirement.linkedTasks?.map((task) => (
                  <div key={task.taskId} className="flex items-center gap-2 p-2 rounded-md bg-muted/30">
                    <CheckCircle2 className="h-4 w-4 text-muted-foreground" />
                    <span className="text-sm flex-1">{task.taskTitle}</span>
                    {task.assigneeName && (
                      <span className="text-xs text-muted-foreground">{task.assigneeName}</span>
                    )}
                    <button
                      className="shrink-0 text-muted-foreground hover:text-destructive"
                      title="解除关联"
                      disabled={unlinkTask.isPending}
                      onClick={async () => {
                        try {
                          await unlinkTask.mutateAsync(task.taskId);
                        } catch (e) {
                          alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '解除关联失败');
                        }
                      }}
                    >
                      <X className="h-3.5 w-3.5" />
                    </button>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-muted-foreground">还没有关联任务</p>
            )}

            {/* 关联新任务 */}
            {linkableTasks.length > 0 ? (
              <div className="flex items-center gap-2">
                <select
                  value={linkTaskId}
                  onChange={(e) => setLinkTaskId(e.target.value)}
                  className="flex h-8 flex-1 rounded-md border border-input bg-background px-2 text-xs shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                >
                  <option value="">选择要关联的任务…</option>
                  {linkableTasks.map((t) => (
                    <option key={t.id} value={t.id}>{t.title}</option>
                  ))}
                </select>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-8 shrink-0 gap-1 text-xs"
                  disabled={!linkTaskId || linkTask.isPending}
                  onClick={async () => {
                    try {
                      await linkTask.mutateAsync(linkTaskId);
                      setLinkTaskId('');
                    } catch (e) {
                      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '关联失败');
                    }
                  }}
                >
                  <Link2 className="h-3 w-3" /> 关联
                </Button>
              </div>
            ) : (
              <p className="text-[11px] text-muted-foreground">项目中的任务都已关联（或暂无任务），可到任务页新建</p>
            )}
          </div>
          {/* 扩展字段（项目自定义） */}
          <CustomFieldsSection
            projectId={projectId}
            issueType="REQUIREMENT"
            issueId={effectiveRequirement.id}
            editable={canManageStatus}
          />

          {/* 设计稿（评审状态会影响"开发就绪"，只让可管理需求的人操作） */}
          <DesignAssetPanel
            requirementId={effectiveRequirement.id}
            projectId={effectiveRequirement.projectId || projectId}
            canManage={canManageStatus}
          />

          {/* UI 走查（只对参与需求管理的人可编辑） */}
          <WalkthroughPanel requirementId={effectiveRequirement.id} canManage={canManageStatus} />

          {/* 变更记录 */}
          <details className="rounded-md border">
            <summary className="cursor-pointer px-3 py-2 text-sm font-medium">
              变更记录{changes.length > 0 && `（${changes.length}）`}
            </summary>
            <div className="max-h-48 space-y-1 overflow-y-auto border-t px-3 py-2">
              {changes.length === 0 ? (
                <p className="text-xs text-muted-foreground">还没有变更记录</p>
              ) : (
                changes.map((c) => (
                  <div key={c.id} className="flex items-start gap-2 text-xs">
                    <span className="mt-0.5 shrink-0 rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
                      {FIELD_LABELS[c.field] || c.field}
                    </span>
                    <span className="min-w-0 flex-1 break-all text-muted-foreground">
                      {c.oldValue != null && <span className="line-through opacity-70">{c.oldValue}</span>}
                      {c.oldValue != null && ' → '}
                      <span className="text-foreground">{c.newValue}</span>
                    </span>
                    <span className="shrink-0 text-[10px] text-muted-foreground">
                      {c.editorName || '未知'} · {formatDate(c.createdAt)}
                    </span>
                  </div>
                ))
              )}
            </div>
          </details>
        </div>
      </DialogContent>
    </Dialog>
  );
}

// ============================================
// 辅助函数
// ============================================
// 变更记录字段中文名
const FIELD_LABELS: Record<string, string> = {
  title: '标题',
  description: '描述',
  priority: '优先级',
  status: '状态',
  estimatedHours: '预估工时',
  source: '来源',
  category: '分类',
};

function getStatusConfig(status: RequirementStatus) {
  switch (status) {
    case 'OPEN':
      return {
        icon: CircleDot,
        label: '开放',
        color: 'text-info',
        bgColor: 'bg-info-muted',
      };
    case 'COMPLETED':
      return {
        icon: CheckCircle2,
        label: '已完成',
        color: 'text-success',
        bgColor: 'bg-success-muted',
      };
    case 'CLOSED':
      return {
        icon: CircleOff,
        label: '已关闭',
        color: 'text-ink-tertiary',
        bgColor: 'bg-surface-2',
      };
    default:
      return {
        icon: FileText,
        label: status,
        color: 'text-muted-foreground',
        bgColor: 'bg-muted',
      };
  }
}

function getPriorityConfig(priority: Priority) {
  switch (priority) {
    case 'CRITICAL':
      return { label: '紧急', color: 'text-danger border-danger/30 bg-danger-muted' };
    case 'HIGH':
      return { label: '高', color: 'text-orange-600 border-orange-200 bg-orange-50' };
    case 'MEDIUM':
      return { label: '中', color: 'text-info border-info/30 bg-info-muted' };
    case 'LOW':
      return { label: '低', color: 'text-ink-subtle border-hairline bg-surface-2' };
    default:
      return { label: priority, color: '' };
  }
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));

  if (days === 0) return '今天';
  if (days === 1) return '昨天';
  if (days < 7) return `${days}天前`;
  return date.toLocaleDateString('zh-CN');
}
