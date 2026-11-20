'use client';

import { useMemo, useState } from 'react';
import {
  Scatter,
  ScatterChart,
  CartesianGrid,
  XAxis,
  YAxis,
  ZAxis,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
} from 'recharts';
import { Badge } from '@/components/ui/badge';
import type { Requirement } from '@/types';

interface QuadrantPoint {
  x: number;
  y: number;
  z: number;
  name: string;
  id: string;
  effort: number;
  score: number;
  priority: string;
}

const QUADRANTS = [
  { label: '优先做', hint: '价值高、成本低', className: 'text-success' },
  { label: '规划做', hint: '价值高、成本高', className: 'text-warning' },
  { label: '顺手做', hint: '价值低、成本低', className: 'text-muted-foreground' },
  { label: '先搁置', hint: '价值低、成本高', className: 'text-muted-foreground' },
];

/** 分数的中位数作为象限分界；空集合按 0 处理 */
function median(values: number[]): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 0 ? (sorted[mid - 1] + sorted[mid]) / 2 : sorted[mid];
}

/**
 * 需求价值/成本四象限。
 *
 * 横轴工作量（人日）、纵轴 RICE 分数：右上"优先做"、左上"规划做"、
 * 右下"顺手做"、左下"先搁置"。分界线取当前集合中位数，而不是写死阈值——
 * 不同项目的绝对值不可比，相对位置才是决策依据。
 */
export function RequirementScoreQuadrant({ requirements }: { requirements: Requirement[] }) {
  const [selected, setSelected] = useState<string | null>(null);

  const scored = useMemo(
    () => requirements.filter((r) => r.riceScore != null && r.effort != null),
    [requirements]
  );
  const unscored = requirements.length - scored.length;

  const points: QuadrantPoint[] = useMemo(
    () => scored.map((r) => ({
      x: Number(r.effort ?? 0),
      y: Number(r.riceScore ?? 0),
      z: 80,
      name: r.title,
      id: r.id,
      effort: Number(r.effort ?? 0),
      score: Number(r.riceScore ?? 0),
      priority: r.priority,
    })),
    [scored]
  );

  const midX = median(points.map((p) => p.x));
  const midY = median(points.map((p) => p.y));
  const active = points.find((p) => p.id === selected) ?? null;

  const counts = useMemo(() => {
    const result = [0, 0, 0, 0];
    for (const p of points) {
      if (p.y >= midY && p.x <= midX) result[0] += 1;
      else if (p.y >= midY) result[1] += 1;
      else if (p.x <= midX) result[2] += 1;
      else result[3] += 1;
    }
    return result;
  }, [points, midX, midY]);

  if (requirements.length === 0) {
    return <p className="py-12 text-center text-sm text-muted-foreground">暂无需求</p>;
  }

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {QUADRANTS.map((quadrant, i) => (
          <div key={quadrant.label} className="rounded-lg border bg-card px-3 py-2">
            <div className={`text-xs font-medium ${quadrant.className}`}>{quadrant.label}</div>
            <div className="text-[11px] text-muted-foreground">{quadrant.hint}</div>
            <div className="mt-1 text-lg font-semibold">{counts[i]}</div>
          </div>
        ))}
      </div>

      {points.length === 0 ? (
        <p className="rounded-lg border bg-muted/30 px-4 py-10 text-center text-sm text-muted-foreground">
          还没有需求完成 RICE 评分——在编辑需求时填写触达人数、影响程度、置信度与工作量即可出现在图上。
        </p>
      ) : (
        <div className="rounded-lg border bg-card p-3">
          <div className="mb-1 flex items-center justify-between text-[11px] text-muted-foreground">
            <span>横轴：工作量（人日，越左越省） · 纵轴：RICE 分数（越高越值得做）</span>
            {unscored > 0 && <span>另有 {unscored} 条未评分未显示</span>}
          </div>
          <div className="h-80 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <ScatterChart margin={{ top: 12, right: 24, bottom: 8, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-hairline" />
                <XAxis
                  type="number"
                  dataKey="x"
                  name="工作量"
                  tick={{ fontSize: 11 }}
                  label={{ value: '工作量（人日）', position: 'insideBottomRight', offset: -2, fontSize: 10 }}
                />
                <YAxis type="number" dataKey="y" name="RICE" tick={{ fontSize: 11 }} />
                <ZAxis type="number" dataKey="z" range={[60, 60]} />
                <Tooltip
                  cursor={{ strokeDasharray: '3 3' }}
                  content={({ active, payload }) => {
                    if (!active || !payload?.length) return null;
                    const point = payload[0].payload as QuadrantPoint;
                    return (
                      <div className="rounded-md border bg-popover px-3 py-2 text-xs shadow-md">
                        <div className="font-medium">{point.name}</div>
                        <div className="text-muted-foreground">
                          RICE {point.score} · 工作量 {point.effort} 人日 · {point.priority}
                        </div>
                      </div>
                    );
                  }}
                />
                <ReferenceLine x={midX} className="stroke-hairline" strokeDasharray="4 4" />
                <ReferenceLine y={midY} className="stroke-hairline" strokeDasharray="4 4" />
                <Scatter
                  data={points}
                  fill="hsl(var(--brand, 240 5% 40%))"
                  onClick={(data: unknown) => {
                    // recharts 把原始数据挂在 payload 上，事件参数本身没有业务字段
                    const id = (data as { payload?: { id?: string } })?.payload?.id;
                    setSelected(id ?? null);
                  }}
                />
              </ScatterChart>
            </ResponsiveContainer>
          </div>
          <p className="mt-1 text-[11px] text-muted-foreground">
            分界线为当前集合的中位数：右上优先做、左上规划做、右下顺手做、左下先搁置。
          </p>
        </div>
      )}

      {active && (
        <div className="rounded-lg border bg-muted/30 px-3 py-2 text-sm">
          <div className="flex items-center gap-2">
            <span className="font-medium">{active.name}</span>
            <Badge variant="outline" className="text-[10px]">RICE {active.score}</Badge>
            <span className="text-xs text-muted-foreground">{active.priority} · {active.effort} 人日</span>
          </div>
        </div>
      )}
    </div>
  );
}
