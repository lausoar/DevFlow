'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useMemberWorkload } from '@/lib/hooks/use-statistics';
import { useProjects } from '@/lib/hooks';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
} from 'recharts';
import {
  Users,
  Loader2,
  Download,
  Scale,
  AlertTriangle,
  TrendingUp,
} from 'lucide-react';
import { statisticsApi } from '@/lib/api/statistics';

// ============================================
// 负载等级配置
// ============================================
const LOAD_CONFIG = {
  HIGH: { label: '高', color: 'text-red-600', bg: 'bg-red-500/10' },
  MEDIUM: { label: '中', color: 'text-amber-600', bg: 'bg-amber-500/10' },
  LOW: { label: '低', color: 'text-green-600', bg: 'bg-green-500/10' },
};

// ============================================
// 成员工作量标签页
// ============================================
export default function MemberWorkloadTab() {
  const { data: projectsRes } = useProjects({ page: 1, pageSize: 20 });
  const projects = projectsRes?.data?.records || [];
  const [selectedProjectId, setSelectedProjectId] = useState<string>(projects[0]?.id || '');

  const { data: workloadRes, isLoading } = useMemberWorkload(selectedProjectId || null);
  const workload = workloadRes?.data;

  const handleExport = () => {
    if (selectedProjectId) {
      statisticsApi.exportWorkloadExcel(selectedProjectId);
    }
  };

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
        {workload && (
          <Button variant="outline" size="sm" onClick={handleExport} className="gap-1.5">
            <Download className="h-3.5 w-3.5" />
            导出工作量
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="flex h-64 items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : !workload || workload.members.length === 0 ? (
        <div className="flex h-64 flex-col items-center justify-center gap-3 text-muted-foreground">
          <Users className="h-8 w-8 opacity-50" />
          <p className="text-sm">暂无成员工作量数据</p>
        </div>
      ) : (
        <>
          {/* 汇总信息 */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">团队成员</p>
                <p className="text-2xl font-bold">{workload.members.length}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">平均任务/人</p>
                <p className="text-2xl font-bold">{workload.summary.avgTasksPerMember.toFixed(1)}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">平均完成率</p>
                <p className="text-2xl font-bold">{workload.summary.avgCompletionRate.toFixed(1)}%</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4">
                <div className="flex items-center gap-2">
                  <p className="text-xs text-muted-foreground">负载均衡</p>
                  {workload.summary.balanced ? (
                    <Badge className="bg-green-500/10 text-green-600 text-[10px]">均衡</Badge>
                  ) : (
                    <Badge className="bg-amber-500/10 text-amber-600 text-[10px]">
                      <AlertTriangle className="mr-1 h-3 w-3" />
                      不均衡
                    </Badge>
                  )}
                </div>
                <p className="mt-1 text-xs text-muted-foreground">
                  最高: {workload.summary.maxLoadMember} | 最低: {workload.summary.minLoadMember}
                </p>
              </CardContent>
            </Card>
          </div>

          {/* 工作量对比柱状图 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium">成员任务量对比</CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={280}>
                <BarChart
                  data={workload.members.map((m) => ({
                    name: m.username,
                    待办: m.todoTasks,
                    进行中: m.inProgressTasks,
                    已完成: m.completedTasks,
                  }))}
                >
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
                  <Legend wrapperStyle={{ fontSize: '12px' }} />
                  <Bar dataKey="待办" stackId="a" fill="#94a3b8" radius={[0, 0, 0, 0]} />
                  <Bar dataKey="进行中" stackId="a" fill="#3b82f6" radius={[0, 0, 0, 0]} />
                  <Bar dataKey="已完成" stackId="a" fill="#10b981" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>

          {/* 成员详情列表 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium">成员详情</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {workload.members
                  .sort((a, b) => b.totalTasks - a.totalTasks)
                  .map((member) => {
                    const loadConfig = LOAD_CONFIG[member.loadLevel as keyof typeof LOAD_CONFIG] || LOAD_CONFIG.MEDIUM;
                    return (
                      <div
                        key={member.userId}
                        className="rounded-lg border p-3 transition-colors hover:bg-muted/50"
                      >
                        <div className="flex items-center gap-3">
                          {/* 头像 */}
                          <Avatar className="h-9 w-9">
                            <AvatarImage src={member.avatar} />
                            <AvatarFallback className="text-xs">
                              {member.username?.charAt(0)?.toUpperCase() || 'U'}
                            </AvatarFallback>
                          </Avatar>

                          {/* 基本信息 */}
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <span className="text-sm font-medium">{member.username}</span>
                              <Badge className={`${loadConfig.bg} ${loadConfig.color} text-[10px]`}>
                                {loadConfig.label}负载
                              </Badge>
                              {member.overdueTasks > 0 && (
                                <Badge className="bg-red-500/10 text-red-600 text-[10px]">
                                  {member.overdueTasks} 延期
                                </Badge>
                              )}
                            </div>
                            <div className="mt-1 flex items-center gap-4 text-xs text-muted-foreground">
                              <span>任务: {member.totalTasks}</span>
                              <span>Bug: {member.bugCount}</span>
                              <span>紧急: {member.criticalTasks}</span>
                            </div>
                          </div>

                          {/* 完成率进度条 */}
                          <div className="w-32">
                            <div className="flex justify-between text-xs mb-1">
                              <span className="text-muted-foreground">完成率</span>
                              <span className="font-medium">{member.completionRate.toFixed(0)}%</span>
                            </div>
                            <Progress value={member.completionRate} className="h-1.5" />
                          </div>
                        </div>
                      </div>
                    );
                  })}
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
