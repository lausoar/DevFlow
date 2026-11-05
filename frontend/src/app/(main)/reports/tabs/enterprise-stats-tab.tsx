'use client';

import { useMemo } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { useEnterpriseStats } from '@/lib/hooks/use-statistics';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line,
  Legend,
} from 'recharts';
import {
  Loader2,
  FolderKanban,
  AlertCircle,
} from 'lucide-react';
import { useRouter } from 'next/navigation';

// ============================================
// 颜色配置
// ============================================
const COLORS = ['#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899'];

const TASK_STATUS_COLORS: Record<string, string> = {
  TODO: '#94a3b8',
  DOING: '#3b82f6',
  TESTING: '#f59e0b',
  DONE: '#10b981',
};

const BUG_SEVERITY_COLORS: Record<string, string> = {
  BLOCKER: '#dc2626',
  CRITICAL: '#ea580c',
  MAJOR: '#f59e0b',
  MINOR: '#3b82f6',
  TRIVIAL: '#94a3b8',
};

// ============================================
// 企业级统计标签页
// ============================================
export default function EnterpriseStatsTab() {
  const router = useRouter();
  const { data: res, isLoading } = useEnterpriseStats();
  const stats = res?.data;

  if (isLoading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (!stats) {
    return (
      <div className="flex h-64 flex-col items-center justify-center gap-3 text-muted-foreground">
        <AlertCircle className="h-8 w-8 opacity-50" />
        <p className="text-sm">暂无企业统计数据</p>
        <p className="text-xs">请先创建企业并添加项目</p>
      </div>
    );
  }

  // 任务趋势折线图数据
  const trendData = stats.taskTrend.map((point) => ({
    date: point.date.slice(5), // MM-DD
    新建: point.created,
    完成: point.completed,
  }));

  // 任务状态分布饼图数据
  const taskPieData = Object.entries(stats.taskStatusDistribution)
    .filter(([, count]) => count > 0)
    .map(([name, value]) => ({
      name: { TODO: '待办', DOING: '进行中', TESTING: '测试中', DONE: '已完成' }[name] || name,
      value,
      color: TASK_STATUS_COLORS[name] || '#94a3b8',
    }));

  // Bug 严重程度分布
  const bugBarData = Object.entries(stats.bugSeverityDistribution)
    .map(([name, count]) => ({
      name: { BLOCKER: '阻断', CRITICAL: '严重', MAJOR: '主要', MINOR: '次要', TRIVIAL: '微小' }[name] || name,
      count,
      color: BUG_SEVERITY_COLORS[name] || '#94a3b8',
    }));

  return (
    <div className="space-y-6">
      {/* 图表行 1：趋势 + 任务分布 */}
      <div className="grid gap-6 lg:grid-cols-2">
        {/* 任务趋势折线图 */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">任务趋势（近 14 天）</CardTitle>
          </CardHeader>
          <CardContent>
            {trendData.length > 0 ? (
              <ResponsiveContainer width="100%" height={240}>
                <LineChart data={trendData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                  <XAxis dataKey="date" tick={{ fontSize: 11 }} stroke="var(--muted-foreground)" />
                  <YAxis tick={{ fontSize: 11 }} stroke="var(--muted-foreground)" />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: 'var(--card)',
                      border: '1px solid var(--border)',
                      borderRadius: '8px',
                      fontSize: '12px',
                    }}
                  />
                  <Legend wrapperStyle={{ fontSize: '12px' }} />
                  <Line type="monotone" dataKey="新建" stroke="#3b82f6" strokeWidth={2} dot={false} />
                  <Line type="monotone" dataKey="完成" stroke="#10b981" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            ) : (
              <p className="py-8 text-center text-sm text-muted-foreground">暂无趋势数据</p>
            )}
          </CardContent>
        </Card>

        {/* 任务状态分布饼图 */}
        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-sm font-medium">任务状态分布</CardTitle>
          </CardHeader>
          <CardContent>
            {taskPieData.length > 0 ? (
              <div className="flex items-center gap-4">
                <ResponsiveContainer width="50%" height={200}>
                  <PieChart>
                    <Pie
                      data={taskPieData}
                      cx="50%"
                      cy="50%"
                      innerRadius={40}
                      outerRadius={70}
                      dataKey="value"
                      stroke="none"
                    >
                      {taskPieData.map((entry, index) => (
                        <Cell key={index} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'var(--card)',
                        border: '1px solid var(--border)',
                        borderRadius: '8px',
                        fontSize: '12px',
                      }}
                    />
                  </PieChart>
                </ResponsiveContainer>
                <div className="flex-1 space-y-2">
                  {taskPieData.map((item) => (
                    <div key={item.name} className="flex items-center justify-between text-sm">
                      <span className="flex items-center gap-2">
                        <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: item.color }} />
                        {item.name}
                      </span>
                      <span className="font-medium">{item.value}</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <p className="py-8 text-center text-sm text-muted-foreground">暂无任务数据</p>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 图表行 2：Bug 分布 */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium">Bug 严重程度分布</CardTitle>
        </CardHeader>
        <CardContent>
          {bugBarData.some((d) => d.count > 0) ? (
            <ResponsiveContainer width="100%" height={200}>
              <BarChart data={bugBarData}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="name" tick={{ fontSize: 11 }} stroke="var(--muted-foreground)" />
                <YAxis tick={{ fontSize: 11 }} stroke="var(--muted-foreground)" />
                <Tooltip
                  contentStyle={{
                    backgroundColor: 'var(--card)',
                    border: '1px solid var(--border)',
                    borderRadius: '8px',
                    fontSize: '12px',
                  }}
                />
                <Bar dataKey="count" radius={[4, 4, 0, 0]}>
                  {bugBarData.map((entry, index) => (
                    <Cell key={index} fill={entry.color} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">暂无 Bug 数据</p>
          )}
        </CardContent>
      </Card>

      {/* 项目进度列表 */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-sm font-medium">各项目进度</CardTitle>
        </CardHeader>
        <CardContent>
          {stats.projectProgressList.length > 0 ? (
            <div className="space-y-4">
              {stats.projectProgressList.map((project) => (
                <div
                  key={project.projectId}
                  className="cursor-pointer rounded-lg border p-3 transition-colors hover:bg-muted/50"
                  onClick={() => router.push(`/projects/${project.projectId}`)}
                >
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-2">
                      <div className="flex h-7 w-7 items-center justify-center rounded bg-brand/10 text-xs font-bold text-brand">
                        {project.projectKey}
                      </div>
                      <span className="text-sm font-medium">{project.projectName}</span>
                    </div>
                    <div className="flex items-center gap-3 text-xs text-muted-foreground">
                      <span>{project.memberCount} 成员</span>
                      <span>{project.completedTasks}/{project.totalTasks} 任务</span>
                      <Badge
                        variant="secondary"
                        className={
                          project.status === 'ACTIVE'
                            ? 'bg-green-500/10 text-green-600'
                            : project.status === 'PLANNING'
                            ? 'bg-amber-500/10 text-amber-600'
                            : 'bg-gray-500/10 text-gray-500'
                        }
                      >
                        {project.status === 'ACTIVE' ? '进行中' : project.status === 'PLANNING' ? '规划中' : '已归档'}
                      </Badge>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <Progress value={project.progress} className="h-1.5 flex-1" />
                    <span className="text-xs font-medium text-muted-foreground w-10 text-right">
                      {project.progress.toFixed(0)}%
                    </span>
                  </div>
                </div>
              ))}
            </div>
          ) : (
            <p className="py-8 text-center text-sm text-muted-foreground">暂无项目数据</p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
