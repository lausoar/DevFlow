'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  useAutomationRules,
  useCreateAutomationRule,
  useUpdateAutomationRule,
  useDeleteAutomationRule,
} from '@/lib/hooks/use-automation-rules';
import { useProjectMembers } from '@/lib/hooks/use-projects';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useAuthStore } from '@/stores/auth-store';
import { Zap, Trash2, Loader2 } from 'lucide-react';
import type { AutomationRuleInfo } from '@/lib/api/automation-rule';

// ============================================
// 自动化规则管理弹窗（项目详情 → 设置入口）
// MVP：当触发事件发生时，通知指定成员；新 Bug 未指派时自动指派给规则成员
// ============================================
const TRIGGER_LABELS: Record<string, string> = {
  BUG_CREATED: '新建 Bug 时',
  BUG_FAIL: 'Bug 标记失败时',
  CASE_FAIL: '用例执行失败时',
  TASK_DONE: '任务完成时',
};

export function AutomationRulesDialog({
  projectId,
  open,
  onOpenChange,
}: {
  projectId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const { user } = useAuthStore();
  const { data: res, isLoading } = useAutomationRules(open ? projectId : null);
  const { data: membersRes } = useProjectMembers(open ? projectId : null);
  const members = membersRes?.data || [];
  // 成员 ID → 展示名（真实姓名优先）
  const memberName = (id?: string | null) => {
    if (!id) return '';
    const m = members.find((x) => x.userId === id);
    return m ? m.realName || m.username || id.slice(0, 8) : id.slice(0, 8);
  };
  const createRule = useCreateAutomationRule(projectId);
  const updateRule = useUpdateAutomationRule(projectId);
  const deleteRule = useDeleteAutomationRule(projectId);

  const [name, setName] = useState('');
  const [trigger, setTrigger] = useState('BUG_CREATED');
  const [notifyUser, setNotifyUser] = useState('');
  const [assignTo, setAssignTo] = useState('');

  const rules: AutomationRuleInfo[] = res?.data || [];

  const handleAdd = async () => {
    if (!name.trim() || !notifyUser.trim()) return;
    try {
      await createRule.mutateAsync({
        name: name.trim(),
        triggerType: trigger as AutomationRuleInfo['triggerType'],
        notifyUserId: notifyUser.trim(),
        assignToUserId: assignTo.trim() || undefined,
        enabled: 1,
      });
      setName('');
      setNotifyUser('');
      setAssignTo('');
    } catch (e) {
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '创建失败');
    }
  };

  const toggleEnabled = async (rule: AutomationRuleInfo) => {
    try {
      await updateRule.mutateAsync({
        ruleId: rule.id,
        data: { enabled: rule.enabled === 1 ? 0 : 1 },
      });
    } catch (e) {
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '操作失败');
    }
  };

  const handleDelete = async (ruleId: string) => {
    if (!confirm('确定删除该规则吗？')) return;
    try {
      await deleteRule.mutateAsync(ruleId);
    } catch (e) {
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '删除失败');
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[85vh] max-w-lg overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-1.5">
            <Zap className="h-4 w-4 text-amber-500" /> 自动化规则
          </DialogTitle>
          <DialogDescription>
            当触发事件发生时，自动通知指定成员（新 Bug 场景还可自动指派）
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          {isLoading ? (
            <p className="py-4 text-center text-xs text-muted-foreground">
              <Loader2 className="mr-1 inline h-3 w-3 animate-spin" /> 加载中...
            </p>
          ) : rules.length === 0 ? (
            <p className="py-4 text-center text-xs text-muted-foreground">还没有自动化规则</p>
          ) : (
            rules.map((rule) => (
              <div key={rule.id} className="flex items-center gap-2 rounded-md border p-2.5">
                <Zap className={`h-3.5 w-3.5 shrink-0 ${rule.enabled === 1 ? 'text-amber-500' : 'text-muted-foreground'}`} />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-xs font-medium">{rule.name}</p>
                  <p className="text-[11px] text-muted-foreground">
                    {TRIGGER_LABELS[rule.triggerType] || rule.triggerType}
                    {rule.notifyUserId && ` → 通知 ${memberName(rule.notifyUserId)}`}
                    {rule.assignToUserId && ` → 指派 ${memberName(rule.assignToUserId)}`}
                    {rule.enabled === 0 && ' （已停用）'}
                  </p>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  className="h-6 shrink-0 text-[11px]"
                  disabled={updateRule.isPending}
                  onClick={() => toggleEnabled(rule)}
                >
                  {rule.enabled === 1 ? '停用' : '启用'}
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-6 w-6 shrink-0 text-muted-foreground hover:text-destructive"
                  title="删除"
                  onClick={() => handleDelete(rule.id)}
                >
                  <Trash2 className="h-3 w-3" />
                </Button>
              </div>
            ))
          )}
        </div>

        <div className="space-y-2 rounded-md border bg-muted/30 p-3">
          <Label className="text-xs">新建规则</Label>
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="规则名称（例如：新缺陷提醒测试组）" className="h-8 text-xs" />
          <Select value={trigger} onValueChange={(v) => { if (v !== null) setTrigger(v); }}>
            <SelectTrigger className="h-8 text-xs">
              <SelectValue>{TRIGGER_LABELS[trigger] || trigger}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              {Object.entries(TRIGGER_LABELS).map(([value, label]) => (
                <SelectItem key={value} value={value}>{label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={notifyUser} onValueChange={(v) => { if (v !== null) setNotifyUser(v); }}>
            <SelectTrigger className="h-8 text-xs">
              <SelectValue>{notifyUser ? `通知 ${memberName(notifyUser)}` : '选择通知对象'}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              {members.map((m) => (
                <SelectItem key={m.id} value={m.userId}>{m.realName || m.username}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={assignTo} onValueChange={(v) => { if (v !== null) setAssignTo(v); }}>
            <SelectTrigger className="h-8 text-xs">
              <SelectValue>{assignTo ? `指派 ${memberName(assignTo)}` : '自动指派给（可选）'}</SelectValue>
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="">不自动指派</SelectItem>
              {members.map((m) => (
                <SelectItem key={m.id} value={m.userId}>{m.realName || m.username}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button className="w-full" size="sm" disabled={!name.trim() || !notifyUser.trim() || createRule.isPending} onClick={handleAdd}>
            {createRule.isPending ? <Loader2 className="mr-1 h-3 w-3 animate-spin" /> : null}
            添加规则
          </Button>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
