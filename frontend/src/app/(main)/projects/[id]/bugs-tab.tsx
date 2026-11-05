'use client';

import { useState, useEffect } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent } from '@/components/ui/card';
import { Separator } from '@/components/ui/separator';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
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
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  DialogFooter,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { useBugs, useCreateBug, useUpdateBug, useDeleteBug, useUpdateBugStatus, useConfirmGuestBug } from '@/lib/hooks/use-bugs';
import { useProjectMembers } from '@/lib/hooks/use-projects';
import { useMyPermissions } from '@/lib/hooks/use-permissions';
import { useAuthStore } from '@/stores/auth-store';
import { bugApi } from '@/lib/api/bug';
import { downloadCsv } from '@/lib/csv';
import {
  Plus,
  Search,
  Bug as BugIcon,
  AlertCircle,
  Filter,
  Pencil,
  Trash2,
  UserRound,
  ShieldCheck,
  Clock,
  UserCheck,
  Image as ImageIcon,
  Download,
  X,
} from 'lucide-react';
import { BugDescriptionField } from './bug-description-field';
import { BugCommentsPanel } from './bug-comments-panel';
import type { Bug, BugStatus, BugSeverity, Priority, ProjectMember } from '@/types';

// ============================================
// 状态配置
// ============================================
const STATUS_CONFIG: Record<BugStatus, { label: string; color: string }> = {
  NEW: { label: '新提出', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' },
  ASSIGNED: { label: '已分配', color: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400' },
  DOING: { label: '解决中', color: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400' },
  RESOLVED: { label: '已解决', color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
  REOPENED: { label: '重新打开', color: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400' },
  CLOSED: { label: '已关闭', color: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400' },
};

/** 状态流转下拉选项（负责人/报告人可切换） */
const STATUS_OPTIONS: { value: BugStatus; label: string }[] = [
  { value: 'NEW', label: '新提出' },
  { value: 'ASSIGNED', label: '已分配' },
  { value: 'DOING', label: '解决中' },
  { value: 'RESOLVED', label: '已解决' },
  { value: 'REOPENED', label: '重新打开' },
  { value: 'CLOSED', label: '已关闭' },
];

// 严重程度配置
const SEVERITY_CONFIG: Record<BugSeverity, { label: string; color: string; icon: string }> = {
  BLOCKER: { label: '阻塞', color: 'text-red-600', icon: '🔴' },
  CRITICAL: { label: '严重', color: 'text-orange-600', icon: '🟠' },
  MAJOR: { label: '重要', color: 'text-amber-600', icon: '🟡' },
  MINOR: { label: '一般', color: 'text-blue-600', icon: '🔵' },
  TRIVIAL: { label: '轻微', color: 'text-slate-500', icon: '⚪' },
};

// 优先级显示
const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  CRITICAL: '紧急',
};

// ============================================
// Bug 标签页
// ============================================
export function BugsTab({ projectId }: { projectId: string }) {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [createOpen, setCreateOpen] = useState(false);
  const [editBug, setEditBug] = useState<Bug | null>(null);
  const [detailBug, setDetailBug] = useState<Bug | null>(null);

  const { data: bugsRes, isLoading } = useBugs(projectId);
  const createBug = useCreateBug(projectId);
  const deleteBug = useDeleteBug(projectId);
  const updateBugStatus = useUpdateBugStatus(projectId);
  const confirmGuestBug = useConfirmGuestBug(projectId);

  /** 确认游客 Bug：成功后同步详情弹窗快照（pending 置 0），列表由 hook 失效刷新 */
  const handleConfirmGuestBug = (bug: Bug) => {
    confirmGuestBug.mutate(bug.id, {
      onSuccess: () => setDetailBug((prev) => (prev && prev.id === bug.id ? { ...prev, pending: 0 } : prev)),
      onError: (e) =>
        alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '确认失败'),
    });
  };

  // 项目成员（用于负责人选择与详情页姓名解析）
  const { data: membersRes } = useProjectMembers(projectId);
  const projectMembers = membersRes?.data || [];
  const memberMap: Record<string, ProjectMember> = {};
  projectMembers.forEach((m) => {
    memberMap[m.userId] = m;
  });

  // 当前用户与 Bug 操作权限：统一问权限接口（bug:create 映射 TESTER/OWNER/MANAGER）
  const { user: currentUser } = useAuthStore();
  const myPerms = useMyPermissions(projectId);
  const canEditBugs = !!myPerms.data?.data?.project?.includes('bug:create');

  const bugs = bugsRes?.data?.records || [];

  // 批量操作（仅 bug:edit 可用）：多选后批量指派/批量优先级
  const queryClient = useQueryClient();
  const [selectedBugIds, setSelectedBugIds] = useState<Set<string>>(new Set());
  const toggleSelectBug = (bugId: string) => {
    setSelectedBugIds((prev) => {
      const next = new Set(prev);
      if (next.has(bugId)) next.delete(bugId);
      else next.add(bugId);
      return next;
    });
  };
  const batchMutate = useMutation({
    mutationFn: async ({ data }: { data: Partial<Bug> }) => {
      await Promise.all(Array.from(selectedBugIds).map((id) => bugApi.update(id, data)));
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
      setSelectedBugIds(new Set());
    },
    onError: (e) =>
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '批量操作失败'),
  });

  // 导出当前筛选结果为 CSV
  const BUG_STATUS_LABELS: Record<string, string> = {
    NEW: '新提出', ASSIGNED: '已指派', DOING: '解决中', RESOLVED: '已解决', REOPENED: '重新打开', CLOSED: '已关闭',
  };
  const SEVERITY_LABELS: Record<string, string> = {
    BLOCKER: '致命', CRITICAL: '严重', MAJOR: '一般', MINOR: '轻微', TRIVIAL: '微小',
  };
  const PRIORITY_LABELS: Record<string, string> = { CRITICAL: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' };
  const handleExport = () => {
    downloadCsv(
      `Bug列表-${new Date().toISOString().slice(0, 10)}`,
      ['标题', '状态', '严重程度', '优先级', '负责人', '报告人', '创建时间'],
      filteredBugs.map((b) => [
        b.title,
        BUG_STATUS_LABELS[b.status] || b.status,
        SEVERITY_LABELS[b.severity] || b.severity,
        PRIORITY_LABELS[b.priority] || b.priority,
        b.assigneeId ? memberMap[b.assigneeId]?.realName || memberMap[b.assigneeId]?.username || '' : '',
        b.reporterType === 'GUEST' ? `游客「${b.guestNickname || ''}」` : b.reporterId ? memberMap[b.reporterId]?.realName || memberMap[b.reporterId]?.username || '' : '',
        b.createdAt ? new Date(b.createdAt).toLocaleString('zh-CN') : '',
      ]),
    );
  };

  // 处理删除 Bug
  const handleDeleteBug = async (bugId: string) => {
    if (!confirm('确定要删除此 Bug 吗？')) return;
    await deleteBug.mutateAsync(bugId);
  };

  // 过滤
  const filteredBugs = bugs.filter((b) => {
    const matchSearch = !search || b.title.toLowerCase().includes(search.toLowerCase());
    const matchStatus = statusFilter === 'ALL' || b.status === statusFilter;
    return matchSearch && matchStatus;
  });

  // 统计
  const statusCounts: Record<string, number> = { ALL: bugs.length };
  bugs.forEach((b) => {
    statusCounts[b.status] = (statusCounts[b.status] || 0) + 1;
  });

  // 创建表单
  const [newBug, setNewBug] = useState<{
    title: string;
    description: string;
    priority: Priority;
    severity: BugSeverity;
    environment: string;
    assigneeId: string;
  }>({
    title: '',
    description: '',
    priority: 'MEDIUM',
    severity: 'MAJOR',
    environment: '',
    assigneeId: '',
  });

  const handleCreate = async () => {
    if (!newBug.title.trim()) return;
    await createBug.mutateAsync({
      ...newBug,
      assigneeId: newBug.assigneeId || undefined,
    });
    setNewBug({ title: '', description: '', priority: 'MEDIUM', severity: 'MAJOR', environment: '', assigneeId: '' });
    setCreateOpen(false);
  };

  return (
    <div className="flex h-full flex-col">
      {/* 工具栏 */}
      <div className="mb-4 flex items-center gap-3">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="搜索 Bug..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
        {/* 导出 CSV（当前筛选结果） */}
        <Button variant="outline" size="sm" className="gap-1 shrink-0" onClick={handleExport} title="导出当前筛选结果">
          <Download className="h-3.5 w-3.5" />
          导出
        </Button>
        {canEditBugs ? (
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger className="inline-flex items-center gap-1.5 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-primary/90">
              <Plus className="h-4 w-4" />
              新建 Bug
            </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>新建 Bug</DialogTitle>
            </DialogHeader>
            <div className="space-y-4">
              <div>
                <Label>标题</Label>
                <Input
                  value={newBug.title}
                  onChange={(e) => setNewBug({ ...newBug, title: e.target.value })}
                  placeholder="输入 Bug 标题"
                />
              </div>
              <BugDescriptionField
                projectId={projectId}
                value={newBug.description}
                onChange={(d) => setNewBug({ ...newBug, description: d })}
              />
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div>
                  <Label>严重程度</Label>
                  <select
                    value={newBug.severity}
                    onChange={(e) => setNewBug({ ...newBug, severity: e.target.value as BugSeverity })}
                    className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <option value="BLOCKER">阻塞</option>
                    <option value="CRITICAL">严重</option>
                    <option value="MAJOR">重要</option>
                    <option value="MINOR">一般</option>
                    <option value="TRIVIAL">轻微</option>
                  </select>
                </div>
                <div>
                  <Label>优先级</Label>
                  <select
                    value={newBug.priority}
                    onChange={(e) => setNewBug({ ...newBug, priority: e.target.value as Priority })}
                    className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  >
                    <option value="LOW">低</option>
                    <option value="MEDIUM">中</option>
                    <option value="HIGH">高</option>
                    <option value="CRITICAL">紧急</option>
                  </select>
                </div>
              </div>
              <div>
                <Label>负责人（指定解决人）</Label>
                <select
                  value={newBug.assigneeId}
                  onChange={(e) => setNewBug({ ...newBug, assigneeId: e.target.value })}
                  className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <option value="">未指派</option>
                  {projectMembers.map((m) => (
                    <option key={m.id} value={m.userId}>
                      {m.realName || m.username}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <Label>环境</Label>
                <Input
                  value={newBug.environment}
                  onChange={(e) => setNewBug({ ...newBug, environment: e.target.value })}
                  placeholder="例如：Chrome 120 / macOS 14 / 生产环境"
                />
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={() => setCreateOpen(false)}>
                  取消
                </Button>
                <Button onClick={handleCreate} disabled={!newBug.title.trim()}>
                  创建
                </Button>
              </div>
            </div>
          </DialogContent>
          </Dialog>
        ) : (
          <span className="flex shrink-0 items-center gap-1.5 rounded-md border border-dashed px-3 py-1.5 text-xs text-muted-foreground">
            <ShieldCheck className="h-3.5 w-3.5" />
            仅测试人员或项目管理员可新建 Bug
          </span>
        )}
      </div>

      {/* 状态过滤标签 */}
      <div className="mb-4 flex gap-1.5 overflow-x-auto">
        {['ALL', 'NEW', 'ASSIGNED', 'DOING', 'RESOLVED', 'REOPENED', 'CLOSED'].map((s) => (
          <button
            key={s}
            onClick={() => setStatusFilter(s)}
            className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
              statusFilter === s
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted text-muted-foreground hover:bg-muted/80'
            }`}
          >
            {s === 'ALL' ? '全部' : STATUS_CONFIG[s as BugStatus]?.label}
            <span className="rounded-full bg-black/10 px-1.5 py-0.5 text-[10px]">
              {statusCounts[s] || 0}
            </span>
          </button>
        ))}
      </div>

      {/* 批量操作条 */}
      {canEditBugs && selectedBugIds.size > 0 && (
        <div className="mb-3 flex flex-wrap items-center gap-2 rounded-lg border bg-primary/5 px-3 py-2">
          <span className="text-xs font-medium">已选 {selectedBugIds.size} 项</span>
          <Select
            value="_noop"
            onValueChange={(v) => {
              if (v && v !== '_noop') batchMutate.mutate({ data: { assigneeId: v } });
            }}
          >
            <SelectTrigger className="h-7 w-32 text-xs">
              <SelectValue>批量指派</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="_none">取消指派</SelectItem>
              {projectMembers.map((m) => (
                <SelectItem key={m.id} value={m.userId}>{m.realName || m.username}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value="_noop"
            onValueChange={(v) => {
              if (v && v !== '_noop') batchMutate.mutate({ data: { priority: v as Priority } });
            }}
          >
            <SelectTrigger className="h-7 w-32 text-xs">
              <SelectValue>批量优先级</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="CRITICAL">紧急</SelectItem>
              <SelectItem value="HIGH">高</SelectItem>
              <SelectItem value="MEDIUM">中</SelectItem>
              <SelectItem value="LOW">低</SelectItem>
            </SelectContent>
          </Select>
          <Button
            variant="ghost"
            size="sm"
            className="ml-auto h-7 gap-1 text-xs"
            disabled={batchMutate.isPending}
            onClick={() => setSelectedBugIds(new Set())}
          >
            <X className="h-3 w-3" /> 取消选择
          </Button>
        </div>
      )}

      {/* Bug 列表 */}
      {isLoading ? (
        <div className="flex flex-1 items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      ) : filteredBugs.length === 0 ? (
        <div className="flex flex-1 flex-col items-center justify-center gap-3 text-muted-foreground">
          <BugIcon className="h-12 w-12 opacity-30" />
          <p className="text-sm">暂无 Bug</p>
        </div>
      ) : (
        <div className="flex-1 space-y-2 overflow-auto pr-1">
          {filteredBugs.map((bug) => (
            <BugCard
              key={bug.id}
              bug={bug}
              memberMap={memberMap}
              canEdit={canEditBugs}
              selectable={canEditBugs}
              selected={selectedBugIds.has(bug.id)}
              onToggleSelect={toggleSelectBug}
              onOpen={setDetailBug}
              onEdit={setEditBug}
              onDelete={handleDeleteBug}
            />
          ))}
        </div>
      )}

      {/* 详情 Dialog：点击卡片查看全部信息 */}
      <BugDetailDialog
        bug={detailBug}
        memberMap={memberMap}
        canEdit={canEditBugs}
        canChangeStatus={
          !!detailBug &&
          (!!currentUser && (detailBug.assigneeId === currentUser.id || detailBug.reporterId === currentUser.id))
        }
        changingStatus={updateBugStatus.isPending}
        confirmingGuest={confirmGuestBug.isPending}
        onConfirmGuest={handleConfirmGuestBug}
        onChangeStatus={(bug, status) =>
          updateBugStatus.mutate(
            { id: bug.id, status },
            {
              // 同步详情弹窗内的状态快照
              onSuccess: (res) => setDetailBug(res.data || { ...bug, status }),
              onError: (e) =>
                alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '状态变更失败'),
            },
          )
        }
        open={!!detailBug}
        onOpenChange={(open) => { if (!open) setDetailBug(null); }}
        onEdit={(bug) => { setDetailBug(null); setEditBug(bug); }}
      />

      {/* 编辑 Bug Dialog */}
      <BugEditDialog
        bug={editBug}
        projectId={projectId}
        projectMembers={projectMembers}
        open={!!editBug}
        onOpenChange={(open) => { if (!open) setEditBug(null); }}
      />
    </div>
  );
}

// ============================================
// Bug 卡片组件（点击打开详情）
// ============================================
function BugCard({
  bug,
  memberMap,
  canEdit,
  selectable,
  selected,
  onToggleSelect,
  onOpen,
  onEdit,
  onDelete,
}: {
  bug: Bug;
  memberMap: Record<string, ProjectMember>;
  canEdit: boolean;
  selectable?: boolean;
  selected?: boolean;
  onToggleSelect?: (bugId: string) => void;
  onOpen: (bug: Bug) => void;
  onEdit: (bug: Bug) => void;
  onDelete: (bugId: string) => void;
}) {
  const statusMeta = STATUS_CONFIG[bug.status] || STATUS_CONFIG.NEW;
  const severityMeta = SEVERITY_CONFIG[bug.severity] || SEVERITY_CONFIG.MINOR;
  const assignee = bug.assigneeId ? memberMap[bug.assigneeId] : undefined;
  const shotCount = (() => {
    try {
      const parsed = typeof bug.screenshots === 'string'
        ? JSON.parse(bug.screenshots || '[]')
        : bug.screenshots;
      return Array.isArray(parsed) ? parsed.filter(Boolean).length : 0;
    } catch {
      return 0;
    }
  })();

  return (
    <Card
      className="group cursor-pointer ring-foreground/20 hover:shadow-md transition-shadow"
      onClick={() => onOpen(bug)}
    >
      <CardContent className="flex items-center gap-4 p-4">
        {/* 批量选择勾选框（阻止冒泡避免打开详情） */}
        {selectable && (
          <input
            type="checkbox"
            checked={!!selected}
            onClick={(e) => e.stopPropagation()}
            onChange={() => onToggleSelect?.(bug.id)}
            className="h-4 w-4 shrink-0 accent-blue-600"
            title="选择用于批量操作"
          />
        )}
        {/* 严重程度图标 */}
        <div className="flex flex-col items-center gap-0.5">
          <span className="text-lg" title={severityMeta.label}>
            {severityMeta.icon}
          </span>
        </div>

        {/* 主体内容 */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <h4 className="truncate text-sm font-medium">{bug.title}</h4>
            {!!bug.pending && (
              <Badge variant="outline" className="shrink-0 border-amber-200 text-[10px] text-amber-600">
                <Clock className="mr-0.5 h-2.5 w-2.5" />
                游客待确认
              </Badge>
            )}
            {bug.priority === 'CRITICAL' && (
              <Badge variant="outline" className="border-red-200 text-[10px] text-red-600">
                紧急
              </Badge>
            )}
          </div>
          <div className="mt-1 flex items-center gap-3 text-xs text-muted-foreground">
            <span className="flex items-center gap-1">
              <AlertCircle className="h-3 w-3" />
              {severityMeta.label}
            </span>
            {bug.reporterType === 'GUEST' && (
              <span className="flex items-center gap-1">
                <UserRound className="h-3 w-3" />
                游客: {bug.guestNickname || '匿名'}
              </span>
            )}
            {shotCount > 0 && (
              <span className="flex items-center gap-1" title={`${shotCount} 张截图`}>
                <ImageIcon className="h-3 w-3" />
                {shotCount}
              </span>
            )}
            {bug.environment && <span>环境: {bug.environment}</span>}
            {assignee && (
              <span className="flex items-center gap-1">
                <UserRound className="h-3 w-3" />
                {assignee.realName || assignee.username}
              </span>
            )}
            <span>创建于 {new Date(bug.createdAt).toLocaleDateString('zh-CN')}</span>
          </div>
        </div>

        {/* 操作按钮（仅测试人员可见） */}
        {canEdit && (
          <div className="flex items-center gap-1 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100">
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7"
              onClick={(e) => { e.stopPropagation(); onEdit(bug); }}
              title="编辑"
            >
              <Pencil className="h-3.5 w-3.5" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-7 w-7 text-red-500 hover:text-red-600"
              onClick={(e) => { e.stopPropagation(); onDelete(bug.id); }}
              title="删除"
            >
              <Trash2 className="h-3.5 w-3.5" />
            </Button>
          </div>
        )}

        {/* 状态 */}
        <Badge
          variant="outline"
          className={`shrink-0 border-0 text-[10px] ${statusMeta.color}`}
        >
          {statusMeta.label}
        </Badge>
      </CardContent>
    </Card>
  );
}

// ============================================
// Bug 详情 Dialog（查看全部信息）
// ============================================
function BugDetailDialog({
  bug,
  memberMap,
  canEdit,
  canChangeStatus,
  changingStatus,
  confirmingGuest,
  onConfirmGuest,
  onChangeStatus,
  open,
  onOpenChange,
  onEdit,
}: {
  bug: Bug | null;
  memberMap: Record<string, ProjectMember>;
  canEdit: boolean;
  canChangeStatus: boolean;
  changingStatus: boolean;
  confirmingGuest: boolean;
  onConfirmGuest: (bug: Bug) => void;
  onChangeStatus: (bug: Bug, status: BugStatus) => void;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onEdit: (bug: Bug) => void;
}) {
  if (!bug) return null;

  const statusMeta = STATUS_CONFIG[bug.status] || STATUS_CONFIG.NEW;
  const severityMeta = SEVERITY_CONFIG[bug.severity] || SEVERITY_CONFIG.MINOR;
  const assignee = bug.assigneeId ? memberMap[bug.assigneeId] : undefined;
  const isGuestReporter = bug.reporterType === 'GUEST';
  const reporter = !isGuestReporter && bug.reporterId ? memberMap[bug.reporterId] : undefined;

  // 后端 screenshots 存的是 JSON 字符串，兼容两种形态
  let screenshots: string[] = [];
  try {
    const parsed = typeof bug.screenshots === 'string'
      ? JSON.parse(bug.screenshots || '[]')
      : bug.screenshots;
    if (Array.isArray(parsed)) screenshots = parsed.filter(Boolean);
  } catch {
    screenshots = [];
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg max-h-[85vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-left">{bug.title}</DialogTitle>
          <div className="flex flex-wrap items-center gap-2 pt-1">
            {canChangeStatus ? (
              <select
                value={bug.status}
                disabled={changingStatus}
                onChange={(e) => onChangeStatus(bug, e.target.value as BugStatus)}
                title="变更 Bug 状态（负责人/创建人）"
                className="h-7 rounded-md border border-input bg-background px-2 text-[11px] shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {STATUS_OPTIONS.map((s) => (
                  <option key={s.value} value={s.value}>{s.label}</option>
                ))}
              </select>
            ) : (
              <Badge variant="outline" className={`border-0 text-[10px] ${statusMeta.color}`}>
                {statusMeta.label}
              </Badge>
            )}
            <span className="flex items-center gap-1 text-xs text-muted-foreground">
              {severityMeta.icon} 严重程度：{severityMeta.label}
            </span>
            <span className="text-xs text-muted-foreground">
              优先级：{PRIORITY_LABELS[bug.priority] || bug.priority}
            </span>
            {canChangeStatus && (
              <span className="text-[10px] text-muted-foreground">
                （负责人/创建人可变更状态）
              </span>
            )}
          </div>
        </DialogHeader>

        <div className="space-y-4">
          {/* 游客待确认提示条 */}
          {!!bug.pending && (
            <div className="flex items-center gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-400">
              <Clock className="h-3.5 w-3.5 shrink-0" />
              <span className="flex-1">该 Bug 由游客提交，确认后才会进入正常处理流程。</span>
              {canEdit && (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 shrink-0 gap-1 border-amber-300 text-xs text-amber-700 hover:bg-amber-100 dark:text-amber-400"
                  disabled={confirmingGuest}
                  onClick={() => onConfirmGuest(bug)}
                >
                  <UserCheck className="h-3 w-3" />
                  {confirmingGuest ? '确认中...' : '确认有效'}
                </Button>
              )}
            </div>
          )}

          {/* 基本信息 */}
          <div className="grid grid-cols-2 gap-3 rounded-lg border bg-muted/30 p-3 text-xs">
            <div>
              <p className="mb-1 text-muted-foreground">负责人</p>
              {assignee ? (
                <div className="flex items-center gap-1.5">
                  <Avatar size="sm">
                    {assignee.avatar && <AvatarImage src={assignee.avatar} />}
                    <AvatarFallback>{(assignee.username || '?').charAt(0).toUpperCase()}</AvatarFallback>
                  </Avatar>
                  <span className="font-medium">{assignee.realName || assignee.username || '未知用户'}</span>
                </div>
              ) : (
                <span className="text-muted-foreground">未指派</span>
              )}
            </div>
            <div>
              <p className="mb-1 text-muted-foreground">报告人</p>
              {isGuestReporter ? (
                <div className="flex items-center gap-1.5">
                  <span className="flex h-6 w-6 items-center justify-center rounded-full bg-amber-500/10 text-[10px] font-medium text-amber-600">
                    客
                  </span>
                  <span className="font-medium">游客「{bug.guestNickname || '匿名'}」</span>
                </div>
              ) : reporter ? (
                <div className="flex items-center gap-1.5">
                  <Avatar size="sm">
                    {reporter.avatar && <AvatarImage src={reporter.avatar} />}
                    <AvatarFallback>{(reporter.username || '?').charAt(0).toUpperCase()}</AvatarFallback>
                  </Avatar>
                  <span className="font-medium">{reporter.realName || reporter.username || '未知用户'}</span>
                </div>
              ) : (
                <span className="font-mono text-[10px] text-muted-foreground">{bug.reporterId}</span>
              )}
            </div>
            <div>
              <p className="mb-1 text-muted-foreground">环境</p>
              <p>{bug.environment || '—'}</p>
            </div>
            <div>
              <p className="mb-1 text-muted-foreground">创建时间</p>
              <p>{new Date(bug.createdAt).toLocaleString('zh-CN')}</p>
            </div>
            <div>
              <p className="mb-1 text-muted-foreground">更新时间</p>
              <p>{new Date(bug.updatedAt).toLocaleString('zh-CN')}</p>
            </div>
          </div>

          {/* 描述（支持内嵌截图：![xx](url) 渲染为图片） */}
          <div>
            <p className="mb-1.5 text-sm font-medium">描述</p>
            <div className="rounded-md border bg-muted/30 p-3 text-sm text-foreground/90">
              {renderDescription(bug.description?.trim() || '暂无描述')}
            </div>
          </div>

          {/* 截图 */}
          {screenshots.length > 0 && (
            <div>
              <p className="mb-1.5 text-sm font-medium">截图</p>
              <div className="grid grid-cols-3 gap-2">
                {screenshots.map((url, i) => (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img
                    key={i}
                    src={url}
                    alt={`截图 ${i + 1}`}
                    className="h-24 w-full rounded-md border object-cover"
                  />
                ))}
              </div>
            </div>
          )}
        </div>

        <Separator />

        {/* 评论（支持 @提及） */}
        <BugCommentsPanel bugId={bug.id} />

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>关闭</Button>
          {canEdit && (
            <Button variant="outline" onClick={() => onEdit(bug)}>
              <Pencil className="mr-1.5 h-3.5 w-3.5" /> 编辑
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

// ============================================
// 编辑 Bug Dialog
// ============================================
function BugEditDialog({
  bug,
  projectId,
  projectMembers,
  open,
  onOpenChange,
}: {
  bug: Bug | null;
  projectId: string;
  projectMembers: ProjectMember[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const updateBug = useUpdateBug(projectId);
  const [form, setForm] = useState<{
    title: string;
    description: string;
    priority: Priority;
    severity: BugSeverity;
    environment: string;
    assigneeId: string;
  }>({
    title: '',
    description: '',
    priority: 'MEDIUM',
    severity: 'MAJOR',
    environment: '',
    assigneeId: '',
  });

  // 当 bug 变化时同步表单数据
  useEffect(() => {
    if (bug) {
      setForm({
        title: bug.title || '',
        description: bug.description || '',
        priority: bug.priority || 'MEDIUM',
        severity: bug.severity || 'MAJOR',
        environment: bug.environment || '',
        assigneeId: bug.assigneeId || '',
      });
    }
  }, [bug]);

  if (!bug) return null;

  const handleSave = async () => {
    if (!form.title.trim()) return;
    await updateBug.mutateAsync({
      id: bug.id,
      data: {
        title: form.title,
        description: form.description || undefined,
        priority: form.priority,
        severity: form.severity,
        environment: form.environment || undefined,
        // 空串表示取消指派（后端会置 NULL）
        assigneeId: form.assigneeId,
      },
    });
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>编辑 Bug</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div>
            <Label>标题</Label>
            <Input
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              placeholder="输入 Bug 标题"
            />
          </div>
          <BugDescriptionField
            projectId={projectId}
            value={form.description}
            onChange={(d) => setForm({ ...form, description: d })}
          />
          <div className="grid grid-cols-2 gap-3">
            <div>
              <Label>严重程度</Label>
              <select
                value={form.severity}
                onChange={(e) => setForm({ ...form, severity: e.target.value as BugSeverity })}
                className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <option value="BLOCKER">阻塞</option>
                <option value="CRITICAL">严重</option>
                <option value="MAJOR">重要</option>
                <option value="MINOR">一般</option>
                <option value="TRIVIAL">轻微</option>
              </select>
            </div>
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
          </div>
          <div>
            <Label>负责人（指定解决人）</Label>
            <select
              value={form.assigneeId}
              onChange={(e) => setForm({ ...form, assigneeId: e.target.value })}
              className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="">未指派</option>
              {projectMembers.map((m) => (
                <option key={m.id} value={m.userId}>
                  {m.realName || m.username}
                </option>
              ))}
            </select>
          </div>
          <div>
            <Label>环境</Label>
            <Input
              value={form.environment}
              onChange={(e) => setForm({ ...form, environment: e.target.value })}
              placeholder="例如：Chrome 120 / macOS 14 / 生产环境"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              取消
            </Button>
            <Button onClick={handleSave} disabled={updateBug.isPending || !form.title.trim()}>
              {updateBug.isPending ? '保存中...' : '保存'}
            </Button>
          </DialogFooter>
        </div>
      </DialogContent>
    </Dialog>
  );
}

// ============================================
// 描述内嵌截图渲染
// ============================================
/** 描述按 ![xx](url) 切分：文本段保留换行，图片段渲染为内嵌图片 */
function renderDescription(text: string) {
  const re = /!\[[^\]]*\]\(([^)\s]+)\)/g;
  const nodes: React.ReactNode[] = [];
  let last = 0;
  let m: RegExpExecArray | null;
  let key = 0;
  while ((m = re.exec(text)) !== null) {
    if (m.index > last) {
      nodes.push(
        <span key={`t${key++}`} className="whitespace-pre-wrap">{text.slice(last, m.index)}</span>
      );
    }
    const url = m[1];
    nodes.push(
      // eslint-disable-next-line @next/next/no-img-element
      <img
        key={`i${key++}`}
        src={url}
        alt="描述截图"
        className="my-1.5 max-h-72 max-w-full cursor-zoom-in rounded-md border"
        onClick={() => window.open(url, '_blank')}
      />
    );
    last = m.index + m[0].length;
  }
  if (last < text.length) {
    nodes.push(
      <span key={`t${key++}`} className="whitespace-pre-wrap">{text.slice(last)}</span>
    );
  }
  return nodes.length > 0 ? nodes : <span className="whitespace-pre-wrap">{text}</span>;
}
