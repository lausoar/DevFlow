'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
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
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { enterpriseApi } from '@/lib/api/enterprise';
import { projectApi } from '@/lib/api/project';
import { useMyPermissions } from '@/lib/hooks/use-permissions';
import { useAuthStore } from '@/stores/auth-store';
import { AutomationRulesDialog } from './automation-rules-dialog';
import { RecycleBinDialog } from './recycle-bin-dialog';
import {
  ArrowLeft,
  Zap,
  Undo2,
  LayoutDashboard,
  ListTodo,
  Bug,
  Settings,
  MoreHorizontal,
  Archive,
  ArchiveRestore,
  Trash2,
  UserPlus,
  X,
  Crown,
  Shield,
  FlaskConical,
  User as UserIcon,
  FileText,
  Link2,
  Search,
  Copy,
  Check,
  Users,
  MessageSquarePlus,
  Globe,
} from 'lucide-react';
import Link from 'next/link';
import {
  useProject,
  useUpdateProject,
  useDeleteProject,
  useProjectMembers,
  useAddProjectMember,
  useUpdateMemberRole,
  useRemoveProjectMember,
  useProjectInviteCode,
} from '@/lib/hooks/use-projects';
import type { ProjectMember, ProjectMemberRole } from '@/types';
import { DashboardTab } from './dashboard-tab';
import { TasksTab } from './tasks-tab';
import { BugsTab } from './bugs-tab';
import { RequirementsTab } from './requirements-tab';
import { FeedbackTab } from './feedback-tab';
import { TestCasesTab } from './test-cases-tab';
import { GuestLinksPanel } from './guest-links-panel';

// ============================================
// 项目详情内容（客户端组件）
// ============================================
export default function ProjectDetailContent({
  projectId,
  initialTab,
}: {
  projectId: string;
  /** 从 URL ?tab= 进入时直达指定标签页（全局搜索跳转用） */
  initialTab?: string;
}) {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState(initialTab || 'dashboard');
  const { data: projectRes, isLoading } = useProject(projectId);
  const project = projectRes?.data;

  // 自动化规则弹窗
  const [automationOpen, setAutomationOpen] = useState(false);
  // 回收站弹窗
  const [recycleBinOpen, setRecycleBinOpen] = useState(false);
  // 设置弹窗状态
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [memberDialogOpen, setMemberDialogOpen] = useState(false);
  const [inviteDialogOpen, setInviteDialogOpen] = useState(false);
  const [guestDialogOpen, setGuestDialogOpen] = useState(false);
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);

  // 表单字段
  const [formName, setFormName] = useState('');
  const [formKey, setFormKey] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formGitUrl, setFormGitUrl] = useState('');

  // Mutation hooks
  const updateProject = useUpdateProject(projectId);
  const deleteProject = useDeleteProject();

  // 成员管理 hooks
  const { data: membersRes } = useProjectMembers(projectId);
  const members = membersRes?.data || [];
  const addMember = useAddProjectMember(projectId);
  const removeMember = useRemoveProjectMember(projectId);

  // 企业成员列表（用于添加成员时选择）
  const [showAddMember, setShowAddMember] = useState(false);
  const [memberSearch, setMemberSearch] = useState('');
  const enterpriseUsers = useQuery({
    queryKey: ['enterprise-members', project?.enterpriseId],
    queryFn: () => enterpriseApi.getMembers(project!.enterpriseId),
    enabled: !!project?.enterpriseId && showAddMember,
  });

  // 项目邀请链接（打开成员弹窗时才请求）
  const inviteCodeQuery = useProjectInviteCode(projectId, memberDialogOpen || inviteDialogOpen);
  const inviteLink =
    inviteCodeQuery.data?.data && typeof window !== 'undefined'
      ? `${window.location.origin}/projects/join?code=${inviteCodeQuery.data.data}`
      : '';
  const [linkCopied, setLinkCopied] = useState(false);
  const handleCopyInviteLink = async () => {
    if (!inviteLink) return;
    try {
      await navigator.clipboard.writeText(inviteLink);
    } catch {
      // clipboard API 不可用时降级为选中文本
      const input = document.getElementById('project-invite-link') as HTMLInputElement | null;
      input?.select();
      document.execCommand('copy');
    }
    setLinkCopied(true);
    setTimeout(() => setLinkCopied(false), 2000);
  };

  // 计算可添加的用户（排除已是成员的用户，再按关键字过滤）
  const memberUserIds = new Set(members.map((m) => m.userId));
  const keyword = memberSearch.trim().toLowerCase();
  const availableUsers = (enterpriseUsers.data?.data || [])
    .filter((u) => !memberUserIds.has(u.id))
    .filter(
      (u) =>
        !keyword ||
        u.username.toLowerCase().includes(keyword) ||
        (u.email || '').toLowerCase().includes(keyword),
    );

  // 角色显示配置
  const roleConfig: Record<ProjectMemberRole, { label: string; icon: React.ElementType; color: string }> = {
    OWNER:   { label: '负责人',   icon: Crown,        color: 'text-amber-500 bg-amber-500/10' },
    MANAGER: { label: '管理员',   icon: Shield,       color: 'text-blue-500 bg-blue-500/10' },
    TESTER:  { label: '测试人员', icon: FlaskConical, color: 'text-emerald-500 bg-emerald-500/10' },
    MEMBER:  { label: '成员',     icon: UserIcon,     color: 'text-muted-foreground bg-muted' },
  };

  // 项目级角色管理：统一问权限接口（member:manage 映射 OWNER/MANAGER）
  const { user: currentUser } = useAuthStore();
  const myPerms = useMyPermissions(projectId);
  const canManageProjectRoles = !!myPerms.data?.data?.project?.includes('member:manage');
  const queryClient = useQueryClient();
  const roleUpdate = useMutation({
    mutationFn: ({ memberId, role }: { memberId: string; role: string }) =>
      projectApi.updateMemberRole(projectId, memberId, { role }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-members', projectId] });
      // 角色变了，权限点集合跟着变
      queryClient.invalidateQueries({ queryKey: ['my-permissions'] });
    },
    onError: (e) =>
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '角色修改失败'),
  });

  // 打开设置弹窗时，初始化表单
  const openSettings = () => {
    if (!project) return;
    setFormName(project.name);
    setFormKey(project.projectKey);
    setFormDesc(project.description || '');
    setFormGitUrl(project.gitRepoUrl || '');
    setSettingsOpen(true);
  };

  // 保存项目设置
  const handleSaveSettings = () => {
    if (!formName.trim() || !formKey.trim()) return;
    updateProject.mutate(
      {
        name: formName.trim(),
        projectKey: formKey.trim(),
        description: formDesc.trim(),
        gitRepoUrl: formGitUrl.trim() || undefined,
      },
      { onSuccess: () => setSettingsOpen(false) },
    );
  };

  // Git webhook 地址（带项目级密钥，供代码托管平台配置）
  const gitWebhookUrl = project?.gitWebhookSecret
    ? `${window.location.protocol}//${window.location.hostname}:8080/api/v1/projects/${projectId}/git/webhook?secret=${project.gitWebhookSecret}`
    : '';

  // 归档 / 恢复
  const handleToggleArchive = () => {
    if (!project) return;
    const newStatus = project.status === 'ARCHIVED' ? 'ACTIVE' : 'ARCHIVED';
    updateProject.mutate({ status: newStatus });
  };

  // 删除项目
  const handleDelete = () => {
    deleteProject.mutate(projectId, {
      onSuccess: () => router.push('/projects'),
    });
  };

  if (isLoading) {
    return (
      <div className="flex h-full items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  if (!project) {
    return (
      <div className="flex h-full flex-col items-center justify-center gap-4 text-muted-foreground">
        <p>项目不存在或已被删除</p>
        <Link href="/projects">
          <Button variant="outline">返回项目列表</Button>
        </Link>
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col overflow-hidden">
      {/* 项目头部 */}
      <div className="flex flex-wrap items-center gap-3 border-b px-4 py-4 sm:px-6">
        <Link href="/projects">
          <Button variant="ghost" size="icon" className="h-8 w-8">
            <ArrowLeft className="h-4 w-4" />
          </Button>
        </Link>

        {/* 项目图标 + 名称 */}
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10 text-lg font-bold text-primary">
            {project.icon || project.name.charAt(0)}
          </div>
          <div>
            <h1 className="text-lg font-semibold">{project.name}</h1>
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <span className="font-mono">{project.projectKey}</span>
              <span>·</span>
              <Badge
                variant={project.status === 'ACTIVE' ? 'default' : 'secondary'}
                className="text-[10px]"
              >
                {project.status === 'ACTIVE' ? '进行中' : project.status === 'PLANNING' ? '规划中' : '已归档'}
              </Badge>
            </div>
          </div>
        </div>

        <div className="ml-auto flex items-center gap-2">
          {/* 设置按钮 — 打开项目设置弹窗 */}
          <Button variant="outline" size="sm" onClick={openSettings}>
            <Settings className="mr-1.5 h-3.5 w-3.5" />
            设置
          </Button>

          {/* 成员管理 — 打开成员弹窗 */}
          <Button variant="outline" size="sm" onClick={() => setMemberDialogOpen(true)}>
            <Users className="mr-1.5 h-3.5 w-3.5" />
            成员
          </Button>

          {/* 邀请链接 — 独立弹窗 */}
          <Button variant="outline" size="sm" onClick={() => setInviteDialogOpen(true)}>
            <Link2 className="mr-1.5 h-3.5 w-3.5" />
            邀请链接
          </Button>

          {/* 游客链接 — 独立弹窗（仅 OWNER/MANAGER） */}
          {canManageProjectRoles && (
            <Button variant="outline" size="sm" onClick={() => setGuestDialogOpen(true)}>
              <Globe className="mr-1.5 h-3.5 w-3.5" />
              游客链接
            </Button>
          )}

          {/* 更多操作菜单 */}
          <DropdownMenu>
            <DropdownMenuTrigger
              className="inline-flex h-8 w-8 items-center justify-center rounded-md border border-border bg-background hover:bg-accent"
            >
              <MoreHorizontal className="h-4 w-4" />
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              {currentUser?.role === 'SUPER_ADMIN' && (
                <DropdownMenuItem onClick={() => setAutomationOpen(true)}>
                  <Zap className="mr-1.5 h-3.5 w-3.5 text-amber-500" /> 自动化规则
                </DropdownMenuItem>
              )}
              <DropdownMenuItem onClick={() => setRecycleBinOpen(true)}>
                <Undo2 className="mr-1.5 h-3.5 w-3.5" /> 回收站
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleToggleArchive}>
                {project.status === 'ARCHIVED' ? (
                  <><ArchiveRestore className="mr-1.5 h-3.5 w-3.5" /> 恢复项目</>
                ) : (
                  <><Archive className="mr-1.5 h-3.5 w-3.5" /> 归档项目</>
                )}
              </DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem variant="destructive" onClick={() => setDeleteConfirmOpen(true)}>
                <Trash2 className="mr-1.5 h-3.5 w-3.5" /> 删除项目
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      {/* 标签页 */}
      <Tabs value={activeTab} onValueChange={setActiveTab} className="flex-1 overflow-hidden">
        <div className="border-b px-4 sm:px-6">
          <TabsList className="h-10 bg-transparent">
            <TabsTrigger value="dashboard" className="gap-1.5 text-xs">
              <LayoutDashboard className="h-3.5 w-3.5" />
              概览
            </TabsTrigger>
            <TabsTrigger value="requirements" className="gap-1.5 text-xs">
              <FileText className="h-3.5 w-3.5" />
              需求
            </TabsTrigger>
            <TabsTrigger value="tasks" className="gap-1.5 text-xs">
              <ListTodo className="h-3.5 w-3.5" />
              任务
            </TabsTrigger>
            <TabsTrigger value="bugs" className="gap-1.5 text-xs">
              <Bug className="h-3.5 w-3.5" />
              Bug
            </TabsTrigger>
            <TabsTrigger value="feedback" className="gap-1.5 text-xs">
              <MessageSquarePlus className="h-3.5 w-3.5" />
              反馈
            </TabsTrigger>
            <TabsTrigger value="testing" className="gap-1.5 text-xs">
              <FlaskConical className="h-3.5 w-3.5" />
              测试
            </TabsTrigger>
          </TabsList>
        </div>

        <TabsContent value="dashboard" className="flex-1 overflow-auto p-6">
          <DashboardTab projectId={projectId} />
        </TabsContent>
        <TabsContent value="requirements" className="flex-1 overflow-auto">
          <RequirementsTab projectId={projectId} />
        </TabsContent>
        <TabsContent value="tasks" className="flex-1 overflow-auto p-6">
          <TasksTab projectId={projectId} />
        </TabsContent>
        <TabsContent value="bugs" className="flex-1 overflow-auto p-6">
          <BugsTab projectId={projectId} />
        </TabsContent>
        <TabsContent value="feedback" className="flex-1 overflow-auto p-6">
          <FeedbackTab projectId={projectId} />
        </TabsContent>
        <TabsContent value="testing" className="flex-1 overflow-auto p-6">
          <TestCasesTab projectId={projectId} />
        </TabsContent>
      </Tabs>

      {/* ============================================ */}
      {/* 项目设置弹窗 */}
      {/* ============================================ */}
      <Dialog open={settingsOpen} onOpenChange={setSettingsOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>项目设置</DialogTitle>
            <DialogDescription>修改项目的基本信息</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="project-name">项目名称</Label>
              <Input
                id="project-name"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                placeholder="请输入项目名称"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="project-key">项目标识</Label>
              <Input
                id="project-key"
                value={formKey}
                onChange={(e) => setFormKey(e.target.value.toUpperCase())}
                placeholder="例如 DF"
                maxLength={10}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="project-desc">项目描述</Label>
              <textarea
                id="project-desc"
                value={formDesc}
                onChange={(e) => setFormDesc(e.target.value)}
                placeholder="简要描述项目用途..."
                rows={3}
                className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="project-git">Git 仓库地址</Label>
              <Input
                id="project-git"
                value={formGitUrl}
                onChange={(e) => setFormGitUrl(e.target.value)}
                placeholder="例如 https://github.com/org/repo（可选）"
              />
              {gitWebhookUrl ? (
                <div className="space-y-1 rounded-md bg-muted/50 p-2">
                  <p className="text-[11px] text-muted-foreground">
                    在代码托管平台配置 push Webhook 到以下地址，提交会推送摘要到企业微信群：
                  </p>
                  <div className="flex items-center gap-1.5">
                    <Input readOnly value={gitWebhookUrl} className="h-7 text-[11px] font-mono" />
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 shrink-0 text-[11px]"
                      onClick={() => {
                        navigator.clipboard?.writeText(gitWebhookUrl).then(
                          () => alert('已复制 Webhook 地址'),
                          () => alert('复制失败，请手动选择复制'),
                        );
                      }}
                    >
                      复制
                    </Button>
                  </div>
                </div>
              ) : (
                <p className="text-[11px] text-muted-foreground">保存项目后可获取 Webhook 地址</p>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSettingsOpen(false)}>取消</Button>
            <Button
              onClick={handleSaveSettings}
              disabled={updateProject.isPending || !formName.trim() || !formKey.trim()}
            >
              {updateProject.isPending ? '保存中...' : '保存修改'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ============================================ */}
      {/* 成员管理弹窗 */}
      {/* ============================================ */}
      <Dialog open={memberDialogOpen} onOpenChange={(open) => { setMemberDialogOpen(open); if (!open) { setShowAddMember(false); setMemberSearch(''); } }}>
        <DialogContent className="sm:max-w-md max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>成员管理</DialogTitle>
            <DialogDescription>管理项目成员，添加或移除参与者</DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            {/* 添加成员按钮 */}
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-foreground">
                成员 <span className="text-muted-foreground">({members.length})</span>
              </span>
              <Button
                variant="outline"
                size="sm"
                className="h-7 gap-1 text-xs"
                onClick={() => setShowAddMember(!showAddMember)}
              >
                <UserPlus className="h-3 w-3" /> 添加成员
              </Button>
            </div>

            {/* 添加成员面板 */}
            {showAddMember && (
              <div className="rounded-lg border bg-muted/30 p-3 space-y-2">
                <div className="relative">
                  <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    value={memberSearch}
                    onChange={(e) => setMemberSearch(e.target.value)}
                    placeholder="搜索用户名或邮箱..."
                    className="h-8 pl-8 text-xs"
                  />
                </div>
                {availableUsers.length === 0 ? (
                  <p className="text-xs text-muted-foreground py-2 text-center">
                    {enterpriseUsers.isLoading
                      ? '加载中...'
                      : keyword
                        ? '没有匹配的企业成员'
                        : '没有可添加的用户'}
                  </p>
                ) : (
                  <div className="max-h-32 overflow-y-auto space-y-1">
                    {availableUsers.map((user) => (
                      <div key={user.id} className="flex items-center justify-between rounded-md px-2 py-1.5 hover:bg-muted">
                        <div className="flex items-center gap-2">
                          <Avatar size="sm">
                            {user.avatar && <AvatarImage src={user.avatar} />}
                            <AvatarFallback>{user.username.charAt(0).toUpperCase()}</AvatarFallback>
                          </Avatar>
                          <div>
                            <p className="text-xs font-medium">{user.username}</p>
                            <p className="text-[10px] text-muted-foreground">{user.email}</p>
                          </div>
                        </div>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-6 text-xs"
                          disabled={addMember.isPending}
                          onClick={() => {
                            addMember.mutate(
                              { userId: user.id, role: 'MEMBER' },
                              { onSuccess: () => setShowAddMember(true) },
                            );
                          }}
                        >
                          添加
                        </Button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* 成员列表 */}
            <div className="space-y-1">
              {members.map((member) => {
                const roleInfo = roleConfig[member.role as ProjectMemberRole] || roleConfig.MEMBER;
                const RoleIcon = roleInfo.icon;
                const showRoleSelect = member.role !== 'OWNER' && canManageProjectRoles;
                return (
                  <div key={member.id} className="flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-muted/50">
                    <Avatar size="sm">
                      {member.avatar && <AvatarImage src={member.avatar} />}
                      <AvatarFallback>{(member.username || '?').charAt(0).toUpperCase()}</AvatarFallback>
                    </Avatar>
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-medium truncate">{member.username || '未知用户'}</p>
                      <p className="text-[10px] text-muted-foreground truncate">{member.email}</p>
                    </div>
                    {showRoleSelect ? (
                      <select
                        value={member.role}
                        disabled={roleUpdate.isPending}
                        onChange={(e) =>
                          roleUpdate.mutate({ memberId: member.id, role: e.target.value })
                        }
                        title="调整项目角色"
                        className="h-6 rounded-md border border-input bg-background px-1.5 text-[11px] shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                      >
                        <option value="MANAGER">管理员</option>
                        <option value="TESTER">测试人员</option>
                        <option value="MEMBER">成员</option>
                      </select>
                    ) : (
                      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${roleInfo.color}`}>
                        <RoleIcon className="h-2.5 w-2.5" />
                        {roleInfo.label}
                      </span>
                    )}
                    {member.role !== 'OWNER' && (
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-5 w-5 shrink-0 text-muted-foreground hover:text-destructive"
                        disabled={removeMember.isPending}
                        onClick={() => removeMember.mutate(member.id)}
                      >
                        <X className="h-3 w-3" />
                      </Button>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setMemberDialogOpen(false)}>关闭</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ============================================ */}
      {/* 邀请链接弹窗 */}
      {/* ============================================ */}
      <Dialog open={inviteDialogOpen} onOpenChange={setInviteDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>邀请链接</DialogTitle>
            <DialogDescription>
              本企业的成员打开链接即可加入项目；非本企业成员无法加入。
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Input
                id="project-invite-link"
                readOnly
                value={inviteCodeQuery.isLoading ? '生成中...' : inviteLink || ''}
                className="h-9 text-xs font-mono"
              />
              <Button
                variant="outline"
                size="sm"
                className="h-9 gap-1 text-xs shrink-0"
                disabled={!inviteLink}
                onClick={handleCopyInviteLink}
              >
                {linkCopied ? (
                  <><Check className="h-3 w-3 text-green-500" /> 已复制</>
                ) : (
                  <><Copy className="h-3 w-3" /> 复制链接</>
                )}
              </Button>
            </div>
            {inviteCodeQuery.isError && (
              <p className="text-[11px] text-destructive">
                {(inviteCodeQuery.error as { response?: { data?: { message?: string } } })?.response?.data?.message || '邀请链接获取失败'}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setInviteDialogOpen(false)}>关闭</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ============================================ */}
      {/* 游客链接弹窗（仅 OWNER/MANAGER 可见入口） */}
      {/* ============================================ */}
      <Dialog open={guestDialogOpen} onOpenChange={setGuestDialogOpen}>
        <DialogContent className="sm:max-w-md max-h-[80vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>游客链接</DialogTitle>
            <DialogDescription>
              外部人员无需账号即可通过链接查看项目概况并提交 Bug / 建议
            </DialogDescription>
          </DialogHeader>
          <GuestLinksPanel projectId={projectId} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setGuestDialogOpen(false)}>关闭</Button>
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
              确定要删除项目「{project?.name}」吗？此操作不可恢复，项目下的所有任务和 Bug 将一并删除。
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

      {/* 自动化规则弹窗 */}
      <AutomationRulesDialog
        projectId={projectId}
        open={automationOpen}
        onOpenChange={setAutomationOpen}
      />

      {/* 回收站弹窗 */}
      <RecycleBinDialog
        projectId={projectId}
        open={recycleBinOpen}
        onOpenChange={setRecycleBinOpen}
      />
    </div>
  );
}
