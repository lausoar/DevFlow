'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Loader2 } from 'lucide-react';
import {
  useCustomFields,
  useCustomFieldValues,
  useSaveCustomFieldValues,
} from '@/lib/hooks/use-custom-fields';
import type { CustomFieldDef } from '@/lib/api/custom-field';

/**
 * 自定义字段编辑区（嵌在工作项详情/编辑弹窗里）。
 *
 * 字段定义来自项目配置，这里只负责按类型渲染控件；
 * 校验以前端提示 + 服务端权威两道，服务端会拒绝未知字段与非法选项。
 */
export function CustomFieldsSection({
  projectId,
  issueType,
  issueId,
  editable,
}: {
  projectId: string;
  issueType: 'TASK' | 'BUG' | 'REQUIREMENT';
  issueId: string;
  editable: boolean;
}) {
  const { data: defsRes, isLoading: defsLoading } = useCustomFields(projectId, issueType);
  const { data: valuesRes, isLoading: valuesLoading } = useCustomFieldValues(issueType, issueId);
  const saveValues = useSaveCustomFieldValues(issueType, issueId);

  const defs = (defsRes?.data || []).filter((d) => d.enabled === 1);
  // 本地编辑优先；未编辑时直接用服务端值（派生而非 effect 回填，
  // 既避免 set-state-in-effect 规则，也不会在数据刷新时打断输入）
  const [edited, setEdited] = useState<Record<string, unknown> | null>(null);
  const [error, setError] = useState('');
  const local: Record<string, unknown> = edited ?? (valuesRes?.data as Record<string, unknown>) ?? {};
  const dirty = edited !== null;

  if (defsLoading || valuesLoading) {
    return (
      <div className="flex h-10 items-center justify-center">
        <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
      </div>
    );
  }
  if (defs.length === 0) {
    return null; // 没配自定义字段就不占位
  }

  const setValue = (key: string, value: unknown) => {
    setEdited({ ...local, [key]: value });
    setError('');
  };

  const handleSave = async () => {
    // 前端只做必填提示，其余交给服务端权威校验
    for (const def of defs) {
      if (def.required === 1) {
        const v = local[def.fieldKey];
        if (v == null || v === '' || (Array.isArray(v) && v.length === 0)) {
          setError(`「${def.name}」为必填项`);
          return;
        }
      }
    }
    try {
      await saveValues.mutateAsync(local);
      setEdited(null); // 保存成功后回到"以服务端值为准"

    } catch (e) {
      setError(
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '保存失败',
      );
    }
  };

  return (
    <div className="space-y-2">
      <h4 className="text-sm font-medium">扩展字段</h4>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {defs.map((def) => (
          <FieldControl
            key={def.id}
            def={def}
            value={local[def.fieldKey]}
            editable={editable}
            onChange={(v) => setValue(def.fieldKey, v)}
          />
        ))}
      </div>
      {error && <p className="text-xs text-destructive">{error}</p>}
      {editable && dirty && (
        <div className="flex justify-end">
          <Button size="sm" className="h-7 text-xs" onClick={handleSave} disabled={saveValues.isPending}>
            {saveValues.isPending ? '保存中...' : '保存扩展字段'}
          </Button>
        </div>
      )}
    </div>
  );
}

function FieldControl({
  def,
  value,
  editable,
  onChange,
}: {
  def: CustomFieldDef;
  value: unknown;
  editable: boolean;
  onChange: (value: unknown) => void;
}) {
  const options: string[] = def.options ? safeParse(def.options) : [];
  const label = (
    <Label className="text-xs">
      {def.name}
      {def.required === 1 && <span className="ml-0.5 text-destructive">*</span>}
    </Label>
  );

  const common = 'flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm disabled:opacity-60';

  switch (def.type) {
    case 'TEXTAREA':
      return (
        <div className="space-y-1 sm:col-span-2">
          {label}
          <textarea
            value={String(value ?? '')}
            disabled={!editable}
            onChange={(e) => onChange(e.target.value)}
            placeholder={def.description || ''}
            className="min-h-[70px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm disabled:opacity-60"
          />
        </div>
      );
    case 'NUMBER':
      return (
        <div className="space-y-1">
          {label}
          <Input
            type="number"
            value={String(value ?? '')}
            disabled={!editable}
            onChange={(e) => onChange(e.target.value)}
            placeholder={def.description || ''}
          />
        </div>
      );
    case 'DATE':
      return (
        <div className="space-y-1">
          {label}
          <Input
            type="date"
            value={String(value ?? '')}
            disabled={!editable}
            onChange={(e) => onChange(e.target.value)}
          />
        </div>
      );
    case 'SELECT':
      return (
        <div className="space-y-1">
          {label}
          <select
            value={String(value ?? '')}
            disabled={!editable}
            onChange={(e) => onChange(e.target.value)}
            className={common}
          >
            <option value="">未选择</option>
            {options.map((o) => (
              <option key={o} value={o}>{o}</option>
            ))}
          </select>
        </div>
      );
    case 'MULTI_SELECT': {
      const selected: string[] = Array.isArray(value) ? (value as string[]) : [];
      return (
        <div className="space-y-1">
          {label}
          <div className="flex flex-wrap gap-1.5 rounded-md border p-2">
            {options.length === 0 && <span className="text-xs text-muted-foreground">未配置选项</span>}
            {options.map((o) => {
              const on = selected.includes(o);
              return (
                <button
                  key={o}
                  type="button"
                  disabled={!editable}
                  onClick={() =>
                    onChange(on ? selected.filter((x) => x !== o) : [...selected, o])
                  }
                  className={`rounded px-2 py-0.5 text-xs transition-colors ${
                    on ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'
                  } ${editable ? '' : 'opacity-60'}`}
                >
                  {o}
                </button>
              );
            })}
          </div>
        </div>
      );
    }
    case 'CHECKBOX':
      return (
        <div className="flex items-center gap-2 pt-5">
          <input
            type="checkbox"
            checked={value === true || value === 'true'}
            disabled={!editable}
            onChange={(e) => onChange(e.target.checked)}
          />
          <span className="text-xs">{def.name}</span>
        </div>
      );
    default:
      return (
        <div className="space-y-1">
          {label}
          <Input
            value={String(value ?? '')}
            disabled={!editable}
            onChange={(e) => onChange(e.target.value)}
            placeholder={def.description || ''}
          />
        </div>
      );
  }
}

function safeParse(json: string): string[] {
  try {
    const v = JSON.parse(json);
    return Array.isArray(v) ? v.map(String) : [];
  } catch {
    return [];
  }
}
