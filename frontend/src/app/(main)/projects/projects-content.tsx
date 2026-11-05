  'use client';

import { useState } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
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
  Users,
  MoreHorizontal,
  Filter,
  Grid3X3,
  List,
  FolderKanban,
  Loader2,
  Pencil,
  Archive,
  ArchiveRestore,
  Trash2,
} from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import { useProjects, useCreateProject, useDeleteProject } from '@/lib/hooks/use-projects';
import { projectApi } from '@/lib/api/project';
import type { Project } from '@/types';

const statusColors = {
  ACTIVE: 'bg-green-500/10 text-green-600',
  PLANNING: 'bg-amber-500/10 text-amber-600',
  ARCHIVED: 'bg-gray-500/10 text-gray-500',
};

const statusLabels = {
  ACTIVE: '进行中',
  PLANNING: '规划中',
  ARCHIVED: '已归档',
};

// 格式化时间为相对时间
function formatRelativeTime(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  const diffHour = Math.floor(diffMs / 3600000);
  const diffDay = Math.floor(diffMs / 86400000);

  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin} 分钟前`;
  if (diffHour < 24) return `${diffHour} 小时前`;
  if (diffDay < 30) return `${diffDay} 天前`;
  return date.toLocaleDateString('zh-CN');
}

// ============================================
// 项目中心内容
// ============================================
export default function ProjectsContent() {
  const router = useRouter();
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [searchQuery, setSearchQuery] = useState('');

  // 创建/编辑弹窗状态
  const [formDialogOpen, setFormDialogOpen] = useState(false);
  const [editingProject, setEditingProject] = useState<Project | null>(null);
  const [formName, setFormName] = useState('');
  const [formKey, setFormKey] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formError, setFormError] = useState('');

  // 删除确认弹窗
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const [deletingProject, setDeletingProject] = useState<Project | null>(null);

  // Mutation hooks
  const queryClient = useQueryClient();
  const createProject = useCreateProject();
  const deleteProject = useDeleteProject();

  // 刷新项目列表
  const refreshProjects = () => {
    queryClient.invalidateQueries({ queryKey: ['projects'] });
  };

  // 打开创建弹窗
  const openCreateDialog = () => {
    setEditingProject(null);
    setFormName('');
    setFormKey('');
    setFormDesc('');
    setFormDialogOpen(true);
  };

  // 打开编辑弹窗
  const openEditDialog = (project: Project) => {
    setEditingProject(project);
    setFormName(project.name);
    setFormKey(project.projectKey);
    setFormDesc(project.description || '');
    setFormDialogOpen(true);
  };

  // 保存（创建或更新）
  const handleSaveForm = async () => {
    if (!formName.trim() || !formKey.trim()) return;
    const payload = { name: formName.trim(), projectKey: formKey.trim(), description: formDesc.trim() };
    setFormError('');
    try {
      if (editingProject) {
        // 编辑模式 — 直接调用 API
        await projectApi.update(editingProject.id, payload);
      } else {
        // 创建模式 — 使用 mutation
        await createProject.mutateAsync(payload);
      }
      refreshProjects();
      setFormDialogOpen(false);
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setFormError(msg || '保存失败，请重试');
    }
  };

  // 打开删除确认
  const openDeleteConfirm = (project: Project) => {
    setDeletingProject(project);
    setDeleteConfirmOpen(true);
  };

  // 确认删除
  const handleDelete = async () => {
    if (!deletingProject) return;
    try {
      await deleteProject.mutateAsync(deletingProject.id);
      refreshProjects();
      setDeleteConfirmOpen(false);
    } catch {
      // 请求失败时不关闭弹窗
    }
  };

  // 归档 / 恢复
  const handleToggleArchive = async (project: Project) => {
    const newStatus = project.status === 'ARCHIVED' ? 'ACTIVE' : 'ARCHIVED';
    try {
      await projectApi.update(project.id, { status: newStatus });
      refreshProjects();
    } catch {
      // 静默处理
    }
  };

  // 从后端 API 获取项目列表
  const { data: projectsRes, isLoading } = useProjects({ page: 1, pageSize: 50 });
  const projects = projectsRes?.data?.records || [];

  const filteredProjects = projects.filter((p) =>
    p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    p.description?.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {/* 页面头部 */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">项目中心</h1>
          <p className="text-sm text-muted-foreground">管理和浏览所有项目</p>
        </div>
        <Button className="gap-2" onClick={openCreateDialog}>
          <Plus className="h-4 w-4" />
          创建项目
        </Button>
      </div>

      {/* 工具栏 */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="搜索项目..."
            className="pl-9"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" className="gap-1">
            <Filter className="h-3 w-3" /> 筛选
          </Button>
          <div className="flex rounded-lg border border-border">
            <Button
              variant={viewMode === 'grid' ? 'secondary' : 'ghost'}
              size="icon"
              className="h-8 w-8 rounded-r-none"
              onClick={() => setViewMode('grid')}
            >
              <Grid3X3 className="h-3.5 w-3.5" />
            </Button>
            <Button
              variant={viewMode === 'list' ? 'secondary' : 'ghost'}
              size="icon"
              className="h-8 w-8 rounded-l-none"
              onClick={() => setViewMode('list')}
            >
              <List className="h-3.5 w-3.5" />
            </Button>
          </div>
        </div>
      </div>

      {/* 加载中 */}
      {isLoading ? (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          <span className="ml-2 text-sm text-muted-foreground">加载项目中...</span>
        </div>
      ) : filteredProjects.length === 0 ? (
        /* 空状态 */
        <div className="flex flex-col items-center justify-center py-20 text-muted-foreground">
          <FolderKanban className="mb-4 h-12 w-12 opacity-30" />
          <p className="text-sm">{searchQuery ? '没有匹配的项目' : '暂无项目'}</p>
        </div>
      ) : viewMode === 'grid' ? (
        /* 网格视图 */
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {filteredProjects.map((project) => (
            <Card
              key={project.id}
              className="group cursor-pointer transition-devflow hover:shadow-md"
              onClick={() => router.push(`/projects/${project.id}`)}
            >
              <CardHeader className="pb-3">
                <div className="flex items-start justify-between">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand/10 text-sm font-bold text-brand">
                      {project.projectKey}
                    </div>
                    <div>
                      <CardTitle className="text-base">{project.name}</CardTitle>
                      <CardDescription className="line-clamp-1">{project.description}</CardDescription>
                    </div>
                  </div>
                  <div
                    onClick={(e) => e.stopPropagation()}
                    onMouseDown={(e) => e.stopPropagation()}
                  >
                    <DropdownMenu>
                      <DropdownMenuTrigger
                        className="inline-flex h-7 w-7 items-center justify-center rounded-md opacity-0 transition-opacity hover:bg-accent group-hover:opacity-100"
                      >
                        <MoreHorizontal className="h-4 w-4" />
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="end">
                        <DropdownMenuItem onClick={() => openEditDialog(project)}>
                          <Pencil className="mr-1.5 h-3.5 w-3.5" /> 编辑
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleToggleArchive(project)}>
                          {project.status === 'ARCHIVED' ? (
                            <><ArchiveRestore className="mr-1.5 h-3.5 w-3.5" /> 恢复</>
                          ) : (
                            <><Archive className="mr-1.5 h-3.5 w-3.5" /> 归档</>
                          )}
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem variant="destructive" onClick={() => openDeleteConfirm(project)}>
                          <Trash2 className="mr-1.5 h-3.5 w-3.5" /> 删除
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3 text-xs text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <Users className="h-3 w-3" /> {project.memberCount || 0}
                    </span>
                  </div>
                  <Badge variant="secondary" className={statusColors[project.status]}>
                    {statusLabels[project.status]}
                  </Badge>
                </div>
                <p className="mt-2 text-xs text-muted-foreground">更新于 {formatRelativeTime(project.updatedAt)}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        /* 列表视图 */
        <Card>
          <CardContent className="p-0">
            <div className="divide-y">
              {filteredProjects.map((project) => (
                <div
                  key={project.id}
                  className="flex items-center gap-4 p-4 transition-devflow hover:bg-muted/50 cursor-pointer"
                  onClick={() => router.push(`/projects/${project.id}`)}
                >
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-brand/10 text-xs font-bold text-brand">
                    {project.projectKey}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-medium truncate">{project.name}</p>
                    <p className="text-xs text-muted-foreground truncate">{project.description}</p>
                  </div>
                  <Badge variant="secondary" className={statusColors[project.status]}>
                    {statusLabels[project.status]}
                  </Badge>
                  <span className="text-xs text-muted-foreground">{project.memberCount || 0} 成员</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* ============================================ */}
      {/* 创建 / 编辑项目弹窗 */}
      {/* ============================================ */}
      <Dialog open={formDialogOpen} onOpenChange={setFormDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>{editingProject ? '编辑项目' : '创建项目'}</DialogTitle>
            <DialogDescription>
              {editingProject ? '修改项目的基本信息' : '填写新项目的基本信息'}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="form-name">项目名称</Label>
              <Input
                id="form-name"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                placeholder="请输入项目名称"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="form-key">项目标识</Label>
              <Input
                id="form-key"
                value={formKey}
                onChange={(e) => setFormKey(e.target.value.toUpperCase())}
                placeholder="例如 DF（2-10 个字符）"
                maxLength={10}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="form-desc">项目描述</Label>
              <textarea
                id="form-desc"
                value={formDesc}
                onChange={(e) => setFormDesc(e.target.value)}
                placeholder="简要描述项目用途..."
                rows={3}
                className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
              />
            </div>
            {formError && (
              <p className="text-xs text-destructive">{formError}</p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFormDialogOpen(false)}>取消</Button>
            <Button
              onClick={handleSaveForm}
              disabled={createProject.isPending || !formName.trim() || !formKey.trim()}
            >
              {createProject.isPending ? '保存中...' : editingProject ? '保存修改' : '创建'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ============================================ */}
      {/* 删除确认弹窗 */}
      {/* ============================================ */}
      <Dialog open={deleteConfirmOpen} onOpenChange={setDeleteConfirmOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>确认删除</DialogTitle>
            <DialogDescription>
              确定要删除项目「{deletingProject?.name}」吗？此操作不可恢复，项目下的所有任务和 Bug 将一并删除。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteConfirmOpen(false)}>取消</Button>
            <Button
              variant="destructive"
              onClick={handleDelete}
              disabled={deleteProject.isPending}
            >
              {deleteProject.isPending ? '删除中...' : '确认删除'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
