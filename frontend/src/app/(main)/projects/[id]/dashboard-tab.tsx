'use client';

import { useMemo } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import {
  ListTodo,
  CheckCircle2,
  Clock,
  AlertTriangle,
  Zap,
  Target,
  TrendingUp,
  Users,
  Flame,
  GanttChartSquare,
  BarChart3,
  Activity,
  GitCommit as GitCommitIcon,
} from 'lucide-react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
  LineChart,
  Line,
  BarChart,
  Bar,
  Cell,
} from 'recharts';
import { useQuery } from '@tanstack/react-query';
import { request } from '@/lib/api/client';
import { useTasks } from '@/lib/hooks/use-tasks';
import { useSprints } from '@/lib/hooks/use-sprints';
import { useBugs } from '@/lib/hooks/use-bugs';
import { useProjectMembers } from '@/lib/hooks/use-projects';
import type { Task, Bug, Sprint, ProjectMember, GitCommit } from '@/types';

// ============================================
// Dashboard 数据类型
// ============================================
interface DashboardData {
  stats: {
    totalTasks: number;
    completedTasks: number;
    inProgressTasks: number;
    todoTasks: number;
    openBugs: number;
    criticalBugs: number;
    completionRate: number;
    overdueRate: number;
  };
  sprint: {
    id: string;
    name: string;
    status: string;
    totalTasks: number;
    completedTasks: number;
    progress: number;
  } | null;
  taskDistribution: Record<string, number>;
  bugDistribution: Record<string, number>;
  recentActivities: Array<{
    userId: string;
    username: string;
    action: string;
    target: string;
    time: string;
  }>;
  memberWorkloads: Array<{
    userId: string;
    username: string;
    taskCount: number;
    completedCount: number;
    bugCount: number;
  }>;
}

// ============================================
// 图表颜色
// ============================================
const MEMBER_COLORS = [
  '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#14b8a6',
];

const PRIORITY_COLORS: Record<string, string> = {
  CRITICAL: '#dc2626',
  HIGH: '#f97316',
  MEDIUM: '#eab308',
  LOW: '#94a3b8',
};

const STATUS_BAR_COLORS: Record<string, string> = {
  TODO: '#94a3b8',
  DOING: '#3b82f6',
  TESTING: '#f59e0b',
  DONE: '#10b981',
};

// ============================================
// Dashboard 标签页
// ============================================
export function DashboardTab({ projectId }: { projectId: string }) {
  // 获取 Dashboard 数据
  const { data: dashboardRes, isLoading } = useQuery({
    queryKey: ['dashboard', projectId],
    queryFn: () => request<DashboardData>('GET', `/projects/${projectId}/dashboard`),
  });

  const dashboard = dashboardRes?.data;

  // 备用数据源
  const { data: tasksRes } = useTasks(projectId);
  const { data: sprintsRes } = useSprints(projectId);
  const { data: bugsRes } = useBugs(projectId);
  const { data: membersRes } = useProjectMembers(projectId);

  const tasks = tasksRes?.data?.records || [];
  const sprints = sprintsRes?.data || [];
  const bugs = bugsRes?.data?.records || [];
  const members = membersRes?.data || [];

  // 统计指标
  const stats = useMemo(() => {
    if (dashboard?.stats) return dashboard.stats;

    const total = tasks.length;
    const completed = tasks.filter((t) => t.status === 'DONE').length;
    const inProgress = tasks.filter((t) => t.status === 'DOING').length;
    const todo = tasks.filter((t) => t.status === 'TODO').length;
    const openBugs = bugs.filter((b) => b.status !== 'RESOLVED' && b.status !== 'CLOSED').length;
    const criticalBugs = bugs.filter(
      (b) => b.severity === 'BLOCKER' || b.severity === 'CRITICAL'
    ).length;
    const overdue = tasks.filter(
      (t) => t.dueDate && new Date(t.dueDate) < new Date() && t.status !== 'DONE'
    ).length;

    return {
      totalTasks: total,
      completedTasks: completed,
      inProgressTasks: inProgress,
      todoTasks: todo,
      openBugs,
      criticalBugs,
      completionRate: total > 0 ? (completed / total) * 100 : 0,
      overdueRate: total > 0 ? (overdue / total) * 100 : 0,
    };
  }, [dashboard, tasks, bugs]);

  const activeSprint = useMemo(() => {
    if (dashboard?.sprint) return dashboard.sprint;
    const active = sprints.find((s) => s.status === 'ACTIVE');
    if (!active) return null;

    const sprintTasks = tasks.filter((t) => t.sprintId === active.id);
    const sprintCompleted = sprintTasks.filter((t) => t.status === 'DONE').length;

    return {
      id: active.id,
      name: active.name,
      status: active.status,
      totalTasks: sprintTasks.length,
      completedTasks: sprintCompleted,
      progress: sprintTasks.length > 0 ? (sprintCompleted / sprintTasks.length) * 100 : 0,
    };
  }, [dashboard, sprints, tasks]);

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 统计卡片 */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatCard
          title="总任务"
          value={stats.totalTasks}
          icon={<ListTodo className="h-4 w-4 text-blue-500" />}
          color="blue"
        />
        <StatCard
          title="已完成"
          value={stats.completedTasks}
          subtitle={`${stats.completionRate.toFixed(0)}%`}
          icon={<CheckCircle2 className="h-4 w-4 text-green-500" />}
          color="green"
        />
        <StatCard
          title="进行中"
          value={stats.inProgressTasks}
          icon={<Clock className="h-4 w-4 text-amber-500" />}
          color="amber"
        />
        <StatCard
          title="待处理 Bug"
          value={stats.openBugs}
          subtitle={stats.criticalBugs > 0 ? `${stats.criticalBugs} 个严重` : undefined}
          icon={<AlertTriangle className="h-4 w-4 text-red-500" />}
          color="red"
        />
      </div>

      {/* Sprint 进度 + 任务分布 */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Sprint 进度卡片 */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-sm font-medium">
              <Zap className="h-4 w-4 text-primary" />
              当前迭代
            </CardTitle>
          </CardHeader>
          <CardContent>
            {activeSprint ? (
              <div className="space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="font-semibold">{activeSprint.name}</h3>
                  <Badge variant="default" className="text-[10px]">
                    {activeSprint.status === 'ACTIVE' ? '进行中' : activeSprint.status}
                  </Badge>
                </div>
                <div className="space-y-2">
                  <div className="flex justify-between text-sm text-muted-foreground">
                    <span>
                      已完成 {activeSprint.completedTasks} / {activeSprint.totalTasks}
                    </span>
                    <span>{activeSprint.progress.toFixed(0)}%</span>
                  </div>
                  <Progress value={activeSprint.progress} className="h-2" />
                </div>
              </div>
            ) : (
              <div className="flex flex-col items-center gap-2 py-6 text-muted-foreground">
                <Target className="h-8 w-8 opacity-50" />
                <p className="text-sm">暂无进行中的迭代</p>
              </div>
            )}
          </CardContent>
        </Card>

        {/* 任务分布 */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="flex items-center gap-2 text-sm font-medium">
              <TrendingUp className="h-4 w-4 text-primary" />
              任务分布
            </CardTitle>
          </CardHeader>
          <CardContent>
            <TaskDistribution data={dashboard?.taskDistribution} tasks={tasks} />
          </CardContent>
        </Card>
      </div>

      {/* 燃尽图 + 任务/Bug 趋势图 */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <BurndownChart tasks={tasks} sprints={sprints} />
        <TrendChart tasks={tasks} bugs={bugs} />
      </div>

      {/* 成员工作量 + 甘特图 */}
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <WorkloadChart tasks={tasks} bugs={bugs} members={members} />
        <GanttChart tasks={tasks} />
      </div>

      {/* 最近活动时间线 */}
      <ActivityTimeline tasks={tasks} bugs={bugs} members={members} />

      {/* 最近提交（Git webhook 落库，配置了才有数据） */}
      <RecentCommits projectId={projectId} />
    </div>
  );
}

// ============================================
// 0. 最近提交卡片（Git 集成）
// ============================================
function RecentCommits({ projectId }: { projectId: string }) {
  const { data: commitsRes } = useQuery({
    queryKey: ['git-commits', projectId],
    queryFn: () => request<GitCommit[]>('GET', `/projects/${projectId}/git/commits`),
  });
  const commits = commitsRes?.data || [];
  if (commits.length === 0) return null;

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base font-semibold">最近提交</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="space-y-1.5">
          {commits.slice(0, 8).map((c) => (
            <div key={c.id} className="flex items-center gap-2 rounded-md px-2 py-1.5 text-xs hover:bg-muted/50">
              <GitCommitIcon className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
              <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[10px]">
                {c.sha.slice(0, 7)}
              </span>
              <span className="min-w-0 flex-1 truncate">
                {c.webUrl ? (
                  <a href={c.webUrl} target="_blank" rel="noreferrer" className="hover:underline">
                    {(c.message || '').split('\n')[0]}
                  </a>
                ) : (
                  (c.message || '').split('\n')[0]
                )}
              </span>
              {c.authorName && (
                <span className="shrink-0 text-muted-foreground">{c.authorName}</span>
              )}
              <span className="shrink-0 text-[10px] text-muted-foreground">
                {new Date(c.pushedAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
              </span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

// ============================================
// 1. 燃尽图组件
// ============================================
function BurndownChart({ tasks, sprints }: { tasks: Task[]; sprints: Sprint[] }) {
  const chartData = useMemo(() => {
    const sprint = sprints.find((s) => s.status === 'ACTIVE') || sprints.find((s) => s.status === 'COMPLETED');
    if (!sprint) return [];

    const sprintTasks = tasks.filter((t) => t.sprintId === sprint.id);
    if (sprintTasks.length === 0) return [];

    const startDate = sprint.startDate ? new Date(sprint.startDate) : new Date();
    const endDate = sprint.endDate ? new Date(sprint.endDate) : new Date(startDate.getTime() + 14 * 86400000);
    const totalDays = Math.max(1, Math.ceil((endDate.getTime() - startDate.getTime()) / 86400000));
    const totalPoints = sprintTasks.reduce((sum, t) => sum + (t.storyPoints || 1), 0);

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const data: Array<{ date: string; 理想: number; 实际: number }> = [];

    for (let i = 0; i <= totalDays; i++) {
      const currentDate = new Date(startDate.getTime() + i * 86400000);
      if (currentDate > today && sprint.status !== 'COMPLETED') break;

      const ideal = Math.max(0, totalPoints * (1 - i / totalDays));

      const completedByDate = sprintTasks
        .filter((t) => {
          if (t.status !== 'DONE') return false;
          const updated = new Date(t.updatedAt);
          return updated <= new Date(currentDate.getTime() + 86400000);
        })
        .reduce((sum, t) => sum + (t.storyPoints || 1), 0);
      const actual = Math.max(0, totalPoints - completedByDate);

      data.push({
        date: `${currentDate.getMonth() + 1}/${currentDate.getDate()}`,
        理想: Math.round(ideal * 10) / 10,
        实际: Math.round(actual * 10) / 10,
      });
    }

    return data;
  }, [tasks, sprints]);

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <Flame className="h-4 w-4 text-orange-500" />
          燃尽图
        </CardTitle>
      </CardHeader>
      <CardContent>
        {chartData.length > 0 ? (
          <ResponsiveContainer width="100%" height={220}>
            <AreaChart data={chartData}>
              <defs>
                <linearGradient id="burndownActual" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="date" tick={{ fontSize: 10 }} stroke="var(--muted-foreground)" />
              <YAxis tick={{ fontSize: 10 }} stroke="var(--muted-foreground)" />
              <Tooltip
                contentStyle={{
                  backgroundColor: 'var(--card)',
                  border: '1px solid var(--border)',
                  borderRadius: '8px',
                  fontSize: '12px',
                }}
              />
              <Legend wrapperStyle={{ fontSize: '11px' }} />
              <Area
                type="monotone"
                dataKey="理想"
                stroke="#94a3b8"
                strokeWidth={2}
                strokeDasharray="5 5"
                fill="none"
              />
              <Area
                type="monotone"
                dataKey="实际"
                stroke="#3b82f6"
                strokeWidth={2}
                fill="url(#burndownActual)"
              />
            </AreaChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex h-[220px] flex-col items-center justify-center gap-2 text-muted-foreground">
            <Flame className="h-6 w-6 opacity-40" />
            <p className="text-xs">暂无迭代数据</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================
// 2. 任务/Bug 趋势图
// ============================================
function TrendChart({ tasks, bugs }: { tasks: Task[]; bugs: Bug[] }) {
  const chartData = useMemo(() => {
    const days = 14;
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const data: Array<{
      date: string;
      新建任务: number;
      完成任务: number;
      新建Bug: number;
    }> = [];

    for (let i = days - 1; i >= 0; i--) {
      const date = new Date(today.getTime() - i * 86400000);
      const dateStr = `${date.getMonth() + 1}/${date.getDate()}`;
      const nextDate = new Date(date.getTime() + 86400000);

      const createdTasks = tasks.filter((t) => {
        const created = new Date(t.createdAt);
        return created >= date && created < nextDate;
      }).length;

      const completedTasks = tasks.filter((t) => {
        if (t.status !== 'DONE') return false;
        const updated = new Date(t.updatedAt);
        return updated >= date && updated < nextDate;
      }).length;

      const createdBugs = bugs.filter((b) => {
        const created = new Date(b.createdAt);
        return created >= date && created < nextDate;
      }).length;

      data.push({ date: dateStr, 新建任务: createdTasks, 完成任务: completedTasks, 新建Bug: createdBugs });
    }

    return data;
  }, [tasks, bugs]);

  const hasData = chartData.some((d) => d.新建任务 > 0 || d.完成任务 > 0 || d.新建Bug > 0);

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <BarChart3 className="h-4 w-4 text-blue-500" />
          任务/Bug 趋势（近 14 天）
        </CardTitle>
      </CardHeader>
      <CardContent>
        {hasData ? (
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="date" tick={{ fontSize: 10 }} stroke="var(--muted-foreground)" />
              <YAxis tick={{ fontSize: 10 }} stroke="var(--muted-foreground)" allowDecimals={false} />
              <Tooltip
                contentStyle={{
                  backgroundColor: 'var(--card)',
                  border: '1px solid var(--border)',
                  borderRadius: '8px',
                  fontSize: '12px',
                }}
              />
              <Legend wrapperStyle={{ fontSize: '11px' }} />
              <Line type="monotone" dataKey="新建任务" stroke="#3b82f6" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="完成任务" stroke="#10b981" strokeWidth={2} dot={false} />
              <Line type="monotone" dataKey="新建Bug" stroke="#ef4444" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex h-[220px] flex-col items-center justify-center gap-2 text-muted-foreground">
            <BarChart3 className="h-6 w-6 opacity-40" />
            <p className="text-xs">暂无趋势数据</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================
// 3. 成员工作量分布
// ============================================
function WorkloadChart({
  tasks,
  bugs,
  members,
}: {
  tasks: Task[];
  bugs: Bug[];
  members: ProjectMember[];
}) {
  const chartData = useMemo(() => {
    if (members.length === 0) return [];

    return members.map((m) => {
      const memberTasks = tasks.filter((t) => t.assigneeId === m.userId);
      const memberBugs = bugs.filter((b) => b.assigneeId === m.userId);
      const completed = memberTasks.filter((t) => t.status === 'DONE').length;
      const inProgress = memberTasks.filter((t) => t.status === 'DOING').length;
      const todo = memberTasks.filter((t) => t.status === 'TODO').length;

      return {
        name: m.username || '未知',
        待办: todo,
        进行中: inProgress,
        已完成: completed,
        Bug: memberBugs.length,
      };
    });
  }, [tasks, bugs, members]);

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <Users className="h-4 w-4 text-purple-500" />
          成员工作量
        </CardTitle>
      </CardHeader>
      <CardContent>
        {chartData.length > 0 ? (
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={chartData}>
              <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
              <XAxis dataKey="name" tick={{ fontSize: 10 }} stroke="var(--muted-foreground)" />
              <YAxis tick={{ fontSize: 10 }} stroke="var(--muted-foreground)" allowDecimals={false} />
              <Tooltip
                contentStyle={{
                  backgroundColor: 'var(--card)',
                  border: '1px solid var(--border)',
                  borderRadius: '8px',
                  fontSize: '12px',
                }}
              />
              <Legend wrapperStyle={{ fontSize: '11px' }} />
              <Bar dataKey="待办" stackId="a" fill="#94a3b8" />
              <Bar dataKey="进行中" stackId="a" fill="#3b82f6" />
              <Bar dataKey="已完成" stackId="a" fill="#10b981" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="flex h-[220px] flex-col items-center justify-center gap-2 text-muted-foreground">
            <Users className="h-6 w-6 opacity-40" />
            <p className="text-xs">暂无成员数据</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================
// 4. 甘特图组件（迷你版）
// ============================================
function GanttChart({ tasks }: { tasks: Task[] }) {
  const { chartData, minDate, dayWidth, totalDays } = useMemo(() => {
    const validTasks = tasks.filter((t) => t.createdAt);
    if (validTasks.length === 0) {
      return { chartData: [], minDate: new Date(), dayWidth: 20, totalDays: 14 };
    }

    // 计算时间范围
    const dates = validTasks.flatMap((t) => {
      const start = new Date(t.createdAt);
      const end = t.dueDate ? new Date(t.dueDate) : new Date(t.updatedAt || t.createdAt);
      return [start, end];
    });
    const min = new Date(Math.min(...dates.map((d) => d.getTime())));
    const max = new Date(Math.max(...dates.map((d) => d.getTime())));
    min.setDate(min.getDate() - 1);
    max.setDate(max.getDate() + 1);

    const days = Math.max(7, Math.ceil((max.getTime() - min.getTime()) / 86400000));
    const width = Math.max(16, Math.min(32, 600 / days));

    const data = validTasks
      .slice(0, 15) // 最多显示 15 条
      .map((task) => {
        const start = new Date(task.createdAt);
        const end = task.dueDate
          ? new Date(task.dueDate)
          : new Date(task.updatedAt || task.createdAt);
        const startOffset = (start.getTime() - min.getTime()) / 86400000;
        const duration = Math.max(1, (end.getTime() - start.getTime()) / 86400000);

        let progress = 0;
        if (task.status === 'DONE') progress = 100;
        else if (task.status === 'TESTING') progress = 80;
        else if (task.status === 'DOING') progress = 50;

        return {
          task,
          left: startOffset * width,
          width: duration * width,
          progress,
          overdue:
            task.dueDate && new Date(task.dueDate) < new Date() && task.status !== 'DONE',
        };
      });

    return { chartData: data, minDate: min, dayWidth: width, totalDays: days };
  }, [tasks]);

  // 日期刻度
  const dateLabels = useMemo(() => {
    const labels: Array<{ label: string; left: number }> = [];
    const step = Math.max(1, Math.floor(totalDays / 8));
    for (let i = 0; i <= totalDays; i += step) {
      const d = new Date(minDate.getTime() + i * 86400000);
      labels.push({
        label: `${d.getMonth() + 1}/${d.getDate()}`,
        left: i * dayWidth,
      });
    }
    return labels;
  }, [minDate, totalDays, dayWidth]);

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <GanttChartSquare className="h-4 w-4 text-emerald-500" />
          任务甘特图
        </CardTitle>
      </CardHeader>
      <CardContent>
        {chartData.length > 0 ? (
          <div className="overflow-x-auto">
            <div className="min-w-[500px]">
              {/* 日期轴 */}
              <div className="relative mb-2 h-5 border-b">
                {dateLabels.map((label, i) => (
                  <span
                    key={i}
                    className="absolute top-0 text-[10px] text-muted-foreground"
                    style={{ left: `${label.left}px` }}
                  >
                    {label.label}
                  </span>
                ))}
              </div>

              {/* 任务条 */}
              <div className="space-y-1.5">
                {chartData.map(({ task, left, width, progress, overdue }) => {
                  const statusColor = STATUS_BAR_COLORS[task.status] || '#94a3b8';
                  return (
                    <div key={task.id} className="flex items-center gap-2">
                      <span
                        className="w-24 shrink-0 truncate text-[11px]"
                        title={task.title}
                      >
                        {task.title}
                      </span>
                      <div className="relative h-5 flex-1 rounded bg-muted/30">
                        {/* 任务条 */}
                        <div
                          className="absolute top-0.5 h-4 rounded-sm transition-all"
                          style={{
                            left: `${left}px`,
                            width: `${Math.max(8, width)}px`,
                            backgroundColor: statusColor,
                            opacity: 0.7,
                          }}
                          title={`${task.title} (${task.status})`}
                        >
                          {/* 进度 */}
                          <div
                            className="h-full rounded-sm"
                            style={{
                              width: `${progress}%`,
                              backgroundColor: statusColor,
                              opacity: 1,
                            }}
                          />
                        </div>
                        {/* 延期标记 */}
                        {overdue && (
                          <AlertTriangle
                            className="absolute top-0.5 h-3.5 w-3.5 text-red-500"
                            style={{ left: `${left + Math.max(8, width) + 2}px` }}
                          />
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* 图例 */}
              <div className="mt-3 flex flex-wrap items-center gap-3 border-t pt-2 text-[10px]">
                {Object.entries(STATUS_BAR_COLORS).map(([status, color]) => (
                  <span key={status} className="flex items-center gap-1">
                    <span
                      className="h-2 w-3 rounded-sm"
                      style={{ backgroundColor: color }}
                    />
                    <span className="text-muted-foreground">
                      {{ TODO: '待办', DOING: '进行中', TESTING: '测试', DONE: '已完成' }[status]}
                    </span>
                  </span>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <div className="flex h-[220px] flex-col items-center justify-center gap-2 text-muted-foreground">
            <GanttChartSquare className="h-6 w-6 opacity-40" />
            <p className="text-xs">暂无任务数据</p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================
// 5. 最近活动时间线
// ============================================
function ActivityTimeline({
  tasks,
  bugs,
  members,
}: {
  tasks: Task[];
  bugs: Bug[];
  members: ProjectMember[];
}) {
  const activities = useMemo(() => {
    // 构建用户名映射
    const nameMap: Record<string, string> = {};
    members.forEach((m) => {
      nameMap[m.userId] = m.username || '未知';
    });

    // 合并任务和 Bug 的最近活动
    const items: Array<{
      id: string;
      type: 'task' | 'bug';
      username: string;
      action: string;
      target: string;
      time: string;
      timestamp: number;
    }> = [];

    // 最近完成的任务
    tasks
      .filter((t) => t.status === 'DONE' && t.updatedAt)
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, 10)
      .forEach((t) => {
        items.push({
          id: `task-done-${t.id}`,
          type: 'task',
          username: t.assigneeId ? (nameMap[t.assigneeId] || '未知') : '未分配',
          action: '完成了任务',
          target: t.title,
          time: t.updatedAt,
          timestamp: new Date(t.updatedAt).getTime(),
        });
      });

    // 最近创建的 Bug
    bugs
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 8)
      .forEach((b) => {
        items.push({
          id: `bug-new-${b.id}`,
          type: 'bug',
          username: nameMap[b.reporterId] || '未知',
          action: '报告了 Bug',
          target: b.title,
          time: b.createdAt,
          timestamp: new Date(b.createdAt).getTime(),
        });
      });

    // 最近创建的任务
    tasks
      .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
      .slice(0, 5)
      .forEach((t) => {
        items.push({
          id: `task-new-${t.id}`,
          type: 'task',
          username: nameMap[t.reporterId] || '未知',
          action: '创建了任务',
          target: t.title,
          time: t.createdAt,
          timestamp: new Date(t.createdAt).getTime(),
        });
      });

    // 按时间倒序排列，取前 12 条
    return items
      .sort((a, b) => b.timestamp - a.timestamp)
      .slice(0, 12);
  }, [tasks, bugs, members]);

  const formatTime = (timeStr: string) => {
    const time = new Date(timeStr);
    const now = new Date();
    const diffMs = now.getTime() - time.getTime();
    const diffMin = Math.floor(diffMs / 60000);
    const diffHour = Math.floor(diffMs / 3600000);
    const diffDay = Math.floor(diffMs / 86400000);

    if (diffMin < 1) return '刚刚';
    if (diffMin < 60) return `${diffMin} 分钟前`;
    if (diffHour < 24) return `${diffHour} 小时前`;
    if (diffDay < 7) return `${diffDay} 天前`;
    return `${time.getMonth() + 1}/${time.getDate()}`;
  };

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <Activity className="h-4 w-4 text-primary" />
          最近动态
        </CardTitle>
      </CardHeader>
      <CardContent>
        {activities.length > 0 ? (
          <div className="space-y-0">
            {activities.map((activity, i) => (
              <div key={activity.id} className="flex gap-3 py-2.5">
                {/* 时间线竖线 + 圆点 */}
                <div className="relative flex flex-col items-center">
                  <div
                    className={`z-10 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${
                      activity.type === 'bug'
                        ? 'bg-red-500/10 text-red-500'
                        : activity.action.includes('完成')
                        ? 'bg-green-500/10 text-green-500'
                        : 'bg-blue-500/10 text-blue-500'
                    }`}
                  >
                    {activity.type === 'bug' ? (
                      <AlertTriangle className="h-3.5 w-3.5" />
                    ) : activity.action.includes('完成') ? (
                      <CheckCircle2 className="h-3.5 w-3.5" />
                    ) : (
                      <ListTodo className="h-3.5 w-3.5" />
                    )}
                  </div>
                  {i < activities.length - 1 && (
                    <div className="absolute top-7 h-full w-px bg-border" />
                  )}
                </div>

                {/* 内容 */}
                <div className="flex-1 min-w-0">
                  <p className="text-sm">
                    <span className="font-medium">{activity.username}</span>
                    <span className="text-muted-foreground"> {activity.action} </span>
                    <span className="font-medium truncate" title={activity.target}>
                      {activity.target}
                    </span>
                  </p>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {formatTime(activity.time)}
                  </p>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="py-4 text-center text-sm text-muted-foreground">暂无动态</p>
        )}
      </CardContent>
    </Card>
  );
}

// ============================================
// 统计卡片组件
// ============================================
function StatCard({
  title,
  value,
  subtitle,
  icon,
  color,
}: {
  title: string;
  value: number;
  subtitle?: string;
  icon: React.ReactNode;
  color: string;
}) {
  const bgMap: Record<string, string> = {
    blue: 'bg-blue-50 dark:bg-blue-950/30',
    green: 'bg-green-50 dark:bg-green-950/30',
    amber: 'bg-amber-50 dark:bg-amber-950/30',
    red: 'bg-red-50 dark:bg-red-950/30',
  };

  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex items-center justify-between">
          <div className={`rounded-lg p-2 ${bgMap[color] || bgMap.blue}`}>{icon}</div>
          {subtitle && (
            <span className="text-xs font-medium text-muted-foreground">{subtitle}</span>
          )}
        </div>
        <div className="mt-3">
          <p className="text-2xl font-bold">{value}</p>
          <p className="text-xs text-muted-foreground">{title}</p>
        </div>
      </CardContent>
    </Card>
  );
}

// ============================================
// 任务分布组件
// ============================================
function TaskDistribution({
  data,
  tasks,
}: {
  data?: Record<string, number>;
  tasks: Array<{ status: string }>;
}) {
  const distribution = useMemo(() => {
    if (data) return data;

    const dist: Record<string, number> = { TODO: 0, DOING: 0, TESTING: 0, DONE: 0 };
    tasks.forEach((t) => {
      if (dist[t.status] !== undefined) dist[t.status]++;
    });
    return dist;
  }, [data, tasks]);

  const statusLabels: Record<string, { label: string; color: string }> = {
    TODO: { label: '待办', color: 'bg-slate-400' },
    DOING: { label: '进行中', color: 'bg-blue-500' },
    TESTING: { label: '测试中', color: 'bg-amber-500' },
    DONE: { label: '已完成', color: 'bg-green-500' },
  };

  const total = Object.values(distribution).reduce((a, b) => a + b, 0);

  if (total === 0) {
    return <p className="py-4 text-center text-sm text-muted-foreground">暂无任务数据</p>;
  }

  return (
    <div className="space-y-3">
      {Object.entries(distribution).map(([status, count]) => {
        const meta = statusLabels[status] || { label: status, color: 'bg-slate-400' };
        const pct = (count / total) * 100;

        return (
          <div key={status} className="space-y-1.5">
            <div className="flex justify-between text-sm">
              <span className="flex items-center gap-2">
                <span className={`h-2 w-2 rounded-full ${meta.color}`} />
                {meta.label}
              </span>
              <span className="text-muted-foreground">
                {count} ({pct.toFixed(0)}%)
              </span>
            </div>
            <div className="h-1.5 rounded-full bg-muted">
              <div
                className={`h-full rounded-full ${meta.color} transition-all`}
                style={{ width: `${pct}%` }}
              />
            </div>
          </div>
        );
      })}
    </div>
  );
}
