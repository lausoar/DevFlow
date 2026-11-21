'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Button } from '@/components/ui/button';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  BarChart3,
  Activity,
  Users,
  Flame,
  GanttChartSquare,
  Download,
  TrendingUp,
  TrendingDown,
  CheckCircle2,
  AlertTriangle,
  FolderKanban,
  Bug,
  FileText,
  Target,
  Loader2,
} from 'lucide-react';
import { useEnterpriseStats } from '@/lib/hooks/use-statistics';
import { useAuthStore } from '@/stores/auth-store';
import { exportApi } from '@/lib/api/export';
import { useProjects } from '@/lib/hooks';
import EnterpriseStatsTab from './tabs/enterprise-stats-tab';
import ProjectHealthTab from './tabs/project-health-tab';
import MemberWorkloadTab from './tabs/member-workload-tab';
import BurndownTab from './tabs/burndown-tab';
import GanttTab from './tabs/gantt-tab';

// ============================================
// 统计报表内容组件
// ============================================
export default function ReportsContent() {
  const [activeTab, setActiveTab] = useState('enterprise');
  const { data: enterpriseRes, isLoading } = useEnterpriseStats();
  const enterprise = enterpriseRes?.data;
  const enterpriseId = useAuthStore((state) => state.user?.enterpriseId);

  const tabs = [
    { key: 'enterprise', label: '企业统计', icon: BarChart3 },
    { key: 'health', label: '项目健康度', icon: Activity },
    { key: 'workload', label: '成员工作量', icon: Users },
    { key: 'burndown', label: '燃尽图', icon: Flame },
    { key: 'gantt', label: '甘特图', icon: GanttChartSquare },
  ];

  return (
    <div className="space-y-6">
      {/* 页面标题 */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-headline text-ink">统计报表</h1>
          <p className="text-[13px] text-ink-subtle">
            企业级数据统计、项目健康度评估、成员工作量分析
          </p>
        </div>
        {enterprise && (
          <Button
            variant="outline"
            className="gap-1.5"
            onClick={() => {
              if (!enterpriseId) return;
              exportApi.downloadEnterpriseSummary(enterpriseId).catch(() => {
                alert('导出失败，请稍后重试');
              });
            }}
          >
            导出 CSV
          </Button>
        )}
      </div>

      {/* 概览卡片（企业级） */}
      {enterprise && (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <OverviewCard
            title="项目总数"
            value={enterprise.projectOverview.totalProjects}
            subtitle={`${enterprise.projectOverview.activeProjects} 个进行中`}
            icon={<FolderKanban className="h-4 w-4 text-info" />}
            color="blue"
          />
          <OverviewCard
            title="任务完成率"
            value={`${enterprise.taskOverview.completionRate.toFixed(1)}%`}
            subtitle={`${enterprise.taskOverview.completedTasks} / ${enterprise.taskOverview.totalTasks}`}
            icon={<CheckCircle2 className="h-4 w-4 text-success" />}
            color="green"
          />
          <OverviewCard
            title="Bug 解决率"
            value={`${enterprise.bugOverview.resolutionRate.toFixed(1)}%`}
            subtitle={`${enterprise.bugOverview.openBugs} 个待处理`}
            icon={<Bug className="h-4 w-4 text-warning" />}
            color="amber"
          />
          <OverviewCard
            title="需求完成率"
            value={`${enterprise.requirementOverview.completionRate.toFixed(1)}%`}
            subtitle={`${enterprise.requirementOverview.openRequirements} 个活跃中`}
            icon={<FileText className="h-4 w-4 text-purple-500" />}
            color="purple"
          />
        </div>
      )}

      {/* 标签页 */}
      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList className="grid w-full grid-cols-5 lg:w-auto lg:inline-grid">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            return (
              <TabsTrigger key={tab.key} value={tab.key} className="gap-1.5 text-xs lg:text-sm">
                <Icon className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">{tab.label}</span>
              </TabsTrigger>
            );
          })}
        </TabsList>

        <TabsContent value="enterprise" className="mt-4">
          <EnterpriseStatsTab />
        </TabsContent>
        <TabsContent value="health" className="mt-4">
          <ProjectHealthTab />
        </TabsContent>
        <TabsContent value="workload" className="mt-4">
          <MemberWorkloadTab />
        </TabsContent>
        <TabsContent value="burndown" className="mt-4">
          <BurndownTab />
        </TabsContent>
        <TabsContent value="gantt" className="mt-4">
          <GanttTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

// ============================================
// 概览卡片组件
// ============================================
function OverviewCard({
  title,
  value,
  subtitle,
  icon,
  color,
}: {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: React.ReactNode;
  color: string;
}) {
  const bgMap: Record<string, string> = {
    blue: 'bg-info-muted ',
    green: 'bg-success-muted ',
    amber: 'bg-warning-muted ',
    purple: 'bg-purple-50 ',
    red: 'bg-danger-muted ',
  };

  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex items-center justify-between">
          <div className={`rounded-lg p-2 ${bgMap[color] || bgMap.blue}`}>{icon}</div>
        </div>
        <div className="mt-3">
          <p className="text-2xl font-bold">{value}</p>
          <p className="text-xs text-muted-foreground">{title}</p>
          {subtitle && (
            <p className="mt-0.5 text-[11px] text-muted-foreground">{subtitle}</p>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
