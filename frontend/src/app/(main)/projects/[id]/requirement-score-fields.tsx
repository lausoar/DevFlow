'use client';

import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';

/** RICE 输入值：空串表示"未填写"，不能当成 0 */
export interface RiceDraft {
  reach: string;
  impact: string;
  confidence: string;
  effort: string;
}

export const EMPTY_RICE: RiceDraft = { reach: '', impact: '', confidence: '', effort: '' };

export const IMPACT_OPTIONS = [
  { value: '0.25', label: '0.25 微小' },
  { value: '0.5', label: '0.5 低' },
  { value: '1', label: '1 中' },
  { value: '2', label: '2 高' },
  { value: '3', label: '3 极大' },
];

/** 计算前端预览分数；四项不全或 effort ≤ 0 时返回 null（与后端生成列口径一致） */
export function previewRiceScore(rice: RiceDraft): number | null {
  const reach = Number(rice.reach);
  const impact = Number(rice.impact);
  const confidence = Number(rice.confidence);
  const effort = Number(rice.effort);
  if (!rice.reach || !rice.impact || !rice.confidence || !rice.effort) return null;
  if (![reach, impact, confidence, effort].every(Number.isFinite)) return null;
  if (effort <= 0) return null;
  const score = (reach * impact * (confidence / 100)) / effort;
  return Math.round(score * 10) / 10;
}

/** 把表单里的空串转成 undefined，避免后端把 0 当成有效输入 */
export function ricePayload(rice: RiceDraft) {
  const toNumber = (value: string) => (value.trim() === '' ? undefined : Number(value));
  return {
    reach: toNumber(rice.reach),
    impact: toNumber(rice.impact),
    confidence: toNumber(rice.confidence),
    effort: toNumber(rice.effort),
  };
}

export function riceDraftOf(requirement?: {
  reach?: number | null;
  impact?: number | null;
  confidence?: number | null;
  effort?: number | null;
} | null): RiceDraft {
  const text = (value?: number | null) => (value === null || value === undefined ? '' : String(value));
  return {
    reach: text(requirement?.reach),
    impact: text(requirement?.impact),
    confidence: text(requirement?.confidence),
    effort: text(requirement?.effort),
  };
}

/**
 * RICE 评分输入区。
 *
 * 四项都可以留空：需求允许"还没评估"。只有全部填齐且工作量 > 0 才产生分数，
 * 与数据库生成列的口径一致，避免前端显示分数而库里为空。
 */
export function RiceScoreFields({
  value,
  onChange,
  idPrefix,
}: {
  value: RiceDraft;
  onChange: (next: RiceDraft) => void;
  idPrefix: string;
}) {
  const score = previewRiceScore(value);
  const set = (patch: Partial<RiceDraft>) => onChange({ ...value, ...patch });

  return (
    <div className="space-y-2 rounded-lg border bg-muted/30 p-3">
      <div className="flex items-center justify-between">
        <span className="text-sm font-medium">RICE 评分（可选）</span>
        <span className="text-xs text-muted-foreground">
          {score === null ? '填齐四项后自动计算' : `预计分数 ${score}`}
        </span>
      </div>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <div className="space-y-1">
          <Label htmlFor={`${idPrefix}-reach`} className="text-xs">触达人数</Label>
          <Input
            id={`${idPrefix}-reach`}
            type="number"
            min={1}
            inputMode="numeric"
            placeholder="如 500"
            value={value.reach}
            onChange={(e) => set({ reach: e.target.value })}
            className="h-8"
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor={`${idPrefix}-impact`} className="text-xs">影响程度</Label>
          <select
            id={`${idPrefix}-impact`}
            value={value.impact}
            onChange={(e) => set({ impact: e.target.value })}
            className="flex h-8 w-full rounded-md border border-input bg-background px-2 text-xs"
          >
            <option value="">未评估</option>
            {IMPACT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>{option.label}</option>
            ))}
          </select>
        </div>
        <div className="space-y-1">
          <Label htmlFor={`${idPrefix}-confidence`} className="text-xs">置信度 %</Label>
          <Input
            id={`${idPrefix}-confidence`}
            type="number"
            min={1}
            max={100}
            inputMode="numeric"
            placeholder="如 80"
            value={value.confidence}
            onChange={(e) => set({ confidence: e.target.value })}
            className="h-8"
          />
        </div>
        <div className="space-y-1">
          <Label htmlFor={`${idPrefix}-effort`} className="text-xs">工作量（人日）</Label>
          <Input
            id={`${idPrefix}-effort`}
            type="number"
            min={0.1}
            step="0.5"
            inputMode="decimal"
            placeholder="如 5"
            value={value.effort}
            onChange={(e) => set({ effort: e.target.value })}
            className="h-8"
          />
        </div>
      </div>
    </div>
  );
}

/** 列表里的分数徽标：未评分显示灰色占位，避免与"0 分"混淆 */
export function RiceScoreBadge({ score }: { score?: number | null }) {
  if (score === null || score === undefined) {
    return (
      <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground" title="尚未完成 RICE 评分">
        未评分
      </span>
    );
  }
  const tone = score >= 50 ? 'bg-success/15 text-success'
    : score >= 10 ? 'bg-warning/15 text-warning'
      : 'bg-muted text-muted-foreground';
  return (
    <span className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${tone}`} title="RICE 分数">
      RICE {score}
    </span>
  );
}
