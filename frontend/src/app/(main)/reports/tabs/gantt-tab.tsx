'use client';

import { useState, useMemo } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useProjectGantt } from '@/lib/hooks/use-statistics';
import { useProjects } from '@/lib/hooks';
import {
  GanttChartSquare,
  Loader2,
  AlertCircle,
  Clock,
  CheckCircle2,
  Play,
  Calendar,
} from 'lucide-react';

// ============================================
// 状态配置
// ============================================
const STATUS_CONFIG: Record<string, { label: string; color: string; bg: string }> = {
  TODO: { label: '待办', color: 'text-slate-600', bg: 'bg-slate-200' },
  DOING: { label: '进行中', color: 'text-blue-600', bg: 'bg-blue-400' },
  TESTING: { label: '测试中', color: 'text-amber-600', bg: 'bg-amber-400' },
  DONE: { label: '已完成', color: 'text-green-600', bg: 'bg-green-400' },
};

const fmtDate = (d: string) =>
  new Date(d).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });

const PRIORITY_COLORS: Record<string, string> = {
  CRITICAL: '#dc2626',
  HIGH: '#f97316',
  MEDIUM: '#eab308',
  LOW: '#94a3b8',
};

// ============================================
// 甘特图标签页
// ============================================
export default function GanttTab() {
  const { data: projectsRes } = useProjects({ page: 1, pageSize: 20 });
  const projects = projectsRes?.data?.records || [];
  const [selectedProjectId, setSelectedProjectId] = useState<string>(projects[0]?.id || '');

  const { data: ganttRes, isLoading } = useProjectGantt(selectedProjectId || null);
  const gantt = ganttRes?.data;

  // 计算甘特图时间范围
  const { minDate, maxDate, totalDays, dayWidth } = useMemo(() => {
    if (!gantt || gantt.tasks.length === 0) {
      return { minDate: new Date(), maxDate: new Date(), totalDays: 30, dayWidth: 30 };
    }

    const dates = gantt.tasks.flatMap((t) => [new Date(t.startDate), new Date(t.endDate)]);
    const min = new Date(Math.min(...dates.map((d) => d.getTime())));
    const max = new Date(Math.max(...dates.map((d) => d.getTime())));

    // 添加边距
    min.setDate(min.getDate() - 2);
    max.setDate(max.getDate() + 2);

    const days = Math.ceil((max.getTime() - min.getTime()) / (1000 * 60 * 60 * 24));
    const width = Math.max(24, Math.min(40, 800 / days));

    return { minDate: min, maxDate: max, totalDays: days, dayWidth: width };
  }, [gantt]);

  // 获取任务在甘特图上的位置
  const getTaskPosition = (startDate: string, endDate: string) => {
    const start = new Date(startDate);
    const end = new Date(endDate);
    const left = ((start.getTime() - minDate.getTime()) / (1000 * 60 * 60 * 24)) * dayWidth;
    const width = Math.max(dayWidth, ((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) * dayWidth);
    return { left, width };
  };

  // 生成日期刻度
  const dateMarkers = useMemo(() => {
    const markers: Array<{ date: Date; label: string; isWeekend: boolean }> = [];
    for (let i = 0; i <= totalDays; i++) {
      const date = new Date(minDate);
      date.setDate(date.getDate() + i);
      const dayOfWeek = date.getDay();
      markers.push({
        date,
        label: `${date.getMonth() + 1}/${date.getDate()}`,
        isWeekend: dayOfWeek === 0 || dayOfWeek === 6,
      });
    }
    return markers;
  }, [minDate, totalDays]);

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
      ) : !gantt || gantt.tasks.length === 0 ? (
        <div className="flex h-64 flex-col items-center justify-center gap-3 text-muted-foreground">
          <GanttChartSquare className="h-8 w-8 opacity-50" />
          <p className="text-sm">暂无甘特图数据</p>
          <p className="text-xs">请先创建迭代并分配任务</p>
        </div>
      ) : (
        <>
          {/* 概览 */}
          <div className="grid gap-4 sm:grid-cols-3">
            <Card>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">总任务</p>
                <p className="text-2xl font-bold">{gantt.tasks.length}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">迭代数</p>
                <p className="text-2xl font-bold">{gantt.sprintGroups.length}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4">
                <p className="text-xs text-muted-foreground">延期任务</p>
                <p className="text-2xl font-bold text-red-500">
                  {gantt.tasks.filter((t) => t.overdue).length}
                </p>
              </CardContent>
            </Card>
          </div>

          {/* 甘特图 */}
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="flex items-center gap-2 text-sm font-medium">
                <GanttChartSquare className="h-4 w-4 text-purple-500" />
                项目甘特图
              </CardTitle>
            </CardHeader>
            <CardContent>
              {/* 手机端：简化列表视图 */}
              <div className="space-y-2 md:hidden">
                {gantt.tasks.map((task) => {
                  const sc = STATUS_CONFIG[task.status] || STATUS_CONFIG.TODO;
                  return (
                    <div key={task.id} className="rounded-lg border p-3">
                      <div className="flex items-center justify-between gap-2">
                        <span className="truncate text-sm font-medium">{task.title}</span>
                        {task.overdue && <AlertCircle className="h-4 w-4 shrink-0 text-red-500" />}
                      </div>
                      <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
                        <span>{task.assigneeName}</span>
                        <span>·</span>
                        <span>
                          {fmtDate(task.startDate)} ~ {fmtDate(task.endDate)}
                        </span>
                      </div>
                      <div className="mt-2 flex items-center gap-2">
                        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                          <div
                            className="h-full rounded-full"
                            style={{
                              width: `${task.progress}%`,
                              backgroundColor: sc.bg.replace('200', '500').replace('400', '600'),
                            }}
                          />
                        </div>
                        <span className="text-xs text-muted-foreground">{task.progress}%</span>
                      </div>
                    </div>
                  );
                })}
              </div>
              {/* 桌面端：甘特横滚视图 */}
              <div className="hidden md:block overflow-x-auto">
                <div className="min-w-[800px]">
                  {/* 日期刻度 */}
                  <div className="flex border-b pb-2 mb-2">
                    <div className="w-48 shrink-0 text-xs font-medium text-muted-foreground">
                      任务
                    </div>
                    <div className="relative flex-1">
                      <div className="flex">
                        {dateMarkers
                          .filter((_, i) => i % Math.max(1, Math.floor(totalDays / 15)) === 0)
                          .map((marker, i) => (
                            <div
                              key={i}
                              className="text-center text-[10px] text-muted-foreground"
                              style={{
                                width: `${dayWidth * Math.max(1, Math.floor(totalDays / 15))}px`,
                              }}
                            >
                              {marker.label}
                            </div>
                          ))}
                      </div>
                    </div>
                  </div>

                  {/* Sprint 分组 */}
                  {gantt.sprintGroups.length > 0 ? (
                    gantt.sprintGroups.map((sprint) => {
                      const sprintTasks = gantt.tasks.filter((t) => t.sprintId === sprint.id);
                      if (sprintTasks.length === 0) return null;

                      return (
                        <div key={sprint.id} className="mb-4">
                          {/* Sprint 标题 */}
                          <div className="flex items-center gap-2 mb-2">
                            <Badge
                              className={
                                sprint.status === 'ACTIVE'
                                  ? 'bg-blue-500/10 text-blue-600'
                                  : sprint.status === 'COMPLETED'
                                  ? 'bg-green-500/10 text-green-600'
                                  : 'bg-gray-500/10 text-gray-600'
                              }
                            >
                              {sprint.name}
                            </Badge>
                            <span className="text-xs text-muted-foreground">
                              {sprintTasks.length} 个任务
                            </span>
                          </div>

                          {/* Sprint 内的任务 */}
                          <div className="space-y-1.5">
                            {sprintTasks.map((task) => {
                              const pos = getTaskPosition(task.startDate, task.endDate);
                              const statusConfig = STATUS_CONFIG[task.status] || STATUS_CONFIG.TODO;

                              return (
                                <div key={task.id} className="flex items-center gap-2">
                                  {/* 任务名称 */}
                                  <div className="w-48 shrink-0 flex items-center gap-2">
                                    <div
                                      className="h-2 w-2 rounded-full shrink-0"
                                      style={{ backgroundColor: PRIORITY_COLORS[task.priority] || '#94a3b8' }}
                                    />
                                    <span className="text-xs truncate" title={task.title}>
                                      {task.title}
                                    </span>
                                    {task.overdue && (
                                      <AlertCircle className="h-3 w-3 text-red-500 shrink-0" />
                                    )}
                                  </div>

                                  {/* 甘特条 */}
                                  <div className="relative flex-1 h-6 bg-muted/30 rounded">
                                    {/* 周末背景 */}
                                    {dateMarkers
                                      .filter((m) => m.isWeekend)
                                      .map((m, i) => (
                                        <div
                                          key={i}
                                          className="absolute top-0 h-full bg-muted/50"
                                          style={{
                                            left: `${((m.date.getTime() - minDate.getTime()) / (1000 * 60 * 60 * 24)) * dayWidth}px`,
                                            width: `${dayWidth}px`,
                                          }}
                                        />
                                      ))}

                                    {/* 任务条 */}
                                    <div
                                      className={`absolute top-1 h-4 rounded-sm ${statusConfig.bg} transition-all`}
                                      style={{
                                        left: `${pos.left}px`,
                                        width: `${pos.width}px`,
                                      }}
                                      title={`${task.title} (${task.assigneeName})`}
                                    >
                                      {/* 进度覆盖 */}
                                      <div
                                        className="h-full rounded-sm opacity-80"
                                        style={{
                                          width: `${task.progress}%`,
                                          backgroundColor: statusConfig.bg.replace('200', '500').replace('400', '600'),
                                        }}
                                      />
                                    </div>
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        </div>
                      );
                    })
                  ) : (
                    /* 没有 Sprint 分组时，直接显示所有任务 */
                    <div className="space-y-1.5">
                      {gantt.tasks.map((task) => {
                        const pos = getTaskPosition(task.startDate, task.endDate);
                        const statusConfig = STATUS_CONFIG[task.status] || STATUS_CONFIG.TODO;

                        return (
                          <div key={task.id} className="flex items-center gap-2">
                            <div className="w-48 shrink-0 flex items-center gap-2">
                              <div
                                className="h-2 w-2 rounded-full shrink-0"
                                style={{ backgroundColor: PRIORITY_COLORS[task.priority] || '#94a3b8' }}
                              />
                              <span className="text-xs truncate" title={task.title}>
                                {task.title}
                              </span>
                            </div>
                            <div className="relative flex-1 h-6 bg-muted/30 rounded">
                              <div
                                className={`absolute top-1 h-4 rounded-sm ${statusConfig.bg}`}
                                style={{
                                  left: `${pos.left}px`,
                                  width: `${pos.width}px`,
                                }}
                              >
                                <div
                                  className="h-full rounded-sm opacity-80"
                                  style={{
                                    width: `${task.progress}%`,
                                    backgroundColor: statusConfig.bg.replace('200', '500').replace('400', '600'),
                                  }}
                                />
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>

          {/* 图例 */}
          <Card>
            <CardContent className="p-4">
              <div className="flex flex-wrap items-center gap-4 text-xs">
                <span className="text-muted-foreground">状态：</span>
                {Object.entries(STATUS_CONFIG).map(([key, config]) => (
                  <span key={key} className="flex items-center gap-1.5">
                    <span className={`h-3 w-6 rounded-sm ${config.bg}`} />
                    {config.label}
                  </span>
                ))}
                <span className="ml-4 text-muted-foreground">优先级：</span>
                {Object.entries(PRIORITY_COLORS).map(([key, color]) => (
                  <span key={key} className="flex items-center gap-1.5">
                    <span className="h-2 w-2 rounded-full" style={{ backgroundColor: color }} />
                    {key}
                  </span>
                ))}
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
