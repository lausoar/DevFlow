'use client';

import { useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/stores';
import { taskApi } from '@/lib/api/task';
import { bugApi } from '@/lib/api/bug';
import { useProjectMembers } from '@/lib/hooks/use-projects';
import type { Priority } from '@/types';
import {
  CheckCircle2,
  Clock,
  FolderKanban,
  FileText,
  MessageSquare,
  Bot,
  Bell,
  Users,
  TrendingUp,
  AlertTriangle,
  Plus,
  BarChart3,
  ListTodo,
  Loader2,
  ArrowRight,
  Route,
  Flag,
} from 'lucide-react';
import { useProjects } from '@/lib/hooks';
import { useRouter } from 'next/navigation';

// 任务状态展示口径（我的任务卡）
const TASK_STATUS: Record<string, { label: string; dot: string }> = {
  TODO: { label: '待办', dot: 'bg-slate-400' },
  DOING: { label: '进行中', dot: 'bg-amber-500' },
  TESTING: { label: '测试中', dot: 'bg-blue-500' },
  DONE: { label: '已完成', dot: 'bg-green-500' },
};

// ============================================
// 工作台内容组件
// ============================================
export default function WorkspaceContent() {
  const { user } = useAuthStore();
  const router = useRouter();
  const queryClient = useQueryClient();

  // 从后端获取项目列表
  const { data: projectsRes, isLoading: projectsLoading } = useProjects({ page: 1, pageSize: 4 });
  const projects = projectsRes?.data?.records || [];

  // 统计口径=我名下的任务与 Bug（跨全部参与项目），不再只看第一个项目
  const { data: myTasksRes } = useQuery({ queryKey: ['tasks', 'my'], queryFn: () => taskApi.getMy() });
  const { data: myBugsRes } = useQuery({ queryKey: ['bugs', 'my'], queryFn: () => bugApi.getMy() });
  const myTasks = myTasksRes?.data || [];
  const myBugs = myBugsRes?.data || [];

  // 新建任务弹窗状态
  const [createTaskOpen, setCreateTaskOpen] = useState(false);
  const [selectedProjectId, setSelectedProjectId] = useState('');
  const [taskTitle, setTaskTitle] = useState('');
  const [taskDesc, setTaskDesc] = useState('');
  const [taskPriority, setTaskPriority] = useState<Priority>('MEDIUM');
  const [taskAssigneeId, setTaskAssigneeId] = useState('');

  // 查询选中项目的成员（用于指派下拉）
  const { data: taskMembersRes } = useProjectMembers(
    createTaskOpen && selectedProjectId ? selectedProjectId : null,
  );
  const taskMembers = taskMembersRes?.data || [];

  // 创建任务 mutation
  const createTaskMutation = useMutation({
    mutationFn: (data: Parameters<typeof taskApi.create>[1]) =>
      taskApi.create(selectedProjectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      setCreateTaskOpen(false);
    },
  });

  // 打开新建任务弹窗
  const handleOpenCreateTask = () => {
    setSelectedProjectId(projects[0]?.id || '');
    setTaskTitle('');
    setTaskDesc('');
    setTaskPriority('MEDIUM');
    setTaskAssigneeId('');
    setCreateTaskOpen(true);
  };

  // 提交新建任务
  const handleCreateTask = () => {
    if (!taskTitle.trim() || !selectedProjectId) return;
    createTaskMutation.mutate({
      title: taskTitle.trim(),
      description: taskDesc.trim(),
      priority: taskPriority,
      assigneeId: taskAssigneeId || undefined,
      status: 'TODO',
    });
  };

  // 根据真实数据计算统计（我名下）
  const stats = useMemo(() => {
    const todoTasks = myTasks.filter((t) => t.status === 'TODO').length;
    const doingTasks = myTasks.filter((t) => t.status === 'DOING' || t.status === 'TESTING').length;
    const doneTasks = myTasks.filter((t) => t.status === 'DONE').length;
    const openBugs = myBugs.filter((b) => b.status !== 'RESOLVED' && b.status !== 'CLOSED').length;

    return [
      { label: '我的待办', value: todoTasks, icon: ListTodo, color: 'text-blue-500', bg: 'bg-blue-500/10' },
      { label: '进行中', value: doingTasks, icon: Clock, color: 'text-amber-500', bg: 'bg-amber-500/10' },
      { label: '已完成', value: doneTasks, icon: CheckCircle2, color: 'text-green-500', bg: 'bg-green-500/10' },
      { label: '我的风险项', value: openBugs, icon: AlertTriangle, color: 'text-red-500', bg: 'bg-red-500/10' },
    ];
  }, [myTasks, myBugs]);

  return (
    <div className="space-y-6">
      {/* 欢迎区域 */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">
            你好，{user?.realName || user?.username || '开发者'} 👋
          </h1>
          <p className="text-sm text-muted-foreground">
            今天是 {new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })}
          </p>
        </div>
        <Button className="gap-2" onClick={handleOpenCreateTask}>
          <Plus className="h-4 w-4" />
          新建任务
        </Button>
      </div>

      {/* 统计卡片 */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon;
          return (
            <Card key={stat.label} className="transition-devflow hover:shadow-md">
              <CardContent className="p-4 sm:p-6">
                <div className="flex items-center justify-between">
                  <div>
                    <p className="text-sm font-medium text-muted-foreground">{stat.label}</p>
                    <p className="text-2xl font-bold text-foreground">{stat.value}</p>
                  </div>
                  <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${stat.bg}`}>
                    <Icon className={`h-5 w-5 ${stat.color}`} />
                  </div>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* 主体内容区 */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* 左侧 - 项目 */}
        <div className="space-y-6 lg:col-span-2">
          {/* 最近项目 */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between pb-2">
              <div>
                <CardTitle className="text-base">最近项目</CardTitle>
                <CardDescription>你参与的项目</CardDescription>
              </div>
              <Button variant="ghost" size="sm" className="gap-1" onClick={() => router.push('/projects') }>
                全部项目 <ArrowRight className="h-3 w-3" />
              </Button>
            </CardHeader>
            <CardContent>
              {projectsLoading ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                  <span className="ml-2 text-sm text-muted-foreground">加载项目中...</span>
                </div>
              ) : projects.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
                  <FolderKanban className="mb-2 h-8 w-8 opacity-30" />
                  <p className="text-sm">暂无项目</p>
                </div>
              ) : (
                <div className="grid gap-3 sm:grid-cols-2">
                  {projects.map((project) => (
                    <div
                      key={project.id}
                      className="group rounded-lg border border-border/50 p-4 transition-devflow hover:border-border hover:shadow-sm"
                    >
                      <div className="mb-3 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand/10 text-xs font-bold text-brand">
                            {project.projectKey}
                          </div>
                          <span className="font-medium">{project.name}</span>
                        </div>
                      </div>
                      <div className="flex items-center gap-3 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Users className="h-3 w-3" /> {project.memberCount || 0} 成员
                        </span>
                        <Badge
                          variant="secondary"
                          className={`text-[10px] ${
                            project.status === 'ACTIVE' ? 'bg-green-500/10 text-green-600' :
                            project.status === 'PLANNING' ? 'bg-amber-500/10 text-amber-600' :
                            'bg-gray-500/10 text-gray-500'
                          }`}
                        >
                          {project.status === 'ACTIVE' ? '进行中' : project.status === 'PLANNING' ? '规划中' : '已归档'}
                        </Badge>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>

        {/* 右侧 - 快捷入口 */}
        <div className="space-y-6">
          {/* AI 助手卡片 */}
          <Card className="border-brand/20 bg-gradient-to-br from-brand/5 to-transparent">
            <CardContent className="p-4 sm:p-6">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand text-white">
                  <Bot className="h-5 w-5" />
                </div>
                <div>
                  <p className="font-semibold">AI 助手</p>
                  <p className="text-xs text-muted-foreground">你的企业 AI 秘书</p>
                </div>
              </div>
              <div className="mt-4 space-y-2">
                <Button variant="outline" className="w-full justify-start gap-2 text-xs" size="sm" onClick={() => router.push('/ai')}>
                  <FileText className="h-3 w-3" /> 生成今日日报
                </Button>
                <Button variant="outline" className="w-full justify-start gap-2 text-xs" size="sm" onClick={() => router.push('/reports')}>
                  <BarChart3 className="h-3 w-3" /> 分析项目健康度
                </Button>
              </div>
            </CardContent>
          </Card>

          {/* 我的任务（名下最近 5 条，点击直达项目任务页） */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-base">我的任务</CardTitle>
            </CardHeader>
            <CardContent>
              {myTasks.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-6 text-muted-foreground">
                  <ListTodo className="mb-2 h-8 w-8 opacity-30" />
                  <p className="text-sm">暂无名下任务</p>
                </div>
              ) : (
                <div className="space-y-0.5">
                  {myTasks.slice(0, 5).map((t) => {
                    const st = TASK_STATUS[t.status] || { label: t.status, dot: 'bg-slate-400' };
                    return (
                      <button
                        key={t.id}
                        onClick={() => router.push(`/projects/${t.projectId}?tab=tasks`)}
                        className="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left hover:bg-muted/50"
                      >
                        <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${st.dot}`} />
                        <span className="min-w-0 flex-1 truncate text-xs">{t.title}</span>
                        <span className="shrink-0 text-[10px] text-muted-foreground">{st.label}</span>
                      </button>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>

          {/* 在线成员 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-base">快速入口</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 gap-2">
                <Button variant="outline" className="h-auto flex-col gap-1 py-3" size="sm" onClick={() => router.push('/chat')}>
                  <MessageSquare className="h-4 w-4" />
                  <span className="text-xs">消息</span>
                </Button>
                <Button variant="outline" className="h-auto flex-col gap-1 py-3" size="sm" onClick={() => router.push('/notifications')}>
                  <Bell className="h-4 w-4" />
                  <span className="text-xs">通知</span>
                </Button>
                <Button variant="outline" className="h-auto flex-col gap-1 py-3" size="sm" onClick={() => router.push('/docs')}>
                  <FileText className="h-4 w-4" />
                  <span className="text-xs">文档</span>
                </Button>
                <Button variant="outline" className="h-auto flex-col gap-1 py-3" size="sm" onClick={() => router.push('/projects')}>
                  <FolderKanban className="h-4 w-4" />
                  <span className="text-xs">项目</span>
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* ============================================ */}
      {/* 新建任务弹窗 */}
      {/* ============================================ */}
      <Dialog open={createTaskOpen} onOpenChange={setCreateTaskOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>新建任务</DialogTitle>
          </DialogHeader>

          {projects.length === 0 ? (
            <div className="py-6 text-center text-sm text-muted-foreground">
              暂无可用项目，请先创建项目
            </div>
          ) : (
            <div className="space-y-4">
              {/* 所属项目 */}
              <div className="space-y-2">
                <Label>所属项目</Label>
                <Select
                  value={selectedProjectId}
                  onValueChange={(v) => { if (v !== null) setSelectedProjectId(v); }}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="选择项目">
                      {(v: string | null) => projects.find(p => p.id === v)?.name}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {projects.map((p) => (
                      <SelectItem key={p.id} value={p.id}>
                        {p.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* 任务标题 */}
              <div className="space-y-2">
                <Label>任务标题 <span className="text-red-500">*</span></Label>
                <Input
                  value={taskTitle}
                  onChange={(e) => setTaskTitle(e.target.value)}
                  placeholder="输入任务标题"
                  onKeyDown={(e) => e.key === 'Enter' && handleCreateTask()}
                />
              </div>

              {/* 任务描述 */}
              <div className="space-y-2">
                <Label>任务描述</Label>
                <textarea
                  value={taskDesc}
                  onChange={(e) => setTaskDesc(e.target.value)}
                  placeholder="简要描述任务内容..."
                  rows={3}
                  className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                />
              </div>

              {/* 优先级 */}
              <div className="space-y-2">
                <Label>优先级</Label>
                <Select
                  value={taskPriority}
                  onValueChange={(v) => { if (v !== null) setTaskPriority(v as Priority); }}
                >
                  <SelectTrigger>
                    <SelectValue>
                      {(v: string | null) => {
                        const labels: Record<string, string> = { CRITICAL: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' };
                        return v ? labels[v] || v : null;
                      }}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CRITICAL">
                      <span className="flex items-center gap-2">
                        <Flag className="h-3 w-3 text-red-500" /> 紧急
                      </span>
                    </SelectItem>
                    <SelectItem value="HIGH">
                      <span className="flex items-center gap-2">
                        <Flag className="h-3 w-3 text-orange-500" /> 高
                      </span>
                    </SelectItem>
                    <SelectItem value="MEDIUM">
                      <span className="flex items-center gap-2">
                        <Flag className="h-3 w-3 text-yellow-500" /> 中
                      </span>
                    </SelectItem>
                    <SelectItem value="LOW">
                      <span className="flex items-center gap-2">
                        <Flag className="h-3 w-3 text-muted-foreground" /> 低
                      </span>
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>

              {/* 指派给 */}
              {taskMembers.length > 0 && (
                <div className="space-y-2">
                  <Label>指派给</Label>
                  <Select
                    value={taskAssigneeId || '_none'}
                    onValueChange={(v) => setTaskAssigneeId(!v || v === '_none' ? '' : v)}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="暂不指派">
                        {(v: string | null) => {
                          if (!v || v === '_none') return '暂不指派';
                          return taskMembers.find(m => m.userId === v)?.realName || taskMembers.find(m => m.userId === v)?.username || '未知用户';
                        }}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="_none">暂不指派</SelectItem>
                      {taskMembers.map((m) => (
                        <SelectItem key={m.id} value={m.userId}>
                          {m.realName || m.username || '未知用户'}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateTaskOpen(false)}>取消</Button>
            <Button
              onClick={handleCreateTask}
              disabled={createTaskMutation.isPending || !taskTitle.trim() || !selectedProjectId}
            >
              {createTaskMutation.isPending ? '创建中...' : '创建任务'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
