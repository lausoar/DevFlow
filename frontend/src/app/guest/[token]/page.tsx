'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { guestApi } from '@/lib/api/guest';
import type { GuestProjectInfo, GuestSubmissions } from '@/types';
import {
  Bug as BugIcon,
  MessageSquarePlus,
  ClipboardList,
  FileText,
  Send,
  CheckCircle2,
  Clock,
  XCircle,
  Loader2,
  LinkIcon,
} from 'lucide-react';

// ============================================
// 游客公开页（免登录）
// 通过游客链接查看项目脱敏信息、提交 Bug/建议、查看自己的提交记录
// ============================================

type Panel = 'bug' | 'suggestion' | 'submissions';

const FEEDBACK_STATUS: Record<string, { label: string; cls: string }> = {
  OPEN: { label: '待处理', cls: 'bg-amber-100 text-amber-700' },
  ACCEPTED: { label: '已接受', cls: 'bg-green-100 text-green-700' },
  REJECTED: { label: '已拒绝', cls: 'bg-slate-100 text-slate-600' },
  CONVERTED: { label: '已转 Bug', cls: 'bg-blue-100 text-blue-700' },
};

const BUG_STATUS: Record<string, { label: string; cls: string }> = {
  NEW: { label: '新提出', cls: 'bg-blue-100 text-blue-700' },
  ASSIGNED: { label: '已分配', cls: 'bg-indigo-100 text-indigo-700' },
  DOING: { label: '解决中', cls: 'bg-amber-100 text-amber-700' },
  RESOLVED: { label: '已解决', cls: 'bg-green-100 text-green-700' },
  REOPENED: { label: '重新打开', cls: 'bg-purple-100 text-purple-700' },
  CLOSED: { label: '已关闭', cls: 'bg-slate-100 text-slate-600' },
};

/** 从 axios 错误里取后端业务文案 */
function errMsg(e: unknown, fallback: string): string {
  return (
    (e as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback
  );
}

export default function GuestPage() {
  const { token } = useParams<{ token: string }>();
  const queryClient = useQueryClient();

  // 联系方式两表单共用
  const [nickname, setNickname] = useState('');
  const [email, setEmail] = useState('');
  const [panel, setPanel] = useState<Panel>('bug');

  // Bug 表单
  const [bugForm, setBugForm] = useState({ title: '', description: '', environment: '' });
  const [bugSubmitting, setBugSubmitting] = useState(false);
  const [bugTip, setBugTip] = useState<{ ok: boolean; text: string } | null>(null);

  // 建议表单
  const [fbForm, setFbForm] = useState({ type: 'SUGGESTION', title: '', content: '' });
  const [fbSubmitting, setFbSubmitting] = useState(false);
  const [fbTip, setFbTip] = useState<{ ok: boolean; text: string } | null>(null);

  // 项目信息：404（无效/撤销/过期）时走 error 分支展示原因
  const projectQuery = useQuery({
    queryKey: ['guest-project', token],
    queryFn: () => guestApi.guestProject(token),
    retry: false,
    staleTime: 60 * 1000,
  });

  // 提交记录：提交成功后手动失效刷新
  const submissionsQuery = useQuery({
    queryKey: ['guest-submissions', token],
    queryFn: () => guestApi.guestSubmissions(token),
    enabled: !!token,
  });

  // 页面标题
  useEffect(() => {
    const name = (projectQuery.data?.data as GuestProjectInfo | undefined)?.project?.name;
    document.title = name ? `${name} · 游客反馈 | DevFlow` : '游客反馈 | DevFlow';
  }, [projectQuery.data]);

  const refreshSubmissions = () =>
    queryClient.invalidateQueries({ queryKey: ['guest-submissions', token] });

  const submitBug = async () => {
    if (!bugForm.title.trim() || !nickname.trim()) return;
    setBugSubmitting(true);
    setBugTip(null);
    try {
      await guestApi.submitGuestBug(token, {
        title: bugForm.title.trim(),
        description: bugForm.description.trim() || undefined,
        environment: bugForm.environment.trim() || undefined,
        nickname: nickname.trim(),
        email: email.trim() || undefined,
      });
      setBugForm({ title: '', description: '', environment: '' });
      setBugTip({ ok: true, text: '提交成功！Bug 已进入项目待确认队列，确认后即可看到处理进展。' });
      refreshSubmissions();
    } catch (e) {
      setBugTip({ ok: false, text: errMsg(e, '提交失败，请稍后再试') });
    } finally {
      setBugSubmitting(false);
    }
  };

  const submitFeedback = async () => {
    if (!fbForm.title.trim() || !nickname.trim()) return;
    setFbSubmitting(true);
    setFbTip(null);
    try {
      await guestApi.submitGuestFeedback(token, {
        type: fbForm.type,
        title: fbForm.title.trim(),
        content: fbForm.content.trim() || undefined,
        nickname: nickname.trim(),
        email: email.trim() || undefined,
      });
      setFbForm({ type: 'SUGGESTION', title: '', content: '' });
      setFbTip({ ok: true, text: '提交成功！感谢你的建议，团队会尽快处理。' });
      refreshSubmissions();
    } catch (e) {
      setFbTip({ ok: false, text: errMsg(e, '提交失败，请稍后再试') });
    } finally {
      setFbSubmitting(false);
    }
  };

  // ---------- 链接无效态 ----------
  if (projectQuery.isError) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-gradient-to-b from-primary/5 to-transparent p-6">
        <div className="w-full max-w-md rounded-xl border bg-card p-8 text-center shadow-sm">
          <XCircle className="mx-auto h-12 w-12 text-muted-foreground/40" />
          <h1 className="mt-4 text-lg font-semibold">链接无法访问</h1>
          <p className="mt-2 text-sm text-muted-foreground">
            {errMsg(projectQuery.error, '游客链接无效或已被撤销')}
          </p>
          <p className="mt-1 text-xs text-muted-foreground">
            请联系项目成员重新获取分享链接。
          </p>
        </div>
      </main>
    );
  }

  // ---------- 加载态 ----------
  if (projectQuery.isLoading) {
    return (
      <main className="flex min-h-screen items-center justify-center">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </main>
    );
  }

  const info = projectQuery.data?.data as GuestProjectInfo | undefined;
  const submissions = submissionsQuery.data?.data as GuestSubmissions | undefined;
  const requirements = info?.requirements || [];

  return (
    <main className="min-h-screen bg-gradient-to-b from-primary/5 to-transparent">
      <div className="mx-auto max-w-2xl px-4 py-8">
        {/* 头部 */}
        <div className="mb-6 flex items-center gap-2 text-sm text-muted-foreground">
          <LinkIcon className="h-4 w-4 text-primary" />
          游客反馈通道 · 无需登录
        </div>

        {/* 项目信息卡片 */}
        <div className="rounded-xl border bg-card p-6 shadow-sm">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-lg font-bold text-primary">
              {(info?.project?.name || '?').charAt(0)}
            </div>
            <div className="min-w-0">
              <h1 className="truncate text-xl font-semibold">{info?.project?.name}</h1>
              {info?.project?.projectKey && (
                <span className="font-mono text-xs text-muted-foreground">
                  {info.project.projectKey}
                </span>
              )}
            </div>
          </div>
          {info?.project?.description && (
            <p className="mt-3 text-sm text-muted-foreground">{info.project.description}</p>
          )}

          {/* 最新需求 */}
          {requirements.length > 0 && (
            <div className="mt-5 border-t pt-4">
              <p className="mb-2 flex items-center gap-1.5 text-xs font-medium text-foreground">
                <FileText className="h-3.5 w-3.5 text-primary" />
                最新需求
              </p>
              <ul className="space-y-1.5">
                {requirements.map((r, i) => (
                  <li key={i} className="flex items-center gap-2 text-xs text-muted-foreground">
                    <CheckCircle2 className="h-3 w-3 shrink-0 text-green-500" />
                    <span className="truncate text-foreground/80">{r.title}</span>
                    <span className="ml-auto shrink-0">
                      {new Date(r.createdAt).toLocaleDateString('zh-CN')}
                    </span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </div>

        {/* 提交人信息（两表单共用） */}
        <div className="mt-4 rounded-xl border bg-card p-5 shadow-sm">
          <p className="mb-3 text-xs font-medium text-foreground">你的联系方式</p>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="guest-nickname" className="text-xs">
                昵称 <span className="text-destructive">*</span>
              </Label>
              <Input
                id="guest-nickname"
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="怎么称呼你"
                maxLength={50}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="guest-email" className="text-xs">
                邮箱（选填）
              </Label>
              <Input
                id="guest-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="方便团队联系你"
              />
            </div>
          </div>
        </div>

        {/* 面板切换 */}
        <div className="mt-4 flex gap-1.5">
          {(
            [
              { key: 'bug', label: '提交 Bug', icon: BugIcon },
              { key: 'suggestion', label: '提交建议', icon: MessageSquarePlus },
              { key: 'submissions', label: '我的提交', icon: ClipboardList },
            ] as { key: Panel; label: string; icon: React.ElementType }[]
          ).map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              onClick={() => setPanel(key)}
              className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-colors ${
                panel === key
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted text-muted-foreground hover:bg-muted/80'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {label}
            </button>
          ))}
        </div>

        {/* 提交 Bug */}
        {panel === 'bug' && (
          <div className="mt-3 rounded-xl border bg-card p-5 shadow-sm space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="bug-title" className="text-xs">
                问题标题 <span className="text-destructive">*</span>
              </Label>
              <Input
                id="bug-title"
                value={bugForm.title}
                onChange={(e) => setBugForm({ ...bugForm, title: e.target.value })}
                placeholder="简要描述遇到的问题"
                maxLength={200}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="bug-desc" className="text-xs">
                复现步骤 / 期望行为
              </Label>
              <textarea
                id="bug-desc"
                value={bugForm.description}
                onChange={(e) => setBugForm({ ...bugForm, description: e.target.value })}
                placeholder="1. 打开…&#10;2. 点击…&#10;期望：… 实际：…"
                rows={4}
                className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="bug-env" className="text-xs">
                环境（选填）
              </Label>
              <Input
                id="bug-env"
                value={bugForm.environment}
                onChange={(e) => setBugForm({ ...bugForm, environment: e.target.value })}
                placeholder="例如：Chrome 120 / Windows 11"
              />
            </div>
            {bugTip && (
              <p className={`text-xs ${bugTip.ok ? 'text-green-600' : 'text-destructive'}`}>
                {bugTip.text}
              </p>
            )}
            <Button
              onClick={submitBug}
              disabled={bugSubmitting || !bugForm.title.trim() || !nickname.trim()}
              className="w-full"
            >
              {bugSubmitting ? (
                <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
              ) : (
                <Send className="mr-1.5 h-3.5 w-3.5" />
              )}
              提交 Bug
            </Button>
            <p className="text-center text-[11px] text-muted-foreground">
              提交后由项目测试人员确认后进入处理流程
            </p>
          </div>
        )}

        {/* 提交建议 */}
        {panel === 'suggestion' && (
          <div className="mt-3 rounded-xl border bg-card p-5 shadow-sm space-y-4">
            <div className="space-y-1.5">
              <Label className="text-xs">建议类型</Label>
              <div className="flex gap-1.5">
                {(
                  [
                    { value: 'SUGGESTION', label: '功能建议' },
                    { value: 'IMPROVEMENT', label: '体验改进' },
                    { value: 'OTHER', label: '其他' },
                  ] as const
                ).map(({ value, label }) => (
                  <button
                    key={value}
                    onClick={() => setFbForm({ ...fbForm, type: value })}
                    className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                      fbForm.type === value
                        ? 'bg-primary/10 text-primary'
                        : 'bg-muted text-muted-foreground hover:bg-muted/80'
                    }`}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="fb-title" className="text-xs">
                建议标题 <span className="text-destructive">*</span>
              </Label>
              <Input
                id="fb-title"
                value={fbForm.title}
                onChange={(e) => setFbForm({ ...fbForm, title: e.target.value })}
                placeholder="一句话概括你的建议"
                maxLength={200}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="fb-content" className="text-xs">
                详细说明
              </Label>
              <textarea
                id="fb-content"
                value={fbForm.content}
                onChange={(e) => setFbForm({ ...fbForm, content: e.target.value })}
                placeholder="描述你的想法和使用场景…"
                rows={4}
                className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              />
            </div>
            {fbTip && (
              <p className={`text-xs ${fbTip.ok ? 'text-green-600' : 'text-destructive'}`}>
                {fbTip.text}
              </p>
            )}
            <Button
              onClick={submitFeedback}
              disabled={fbSubmitting || !fbForm.title.trim() || !nickname.trim()}
              className="w-full"
            >
              {fbSubmitting ? (
                <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
              ) : (
                <Send className="mr-1.5 h-3.5 w-3.5" />
              )}
              提交建议
            </Button>
          </div>
        )}

        {/* 我的提交记录 */}
        {panel === 'submissions' && (
          <div className="mt-3 rounded-xl border bg-card p-5 shadow-sm">
            {submissionsQuery.isLoading ? (
              <div className="flex justify-center py-6">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            ) : (
              <div className="space-y-5">
                <div>
                  <p className="mb-2 flex items-center gap-1.5 text-xs font-medium">
                    <BugIcon className="h-3.5 w-3.5 text-primary" />
                    我提交的 Bug（{submissions?.bugs?.length || 0}）
                  </p>
                  {!submissions?.bugs?.length ? (
                    <p className="py-2 text-center text-xs text-muted-foreground">还没有提交过 Bug</p>
                  ) : (
                    <ul className="space-y-1.5">
                      {submissions.bugs.map((b, i) => (
                        <li key={i} className="flex items-center gap-2 rounded-md border px-3 py-2">
                          <span className="min-w-0 flex-1 truncate text-xs">{b.title}</span>
                          {!!b.pending && (
                            <Badge className="shrink-0 border-0 bg-amber-100 text-[10px] text-amber-700">
                              <Clock className="mr-1 h-2.5 w-2.5" />
                              待确认
                            </Badge>
                          )}
                          <Badge
                            className={`shrink-0 border-0 text-[10px] ${
                              BUG_STATUS[b.status]?.cls || 'bg-slate-100 text-slate-600'
                            }`}
                          >
                            {BUG_STATUS[b.status]?.label || b.status}
                          </Badge>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
                <div>
                  <p className="mb-2 flex items-center gap-1.5 text-xs font-medium">
                    <MessageSquarePlus className="h-3.5 w-3.5 text-primary" />
                    我提交的建议（{submissions?.feedbacks?.length || 0}）
                  </p>
                  {!submissions?.feedbacks?.length ? (
                    <p className="py-2 text-center text-xs text-muted-foreground">还没有提交过建议</p>
                  ) : (
                    <ul className="space-y-1.5">
                      {submissions.feedbacks.map((f, i) => (
                        <li key={i} className="flex items-center gap-2 rounded-md border px-3 py-2">
                          <span className="min-w-0 flex-1 truncate text-xs">{f.title}</span>
                          <span className="shrink-0 text-[10px] text-muted-foreground">
                            {new Date(f.createdAt).toLocaleDateString('zh-CN')}
                          </span>
                          <Badge
                            className={`shrink-0 border-0 text-[10px] ${
                              FEEDBACK_STATUS[f.status]?.cls || 'bg-slate-100 text-slate-600'
                            }`}
                          >
                            {FEEDBACK_STATUS[f.status]?.label || f.status}
                          </Badge>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            )}
          </div>
        )}

        <p className="mt-6 text-center text-[11px] text-muted-foreground">
          Powered by DevFlow · 提交内容仅项目成员可见
        </p>
      </div>
    </main>
  );
}
