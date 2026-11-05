'use client';

import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { useProjectFeedback, useUpdateFeedbackStatus, useConvertFeedback } from '@/lib/hooks/use-guest';
import { useProjectMembers } from '@/lib/hooks/use-projects';
import { useMyPermissions } from '@/lib/hooks/use-permissions';
import type { ProjectFeedback, FeedbackType, FeedbackStatus } from '@/types';
import {
  MessageSquarePlus,
  Lightbulb,
  Wrench,
  CircleEllipsis,
  Check,
  X,
  Bug as BugIcon,
  ExternalLink,
} from 'lucide-react';

// ============================================
// 项目反馈标签页（站内成员查看，处理需 bug:edit / 转 Bug 需 bug:create）
// ============================================
const TYPE_CONFIG: Record<FeedbackType, { label: string; icon: React.ElementType; cls: string }> = {
  SUGGESTION: { label: '功能建议', icon: Lightbulb, cls: 'text-amber-600 bg-amber-500/10' },
  IMPROVEMENT: { label: '体验改进', icon: Wrench, cls: 'text-blue-600 bg-blue-500/10' },
  OTHER: { label: '其他', icon: CircleEllipsis, cls: 'text-muted-foreground bg-muted' },
};

const STATUS_CONFIG: Record<FeedbackStatus, { label: string; cls: string }> = {
  OPEN: { label: '待处理', cls: 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400' },
  ACCEPTED: { label: '已接受', cls: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
  REJECTED: { label: '已拒绝', cls: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400' },
  CONVERTED: { label: '已转 Bug', cls: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' },
};

function errMsg(e: unknown, fallback: string): string {
  return (
    (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
  );
}

export function FeedbackTab({ projectId }: { projectId: string }) {
  const { data: feedbackRes, isLoading } = useProjectFeedback(projectId);
  const updateStatus = useUpdateFeedbackStatus(projectId);
  const convert = useConvertFeedback(projectId);

  // 站内提交者姓名解析
  const { data: membersRes } = useProjectMembers(projectId);
  const memberNameMap: Record<string, string> = {};
  (membersRes?.data || []).forEach((m) => {
    memberNameMap[m.userId] = m.username || '';
  });

  // 处理反馈 = bug:edit；转 Bug = bug:create（均映射测试人员及以上）
  const myPerms = useMyPermissions(projectId);
  const projectPerms = myPerms.data?.data?.project || [];
  const canHandle = projectPerms.includes('bug:edit');
  const canConvert = projectPerms.includes('bug:create');

  const feedbacks: ProjectFeedback[] = feedbackRes?.data || [];

  const handleError = (e: unknown, fallback: string) =>
    alert(errMsg(e, fallback));

  return (
    <div className="mx-auto max-w-3xl space-y-3">
      {!isLoading && feedbacks.length === 0 ? (
        <div className="flex flex-col items-center justify-center gap-3 py-16 text-muted-foreground">
          <MessageSquarePlus className="h-12 w-12 opacity-30" />
          <p className="text-sm">暂无反馈</p>
          <p className="text-xs">游客或成员通过分享链接提交的建议会显示在这里</p>
        </div>
      ) : (
        feedbacks.map((fb) => {
          const typeMeta = TYPE_CONFIG[fb.type] || TYPE_CONFIG.OTHER;
          const statusMeta = STATUS_CONFIG[fb.status] || STATUS_CONFIG.OPEN;
          const TypeIcon = typeMeta.icon;
          const submitterName =
            fb.submitterType === 'GUEST'
              ? `游客「${fb.guestNickname || '匿名'}」`
              : memberNameMap[fb.submitterUserId || ''] || '项目成员';
          return (
            <Card key={fb.id}>
              <CardContent className="space-y-2.5 p-4">
                <div className="flex items-center gap-2">
                  <span
                    className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${typeMeta.cls}`}
                  >
                    <TypeIcon className="h-2.5 w-2.5" />
                    {typeMeta.label}
                  </span>
                  <h4 className="min-w-0 flex-1 truncate text-sm font-medium">{fb.title}</h4>
                  <Badge variant="outline" className={`shrink-0 border-0 text-[10px] ${statusMeta.cls}`}>
                    {statusMeta.label}
                  </Badge>
                </div>

                {fb.content?.trim() && (
                  <p className="whitespace-pre-wrap rounded-md border bg-muted/30 p-2.5 text-xs text-foreground/90">
                    {fb.content}
                  </p>
                )}

                <div className="flex flex-wrap items-center gap-2 text-[11px] text-muted-foreground">
                  <span>来自 {submitterName}</span>
                  <span>·</span>
                  <span>{new Date(fb.createdAt).toLocaleString('zh-CN')}</span>
                  {fb.convertedBugId && (
                    <span className="flex items-center gap-1 font-mono">
                      · <BugIcon className="h-3 w-3" />
                      Bug {fb.convertedBugId.slice(0, 8)}
                    </span>
                  )}
                </div>

                {/* 操作区：处理（接受/拒绝）+ 转 Bug */}
                {(canHandle || canConvert) && fb.status !== 'CONVERTED' && (
                  <div className="flex items-center gap-1.5 border-t pt-2.5">
                    {canHandle && fb.status === 'OPEN' && (
                      <>
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 gap-1 text-xs text-green-600 hover:text-green-700"
                          disabled={updateStatus.isPending}
                          onClick={() =>
                            updateStatus.mutate(
                              { feedbackId: fb.id, status: 'ACCEPTED' },
                              { onError: (e) => handleError(e, '处理失败') },
                            )
                          }
                        >
                          <Check className="h-3 w-3" /> 接受
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 gap-1 text-xs text-muted-foreground"
                          disabled={updateStatus.isPending}
                          onClick={() =>
                            updateStatus.mutate(
                              { feedbackId: fb.id, status: 'REJECTED' },
                              { onError: (e) => handleError(e, '处理失败') },
                            )
                          }
                        >
                          <X className="h-3 w-3" /> 拒绝
                        </Button>
                      </>
                    )}
                    {canConvert && (
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 gap-1 text-xs"
                        disabled={convert.isPending}
                        onClick={() =>
                          convert.mutate(fb.id, {
                            onError: (e) => handleError(e, '转化失败'),
                          })
                        }
                        title="创建一条关联此建议的 Bug"
                      >
                        <ExternalLink className="h-3 w-3" /> 转 Bug
                      </Button>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          );
        })
      )}
      {isLoading && (
        <div className="flex items-center justify-center py-16">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent" />
        </div>
      )}
    </div>
  );
}
