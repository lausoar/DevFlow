'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { useJoinProjectByCode } from '@/lib/hooks/use-projects';
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
  const { mutateAsync: joinByCode } = useJoinProjectByCode();

  const [status, setStatus] = useState<'joining' | 'success' | 'error'>('joining');
  const [project, setProject] = useState<Project | null>(null);
  const [errorMessage, setErrorMessage] = useState('');

  // 防止 React 18 StrictMode 双执行导致重复请求
  const startedRef = useRef(false);

  useEffect(() => {
    if (startedRef.current) return;
    startedRef.current = true;

    const code = new URLSearchParams(window.location.search).get('code');
    if (!code) {
      setStatus('error');
      setErrorMessage('邀请链接无效：缺少邀请码');
      return;
    }

    joinByCode(code)
      .then((res) => {
        setProject(res.data || null);
        setStatus('success');
      })
      .catch((err) => {
        setErrorMessage(extractErrorMessage(err, '加入失败，请联系项目成员重新分享'));
        setStatus('error');
      });
  }, [joinByCode]);

  return (
    <div className="flex h-full items-center justify-center p-6">
      <Card className="w-full max-w-md text-center">
        <CardHeader>
          <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-primary/10">
            {status === 'success' ? (
              <CheckCircle2 className="h-6 w-6 text-green-500" />
            ) : status === 'error' ? (
              <XCircle className="h-6 w-6 text-destructive" />
            ) : (
              <Loader2 className="h-6 w-6 animate-spin text-primary" />
            )}
          </div>
          <CardTitle>
            {status === 'joining' && '正在加入项目...'}
            {status === 'success' && '加入成功'}
            {status === 'error' && '无法加入项目'}
          </CardTitle>
          <CardDescription>
            {status === 'success' && project
              ? `你已加入项目「${project.name}」`
              : status === 'joining'
                ? '正在校验邀请链接...'
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
