'use client';

import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  useRecycleBin,
  useRestoreTask,
  useRestoreBug,
  useRestoreRequirement,
} from '@/lib/hooks/use-recycle-bin';
import { AlertTriangle, ListTodo, Bug as BugIcon, FileText, RotateCcw } from 'lucide-react';

// ============================================
// 回收站弹窗：项目内已删除任务/Bug/需求的查看与恢复
// ============================================
export function RecycleBinDialog({
  projectId,
  open,
  onOpenChange,
}: {
  projectId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { data: res, isLoading } = useRecycleBin(open ? projectId : null);
  const restoreTask = useRestoreTask(projectId);
  const restoreBug = useRestoreBug(projectId);
  const restoreRequirement = useRestoreRequirement(projectId);

  const tasks = res?.data?.tasks || [];
  const bugs = res?.data?.bugs || [];
  const requirements = res?.data?.requirements || [];
  const total = tasks.length + bugs.length + requirements.length;

  const sections = [
    { key: 'task', label: '任务', icon: ListTodo, items: tasks, restore: restoreTask },
    { key: 'bug', label: 'Bug', icon: BugIcon, items: bugs, restore: restoreBug },
    { key: 'requirement', label: '需求', icon: FileText, items: requirements, restore: restoreRequirement },
  ];

  const handleRestore = (
    restore: { mutate: (id: string, opts?: object) => void },
    id: string,
    name: string,
  ) => {
    if (!confirm(`确定恢复「${name}」吗？`)) return;
    restore.mutate(id, {
      onError: (e: unknown) =>
        alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '恢复失败'),
    });
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[80vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-1.5">
            <AlertTriangle className="h-4 w-4 text-amber-500" /> 回收站
          </DialogTitle>
          <DialogDescription>
            项目内已删除的任务、Bug 和需求会保留在这里，可随时恢复
          </DialogDescription>
        </DialogHeader>

        {isLoading ? (
          <p className="py-6 text-center text-xs text-muted-foreground">加载中...</p>
        ) : total === 0 ? (
          <p className="py-6 text-center text-xs text-muted-foreground">回收站是空的</p>
        ) : (
          <div className="space-y-4">
            {sections.map(({ key, label, icon: Icon, items, restore }) =>
              items.length === 0 ? null : (
                <div key={key} className="space-y-1.5">
                  <p className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
                    <Icon className="h-3.5 w-3.5" /> {label}
                    <Badge variant="outline" className="text-[10px]">{items.length}</Badge>
                  </p>
                  {items.map((item) => (
                    <div key={item.id} className="flex items-center gap-2 rounded-md border p-2">
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-xs font-medium">{item.title}</p>
                        <p className="text-[11px] text-muted-foreground">
                          #{item.id.slice(-8)}
                          {item.updatedAt && ` · 删除于 ${new Date(item.updatedAt).toLocaleString('zh-CN')}`}
                        </p>
                      </div>
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-6 shrink-0 gap-1 text-[11px]"
                        disabled={restore.isPending}
                        onClick={() => handleRestore(restore, item.id, item.title)}
                      >
                        <RotateCcw className="h-3 w-3" /> 恢复
                      </Button>
                    </div>
                  ))}
                </div>
              )
            )}
          </div>
        )}

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
