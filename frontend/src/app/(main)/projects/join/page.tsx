'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useQueryClient } from '@tanstack/react-query';
import { projectApi } from '@/lib/api/project';
import { useSearchParam } from '@/lib/hooks/use-client-only';
import type { Project } from '@/types';
import { CheckCircle2, XCircle, Loader2, FolderKanban } from 'lucide-react';

// 从 request() 抛出的错误形状中提取后端 message
function extractErrorMessage(err: unknown, fallback: string): string {
  const e = err as { response?: { data?: { message?: string } }; message?: string };
  return e?.response?.data?.message || e?.message || fallback;
}

// ============================================
// 项目邀请链接落地页：/projects/join?code=xxx
// 仅项目所属企业的成员可加入；已是成员时直接进入项目
// ============================================
export default function ProjectJoinPage() {
  const router = useRouter();

  const inviteCode = useSearchParam('code');
  const queryClient = useQueryClient();

  // 结果直接放本地状态，由接口的 then/catch 落定。
  // 不用 useMutation 的 isPending/isError：在这条「页面一进来就发一次、
  // 发完就跳走」的路径上，mutation 状态出现过 404 后仍停在 pending 的情况，
  // 用户会永远卡在"正在校验邀请链接"。
  const [result, setResult] = useState<
    { ok: true; project: Project } | { ok: false; message: string } | null
  >(null);

  const startedRef = useRef(false);
  useEffect(() => {
    if (startedRef.current || !inviteCode) return;
    startedRef.current = true;
    // 注意不要用「cleanup 里把 alive 置 false」来防重复：
    // StrictMode 会 mount→unmount→mount，第二次因 startedRef 直接返回，
    // 若第一次的请求被 alive 门挡住，结果就永远落不下来，页面卡在"正在校验"。
    projectApi
      .joinByInviteCode(inviteCode)
      .then((res) => {
        // 加入成功后让项目列表失效，回到项目页即可看到新项目
        queryClient.invalidateQueries({ queryKey: ['projects'] });
        setResult({ ok: true, project: res.data });
      })
      .catch((err) => {
        setResult({
          ok: false,
          message: extractErrorMessage(err, '加入失败，请联系项目成员重新分享'),
        });
      });
  }, [inviteCode, queryClient]);

  const status: 'joining' | 'success' | 'error' | 'invalid' = !inviteCode
    ? 'invalid'
    : result === null
      ? 'joining'
      : result.ok
        ? 'success'
        : 'error';
  const project = result && result.ok ? result.project : null;
  const errorMessage = !inviteCode
    ? '邀请链接无效：缺少邀请码'
    : result && !result.ok
      ? result.message
      : '';

  return (
    <div className="flex h-full items-center justify-center p-6">
      <Card className="w-full max-w-md text-center">
        <CardHeader>
          <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-brand/10">
            {status === 'success' ? (
              <CheckCircle2 className="h-6 w-6 text-success" />
            ) : status === 'error' ? (
              <XCircle className="h-6 w-6 text-destructive" />
            ) : (
              <Loader2 className="h-6 w-6 animate-spin text-brand" />
            )}
          </div>
          <CardTitle>
            {status === 'joining' && '正在加入项目...'}
            {status === 'success' && '加入成功'}
            {status === 'error' && '无法加入项目'}
            {status === 'invalid' && '邀请链接无效'}
          </CardTitle>
          <CardDescription>
            {status === 'success' && project
              ? `你已加入项目「${project.name}」`
              : status === 'joining'
                ? '正在校验邀请链接...'
                : status === 'invalid'
                  ? '这个链接缺少邀请码，请向项目成员索取完整的邀请链接。'
                  : errorMessage}
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col items-center gap-2">
          {status === 'success' && project && (
            <>
              <Button className="w-full gap-2" onClick={() => router.push(`/projects/${project.id}`)}>
                <FolderKanban className="h-4 w-4" /> 进入项目
              </Button>
              <Link href="/projects" className="w-full">
                <Button variant="outline" className="w-full">返回项目列表</Button>
              </Link>
            </>
          )}
          {status === 'error' && (
            <div className="space-y-2 w-full">
              <p className="text-xs text-muted-foreground">
                邀请链接仅对项目所属企业的成员有效。如果你已在其他企业，请联系项目成员将你加入对应企业。
              </p>
              <Link href="/projects" className="w-full">
                <Button variant="outline" className="w-full">前往项目列表</Button>
              </Link>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
