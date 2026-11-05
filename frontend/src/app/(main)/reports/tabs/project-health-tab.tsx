'use client';

import { useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useProjectHealth } from '@/lib/hooks/use-statistics';
import { useProjects } from '@/lib/hooks';
import {
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  PolarRadiusAxis,
  Radar,
  ResponsiveContainer,
  Tooltip,
} from 'recharts';
import {
  Activity,
  Loader2,
  CheckCircle2,
  AlertTriangle,
  TrendingUp,
  TrendingDown,
  Lightbulb,
  Download,
} from 'lucide-react';
import { statisticsApi } from '@/lib/api/statistics';

// ============================================
// 健康等级配置
// ============================================
const HEALTH_CONFIG = {
  EXCELLENT: { label: '优秀', color: 'text-green-600', bg: 'bg-green-500/10', borderColor: 'border-green-200' },
  GOOD: { label: '良好', color: 'text-blue-600', bg: 'bg-blue-500/10', borderColor: 'border-blue-200' },
  FAIR: { label: '一般', color: 'text-amber-600', bg: 'bg-amber-500/10', borderColor: 'border-amber-200' },
  POOR: { label: '较差', color: 'text-red-600', bg: 'bg-red-500/10', borderColor: 'border-red-200' },
};

// ============================================
// 项目健康度标签页
// ============================================
export default function ProjectHealthTab() {
  const { data: projectsRes } = useProjects({ page: 1, pageSize: 20 });
  const projects = projectsRes?.data?.records || [];
  const [selectedProjectId, setSelectedProjectId] = useState<string>(projects[0]?.id || '');

  const { data: healthRes, isLoading } = useProjectHealth(selectedProjectId || null);
  const health = healthRes?.data;

  const handleExport = () => {
    if (selectedProjectId) {
      statisticsApi.exportProjectExcel(selectedProjectId);
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
        {health && (
          <Button variant="outline" size="sm" onClick={handleExport} className="gap-1.5">
            <Download className="h-3.5 w-3.5" />
            导出报表
          </Button>
        )}
      </div>

      {isLoading ? (
        <div className="flex h-64 items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : !health ? (
        <div className="flex h-64 flex-col items-center justify-center gap-3 text-muted-foreground">
          <Activity className="h-8 w-8 opacity-50" />
          <p className="text-sm">请选择一个项目查看健康度</p>
        </div>
      ) : (
        <>
          {/* 综合评分卡片 */}
          <div className="grid gap-6 lg:grid-cols-3">
            <Card className={`lg:col-span-1 border-2 ${HEALTH_CONFIG[health.healthLevel].borderColor}`}>
              <CardContent className="flex flex-col items-center justify-center p-6">
                <div className={`rounded-full ${HEALTH_CONFIG[health.healthLevel].bg} p-6`}>
                  <span className={`text-4xl font-bold ${HEALTH_CONFIG[health.healthLevel].color}`}>
                    {health.overallScore}
                  </span>
                </div>
                <Badge className={`mt-3 ${HEALTH_CONFIG[health.healthLevel].bg} ${HEALTH_CONFIG[health.healthLevel].color}`}>
                  {HEALTH_CONFIG[health.healthLevel].label}
                </Badge>
                <p className="mt-2 text-sm font-medium">{health.projectName}</p>
                <p className="text-xs text-muted-foreground">综合健康评分</p>
              </CardContent>
            </Card>

            {/* 雷达图 */}
            <Card className="lg:col-span-2">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm font-medium">多维度健康评估</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width="100%" height={260}>
                  <RadarChart
                    data={health.dimensions.map((d) => ({
                      dimension: d.name,
                      score: d.score,
                      fullMark: 100,
                    }))}
                  >
                    <PolarGrid stroke="var(--border)" />
                    <PolarAngleAxis dataKey="dimension" tick={{ fontSize: 12 }} />
                    <PolarRadiusAxis angle={90} domain={[0, 100]} tick={{ fontSize: 10 }} />
                    <Radar
                      name="评分"
                      dataKey="score"
                      stroke="#3b82f6"
                      fill="#3b82f6"
                      fillOpacity={0.3}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: 'var(--card)',
                        border: '1px solid var(--border)',
                        borderRadius: '8px',
                        fontSize: '12px',
                      }}
                    />
                  </RadarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>
          </div>

          {/* 关键指标 */}
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <MetricCard
              label="任务完成率"
              value={`${health.metrics.taskCompletionRate.toFixed(1)}%`}
              trend={health.metrics.taskCompletionRate >= 70 ? 'up' : 'down'}
            />
            <MetricCard
              label="Bug 解决率"
              value={`${health.metrics.bugResolutionRate.toFixed(1)}%`}
              trend={health.metrics.bugResolutionRate >= 70 ? 'up' : 'down'}
            />
            <MetricCard
              label="需求完成率"
              value={`${health.metrics.requirementCompletionRate.toFixed(1)}%`}
              trend={health.metrics.requirementCompletionRate >= 60 ? 'up' : 'down'}
            />
            <MetricCard
              label="延期率"
              value={`${health.metrics.overdueRate.toFixed(1)}%`}
              trend={health.metrics.overdueRate <= 15 ? 'up' : 'down'}
              invertTrend
            />
          </div>

          {/* 各维度详情 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium">维度评分详情</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {health.dimensions.map((dim) => (
                  <div key={dim.key} className="flex items-center gap-4">
                    <span className="w-20 text-sm font-medium">{dim.name}</span>
                    <div className="flex-1">
                      <div className="flex items-center gap-2">
                        <div className="h-2 flex-1 rounded-full bg-muted">
                          <div
                            className={`h-full rounded-full transition-all ${
                              dim.score >= 80 ? 'bg-green-500' :
                              dim.score >= 60 ? 'bg-blue-500' :
                              dim.score >= 40 ? 'bg-amber-500' :
                              'bg-red-500'
                            }`}
                            style={{ width: `${dim.score}%` }}
                          />
                        </div>
                        <span className="w-12 text-right text-sm font-medium">{dim.score}分</span>
                      </div>
                      <p className="mt-0.5 text-xs text-muted-foreground">{dim.description}（权重 {dim.weight * 100}%）</p>
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>

          {/* 改进建议 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-sm font-medium">
                <Lightbulb className="h-4 w-4 text-amber-500" />
                改进建议
              </CardTitle>
            </CardHeader>
            <CardContent>
              <ul className="space-y-2">
                {health.suggestions.map((suggestion, i) => (
                  <li key={i} className="flex items-start gap-2 text-sm">
                    <span className="mt-0.5 flex h-5 w-5 items-center justify-center rounded-full bg-muted text-xs font-medium">
                      {i + 1}
                    </span>
                    {suggestion}
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

// ============================================
// 指标卡片
// ============================================
function MetricCard({
  label,
  value,
  trend,
  invertTrend = false,
}: {
  label: string;
  value: string;
  trend: 'up' | 'down';
  invertTrend?: boolean;
}) {
  const isPositive = invertTrend ? trend === 'down' : trend === 'up';

  return (
    <Card>
      <CardContent className="p-4">
        <div className="flex items-center justify-between">
          <p className="text-xs text-muted-foreground">{label}</p>
          {isPositive ? (
            <TrendingUp className="h-4 w-4 text-green-500" />
          ) : (
            <TrendingDown className="h-4 w-4 text-red-500" />
          )}
        </div>
        <p className="mt-1 text-xl font-bold">{value}</p>
      </CardContent>
    </Card>
  );
}
