'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Loader2, Plus, Trash2, Bookmark } from 'lucide-react';
import { queryApi, type SavedFilter } from '@/lib/api/query';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

/**
 * 保存的筛选器（视图）面板。
 *
 * 使用体验：要么从已有筛选器里选一个直接执行，要么手写表达式后保存。
 * 表达式是受限语法（字段白名单 + 参数绑定），保存前服务端会先解析一次，
 * 语法错或用了不存在的字段会被直接拒绝 —— 避免存下一个永远跑不通的视图。
 */

const EXAMPLES = [
  'status IN (TODO, DOING) AND priority = HIGH',
  'assignee = currentUser() AND dueDate <= endOfWeek()',
  'title ~ 登录 AND status != DONE',
];

export function SavedFiltersPanel({
  projectId,
  entity = 'TASK',
  onRun,
}: {
  projectId: string;
  entity?: 'TASK' | 'BUG' | 'REQUIREMENT';
  onRun?: (expression: string) => void;
}) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [expression, setExpression] = useState('');
  const [error, setError] = useState('');
  const [preview, setPreview] = useState<{ total: number; records: unknown[] } | null>(null);

  const { data: filtersRes, isLoading } = useQuery({
    queryKey: ['saved-filters', projectId, entity],
    queryFn: () => queryApi.listFilters({ projectId, entity }),
    enabled: !!projectId && open,
  });
  const createFilter = useMutation({
    mutationFn: () => queryApi.saveFilter({ projectId, entity, name: name.trim(), expression: expression.trim() }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['saved-filters', projectId, entity] });
      setName('');
      setExpression('');
      setError('');
    },
    onError: (e) =>
      setError((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '保存失败'),
  });
  const deleteFilter = useMutation({
    mutationFn: (id: string) => queryApi.deleteFilter(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['saved-filters', projectId, entity] }),
  });
  const runPreview = useMutation({
    mutationFn: (expr: string) => queryApi.run({ entity, expression: expr, projectId, pageSize: 20 }),
    onSuccess: (res) => {
      const d = res.data as { total: number; records: unknown[] };
      setPreview({ total: d.total, records: d.records || [] });
      setError('');
    },
    onError: (e) =>
      setError((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '表达式无效'),
  });

  const filters: SavedFilter[] = filtersRes?.data || [];

  return (
    <>
      <Button variant="outline" size="sm" className="gap-1" onClick={() => setOpen(true)}>
        <Bookmark className="h-3.5 w-3.5" />
        视图
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Bookmark className="h-4 w-4" />
              保存的视图
            </DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            {/* 已有筛选器 */}
            <div className="space-y-1.5">
              <Label className="text-xs">我的视图</Label>
              {isLoading ? (
                <div className="flex h-10 items-center justify-center">
                  <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                </div>
              ) : filters.length === 0 ? (
                <p className="text-xs text-muted-foreground">还没有保存的视图</p>
              ) : (
                <div className="space-y-1">
                  {filters.map((f) => (
                    <div key={f.id} className="flex items-center gap-2 rounded-md border px-2 py-1.5">
                      <span className="text-xs font-medium">{f.name}</span>
                      <Badge variant="outline" className="text-[10px]">{f.entity}</Badge>
                      {f.isShared === 1 && <Badge variant="outline" className="text-[10px]">共享</Badge>}
                      <code className="flex-1 truncate text-[10px] text-muted-foreground">{f.expression}</code>
                      <Button
                        variant="ghost" size="sm" className="h-6 text-xs"
                        onClick={() => {
                          setExpression(f.expression);
                          onRun?.(f.expression);
                        }}
                      >
                        使用
                      </Button>
                      <Button
                        variant="ghost" size="icon" className="h-6 w-6"
                        onClick={() => deleteFilter.mutate(f.id)}
                        title="删除"
                      >
                        <Trash2 className="h-3 w-3" />
                      </Button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* 新建 */}
            <div className="space-y-2 border-t pt-3">
              <Label className="text-xs">新建视图</Label>
              <Input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="视图名称，例如「我的高优待办」"
                className="h-8 text-xs"
              />
              <Input
                value={expression}
                onChange={(e) => setExpression(e.target.value)}
                placeholder="status IN (TODO, DOING) AND priority = HIGH"
                className="h-8 font-mono text-xs"
              />
              <div className="flex flex-wrap gap-1">
                {EXAMPLES.map((ex) => (
                  <button
                    key={ex}
                    onClick={() => setExpression(ex)}
                    className="rounded bg-muted px-2 py-0.5 text-[10px] text-muted-foreground hover:bg-muted/70"
                  >
                    {ex}
                  </button>
                ))}
              </div>
              <p className="text-[11px] text-muted-foreground">
                支持字段：status / priority / assignee / reporter / sprint / fixVersion / title /
                dueDate / createdAt / updatedAt；函数 currentUser()、today()、startOfWeek()、endOfWeek()、startOfMonth()、endOfMonth()
              </p>
              <div className="flex gap-2">
                <Button
                  variant="outline" size="sm" className="h-7 text-xs"
                  onClick={() => runPreview.mutate(expression)}
                  disabled={!expression.trim() || runPreview.isPending}
                >
                  {runPreview.isPending ? '执行中...' : '试运行'}
                </Button>
                <Button
                  size="sm" className="h-7 text-xs"
                  onClick={() => createFilter.mutate()}
                  disabled={!name.trim() || !expression.trim() || createFilter.isPending}
                >
                  <Plus className="mr-1 h-3 w-3" />
                  保存为视图
                </Button>
              </div>
              {error && <p className="text-xs text-destructive">{error}</p>}
              {preview && (
                <p className="text-xs text-green-600">
                  试运行命中 {preview.total} 条
                </p>
              )}
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
