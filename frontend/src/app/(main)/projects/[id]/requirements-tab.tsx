'use client';

import { useEffect, useState } from 'react';
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
import { useTasks } from '@/lib/hooks/use-tasks';
import type { Requirement, RequirementStatus, Priority } from '@/types';

// 列表分页大小
const PAGE_SIZE = 20;

// ============================================
// 需求标签页组件
// ============================================
export function RequirementsTab({ projectId }: { projectId: string }) {
  const [searchKeyword, setSearchKeyword] = useState('');
  const [priorityFilter, setPriorityFilter] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [page, setPage] = useState(1);
  const [createDialogOpen, setCreateDialogOpen] = useState(false);
  const [detailDialogOpen, setDetailDialogOpen] = useState(false);
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  // 只存 ID，详情从最新列表派生：操作后弹窗能同步刷新，避免展示旧快照
  const [selectedRequirementId, setSelectedRequirementId] = useState<string | null>(null);
  const [editingRequirementId, setEditingRequirementId] = useState<string | null>(null);

  // 筛选条件变化时回到第一页
  useEffect(() => {
    setPage(1);
  }, [searchKeyword, priorityFilter, statusFilter]);

  // 获取需求列表
  const { data: requirementsRes, isLoading } = useRequirements(projectId, {
    keyword: searchKeyword || undefined,
    priority: priorityFilter || undefined,
    status: statusFilter || undefined,
    page,
    pageSize: PAGE_SIZE,
  });

  const requirements = requirementsRes?.data?.records || [];
  const total = requirementsRes?.data?.total || 0;
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const selectedRequirement = requirements.find((r) => r.id === selectedRequirementId) ?? null;
  const editingRequirement = requirements.find((r) => r.id === editingRequirementId) ?? null;

  const openEditDialog = (requirementId: string) => {
    setDetailDialogOpen(false);
    setEditingRequirementId(requirementId);
    setEditDialogOpen(true);
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

        {/* 新建按钮 */}
          <Button size="sm" className="w-full sm:w-auto" onClick={() => setCreateDialogOpen(true)}>
          <Plus className="mr-1.5 h-4 w-4" />
          新建需求
        </Button>
      </div>

      {/* 需求列表 */}
      <div className="flex-1 overflow-auto">
        {isLoading ? (
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
      },
      {
        onSuccess: () => {
          setTitle('');
          setDescription('');
          setPriority('MEDIUM');
          setEstimatedHours('');
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
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<Priority>('MEDIUM');
  const [estimatedHours, setEstimatedHours] = useState('');

  const updateRequirement = useUpdateRequirement(projectId);

  // 打开时用当前需求回填（依赖 id，避免列表后台刷新打断编辑）
  useEffect(() => {
    if (open && requirement) {
      setTitle(requirement.title);
      setDescription(requirement.description || '');
      setPriority(requirement.priority || 'MEDIUM');
      setEstimatedHours(requirement.estimatedHours ? String(requirement.estimatedHours) : '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, requirement?.id]);

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
  projectId,
  onEdit,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  requirement: Requirement | null;
  projectId: string;
  onEdit?: () => void;
}) {
  const linkTask = useLinkTask(projectId, requirement?.id || '');
  const unlinkTask = useUnlinkTask(projectId, requirement?.id || '');
  const updateRequirement = useUpdateRequirement(projectId);
  const [linkTaskId, setLinkTaskId] = useState('');
  const { data: tasksRes } = useTasks(projectId);
  const allTasks = tasksRes?.data?.records || [];
  const { data: membersRes } = useProjectMembers(projectId);
  const currentUser = useAuthStore((s) => s.user);
  // 变更记录（详情打开时才请求）
  const { data: changesRes } = useRequirementChanges(
    open ? projectId : null,
    open ? requirement?.id ?? null : null,
  );
  const changes = changesRes?.data || [];

  if (!requirement) return null;

  // 状态流转权限与编辑一致：创建人、企业 SUPER_ADMIN/ADMIN、项目 OWNER/MANAGER
  const myProjectRole = membersRes?.data?.find((m) => m.userId === currentUser?.id)?.role;
  const canManageStatus =
    requirement.requesterId === currentUser?.id ||
    currentUser?.role === 'SUPER_ADMIN' ||
    currentUser?.role === 'ADMIN' ||
    myProjectRole === 'OWNER' ||
    myProjectRole === 'MANAGER';

  const handleStatusChange = (status: RequirementStatus) => {
    if (status === requirement.status) return;
    updateRequirement.mutate(
      { id: requirement.id, data: { status } },
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

  const statusConfig = getStatusConfig(requirement.status);
  const priorityConfig = getPriorityConfig(requirement.priority);
  const linkedIds = new Set((requirement.linkedTasks || []).map((t) => t.taskId));
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
              <DialogTitle>{requirement.title}</DialogTitle>
              <DialogDescription className="mt-1">
                #{requirement.id.slice(-8)} · {requirement.requesterName} · {formatDate(requirement.createdAt)}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="space-y-4 py-2">
          {/* 状态和优先级 */}
          <div className="flex items-center gap-3">
            {canManageStatus ? (
              <Select
                value={requirement.status}
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
            {requirement.estimatedHours && (
              <span className="text-sm text-muted-foreground">
                预估 {requirement.estimatedHours} 小时
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
            <RichDescription text={requirement.description?.trim() || ''} />
          </div>

          {/* 关联任务 */}
          <div className="space-y-2">
            <h4 className="text-sm font-medium">关联任务</h4>

            {(requirement.linkedTasks || []).length > 0 ? (
              <div className="space-y-1">
                {requirement.linkedTasks?.map((task) => (
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
        color: 'text-blue-600',
        bgColor: 'bg-blue-100',
      };
    case 'COMPLETED':
      return {
        icon: CheckCircle2,
        label: '已完成',
        color: 'text-green-600',
        bgColor: 'bg-green-100',
      };
    case 'CLOSED':
      return {
        icon: CircleOff,
        label: '已关闭',
        color: 'text-gray-500',
        bgColor: 'bg-gray-100',
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
      return { label: '紧急', color: 'text-red-600 border-red-200 bg-red-50' };
    case 'HIGH':
      return { label: '高', color: 'text-orange-600 border-orange-200 bg-orange-50' };
    case 'MEDIUM':
      return { label: '中', color: 'text-blue-600 border-blue-200 bg-blue-50' };
    case 'LOW':
      return { label: '低', color: 'text-gray-600 border-gray-200 bg-gray-50' };
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
