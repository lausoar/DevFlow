'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useSprintBurndown } from '@/lib/hooks/use-statistics';
import { useProjects } from '@/lib/hooks';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
  ReferenceLine,
} from 'recharts';
import {
  Flame,
  Loader2,
  TrendingUp,
  Calendar,
  Target,
  CheckCircle2,
} from 'lucide-react';

// ============================================
// Sprint 燃尽图标签页
// ============================================
export default function BurndownTab() {
  const { data: projectsRes } = useProjects({ page: 1, pageSize: 20 });
  const projects = projectsRes?.data?.records || [];
  const [selectedProjectId, setSelectedProjectId] = useState<string>(projects[0]?.id || '');

  const { data: burndownRes, isLoading } = useSprintBurndown(selectedProjectId || null);
  const burndown = burndownRes?.data;

  // 构建图表数据
  const chartData = burndown?.dataPoints.map((point) => ({
    date: point.date.slice(5), // MM-DD
    理想剩余: point.idealRemaining,
    实际剩余: point.actualRemaining,
    当日完成: point.completedPoints,
  })) || [];

  return (
    <div className="space-y-6">
      {/* 项目选择器 */}
      <div className="flex flex-wrap items-center gap-3">
        <Select value={selectedProjectId} onValueChange={(v) => { if (v !== null) setSelectedProjectId(v); }}>
          <SelectTrigger className="w-full sm:w-64">
            <SelectValue placeholder="选择项目" />
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

      {isLoading ? (
        <div className="flex h-64 items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : !burndown || burndown.dataPoints.length === 0 ? (
        <div className="flex h-64 flex-col items-center justify-center gap-3 text-muted-foreground">
          <Flame className="h-8 w-8 opacity-50" />
          <p className="text-sm">暂无燃尽图数据</p>
          <p className="text-xs">请确保项目有进行中的 Sprint 且任务已设置故事点</p>
        </div>
      ) : (
        <>
          {/* Sprint 概览 */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardContent className="p-4">
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <Target className="h-3.5 w-3.5" />
                  Sprint
                </div>
                <p className="mt-1 text-lg font-bold">{burndown.sprintName}</p>
                <Badge
                  className={`mt-1 text-[10px] ${
                    burndown.sprintStatus === 'ACTIVE'
                      ? 'bg-blue-500/10 text-blue-600'
                      : burndown.sprintStatus === 'COMPLETED'
                      ? 'bg-green-500/10 text-green-600'
                      : 'bg-gray-500/10 text-gray-600'
                  }`}
                >
                  {burndown.sprintStatus === 'ACTIVE' ? '进行中' : burndown.sprintStatus === 'COMPLETED' ? '已完成' : '规划中'}
                </Badge>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">总故事点</p>
                <p className="mt-1 text-2xl font-bold">{burndown.totalStoryPoints}</p>
                <p className="text-xs text-muted-foreground">{burndown.totalTasks} 个任务</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">燃尽速率</p>
                <p className="mt-1 text-2xl font-bold">{burndown.velocity.toFixed(1)}</p>
                <p className="text-xs text-muted-foreground">故事点/天</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">预计完成</p>
                <p className="mt-1 text-lg font-bold">
                  {burndown.estimatedCompletionDate
                    ? burndown.estimatedCompletionDate.slice(5)
                    : '-'}
                </p>
                <Badge
                  className={`mt-1 text-[10px] ${
                    burndown.onTrack
                      ? 'bg-green-500/10 text-green-600'
                      : 'bg-amber-500/10 text-amber-600'
                  }`}
                >
                  {burndown.onTrack ? '进度正常' : '可能延期'}
                </Badge>
              </CardContent>
            </Card>
          </div>

          {/* 燃尽图 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-sm font-medium">
                <Flame className="h-4 w-4 text-orange-500" />
                Sprint 燃尽图
              </CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={320}>
                <AreaChart data={chartData}>
                  <defs>
                    <linearGradient id="idealGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#94a3b8" stopOpacity={0.1} />
                      <stop offset="95%" stopColor="#94a3b8" stopOpacity={0} />
                    </linearGradient>
                    <linearGradient id="actualGradient" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor="#3b82f6" stopOpacity={0.3} />
                      <stop offset="95%" stopColor="#3b82f6" stopOpacity={0} />
                    </linearGradient>
                  </defs>
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
                  <Area
                    type="monotone"
                    dataKey="理想剩余"
                    stroke="#94a3b8"
                    strokeWidth={2}
                    strokeDasharray="5 5"
                    fill="url(#idealGradient)"
                  />
                  <Area
                    type="monotone"
                    dataKey="实际剩余"
                    stroke="#3b82f6"
                    strokeWidth={2}
                    fill="url(#actualGradient)"
                  />
                </AreaChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>

          {/* 每日完成详情 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium">每日完成情况</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-2">
                {burndown.dataPoints
                  .filter((p) => p.completedTasks > 0)
                  .map((point) => (
                    <div key={point.date} className="flex items-center gap-4 text-sm">
                      <span className="w-20 text-muted-foreground">{point.date.slice(5)}</span>
                      <div className="flex items-center gap-2 flex-1">
                        <CheckCircle2 className="h-4 w-4 text-green-500" />
                        <span>
                          完成 <strong>{point.completedTasks}</strong> 个任务，
                          <strong>{point.completedPoints}</strong> 个故事点
                        </span>
                      </div>
                      <span className="text-xs text-muted-foreground">
                        剩余 {point.actualRemaining} 点
                      </span>
                    </div>
                  ))}
                {burndown.dataPoints.filter((p) => p.completedTasks > 0).length === 0 && (
                  <p className="py-4 text-center text-sm text-muted-foreground">
                    暂无完成记录
                  </p>
                )}
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
