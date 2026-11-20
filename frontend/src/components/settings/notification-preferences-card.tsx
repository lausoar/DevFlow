'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Bell, Moon, Loader2 } from 'lucide-react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notificationPreferenceApi } from '@/lib/api/notification-preference';

/**
 * 通知偏好卡片（设置页）。
 *
 * 两类控制：
 * - 按类型开关：任务/缺陷/评论/提及…各自可关；
 * - 免打扰时段：该时段内通知仍入库，但不实时推送（用户回来看通知中心）。
 */
export function NotificationPreferencesCard() {
  const queryClient = useQueryClient();
  const { data: res, isLoading } = useQuery({
    queryKey: ['notification-preferences'],
    queryFn: () => notificationPreferenceApi.get(),
  });
  const update = useMutation({
    mutationFn: (data: Parameters<typeof notificationPreferenceApi.update>[0]) =>
      notificationPreferenceApi.update(data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notification-preferences'] }),
  });

  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const prefs = res?.data;

  const TYPE_LABELS: Record<string, string> = {
    COMMENT: '评论',
    MENTION: '@提及',
    TASK_UPDATE: '任务变更',
    BUG_UPDATE: '缺陷变更',
    SPRINT_UPDATE: '迭代变更',
    SYSTEM: '系统消息',
    AI: 'AI 助手',
    DESIGN_UPDATE: '设计稿变更',
    TRIAGE: '分诊待处理',
    GUEST_SUBMIT: '外部提交',
    DEPLOY_APPROVAL: '部署审批',
  };

  const toggleType = async (type: string, enabled: boolean) => {
    setMsg(null);
    await update.mutateAsync({ types: { [type]: enabled } });
  };

  const saveDnd = async (patch: { dndEnabled?: boolean; dndStart?: string; dndEnd?: string }) => {
    setMsg(null);
    try {
      await update.mutateAsync(patch);
      setMsg({ ok: true, text: '已保存' });
    } catch (e) {
      setMsg({
        ok: false,
        text: (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '保存失败',
      });
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Bell className="h-4 w-4" />
          通知偏好
        </CardTitle>
        <CardDescription>
          关掉的类型仍会进通知中心，只是不再实时推送——避免重要消息彻底丢失
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {isLoading ? (
          <div className="flex h-12 items-center justify-center">
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          </div>
        ) : (
          <>
            <div className="space-y-1.5">
              {Object.entries(prefs?.types || {}).map(([type, enabled]) => (
                <label key={type} className="flex items-center justify-between rounded-md px-1 py-1.5 hover:bg-muted/40">
                  <span className="text-sm">{TYPE_LABELS[type] || type}</span>
                  <input
                    type="checkbox"
                    checked={enabled}
                    onChange={(e) => toggleType(type, e.target.checked)}
                    className="h-4 w-4"
                  />
                </label>
              ))}
            </div>

            <div className="space-y-2 border-t pt-3">
              <div className="flex items-center gap-2">
                <Moon className="h-3.5 w-3.5 text-muted-foreground" />
                <span className="text-sm font-medium">免打扰时段</span>
                <span className="text-xs text-muted-foreground">（该时段内只入库不推送）</span>
              </div>
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={!!prefs?.dndEnabled}
                  onChange={(e) => saveDnd({ dndEnabled: e.target.checked })}
                  className="h-4 w-4"
                />
                启用免打扰
              </label>
              <div className="flex items-center gap-2">
                <div className="flex-1">
                  <Label className="text-xs">开始</Label>
                  <Input
                    type="time"
                    defaultValue={prefs?.dndStart || '22:00'}
                    onBlur={(e) => saveDnd({ dndStart: e.target.value })}
                  />
                </div>
                <div className="flex-1">
                  <Label className="text-xs">结束</Label>
                  <Input
                    type="time"
                    defaultValue={prefs?.dndEnd || '08:00'}
                    onBlur={(e) => saveDnd({ dndEnd: e.target.value })}
                  />
                </div>
              </div>
              <p className="text-[11px] text-muted-foreground">
                开始晚于结束时按跨天处理（例如 22:00 - 08:00）
              </p>
            </div>

            <div className="flex gap-2">
              <Button
                variant="outline" size="sm" className="h-7 text-xs"
                onClick={async () => {
                  await notificationPreferenceApi.bulk(true);
                  queryClient.invalidateQueries({ queryKey: ['notification-preferences'] });
                }}
              >
                全部开启
              </Button>
              <Button
                variant="outline" size="sm" className="h-7 text-xs"
                onClick={async () => {
                  await notificationPreferenceApi.bulk(false);
                  queryClient.invalidateQueries({ queryKey: ['notification-preferences'] });
                }}
              >
                全部关闭
              </Button>
            </div>
            {msg && (
              <p className={`text-xs ${msg.ok ? 'text-success' : 'text-destructive'}`}>{msg.text}</p>
            )}
          </>
        )}
      </CardContent>
    </Card>
  );
}
