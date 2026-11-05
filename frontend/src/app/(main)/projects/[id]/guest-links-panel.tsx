'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { useGuestLinks, useGenerateGuestLink, useRevokeGuestLink } from '@/lib/hooks/use-guest';
import type { GuestLink } from '@/types';
import { LinkIcon, Copy, Check, Plus, Loader2, Ban } from 'lucide-react';

// ============================================
// 游客链接管理面板（成员管理弹窗内，仅 OWNER/MANAGER 可见）
// 生成后完整链接只显示一次（后端只存哈希）
// ============================================
export function GuestLinksPanel({ projectId }: { projectId: string }) {
  const { data: linksRes, isLoading } = useGuestLinks(projectId, true);
  const generateLink = useGenerateGuestLink(projectId);
  const revokeLink = useRevokeGuestLink(projectId);

  const [expiresInDays, setExpiresInDays] = useState<string>('0');
  const [newToken, setNewToken] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const links: GuestLink[] = linksRes?.data || [];

  const handleGenerate = async () => {
    setError(null);
    generateLink.mutate(
      expiresInDays === '0' ? undefined : Number(expiresInDays),
      {
        onSuccess: (res) => {
          setNewToken(res.data?.token || null);
          setCopied(false);
        },
        onError: (e) =>
          setError(
            (e as { response?: { data?: { message?: string } } })?.response?.data?.message ||
              '生成失败',
          ),
      },
    );
  };

  const guestLinkUrl = newToken
    ? typeof window !== 'undefined'
      ? `${window.location.origin}/guest/${newToken}`
      : ''
    : '';

  const handleCopy = async () => {
    if (!guestLinkUrl) return;
    try {
      await navigator.clipboard.writeText(guestLinkUrl);
    } catch {
      const input = document.getElementById('guest-link-url') as HTMLInputElement | null;
      input?.select();
      document.execCommand('copy');
    }
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleRevoke = (guestId: string) => {
    if (!confirm('确定要撤销该游客链接吗？撤销后链接立即失效。')) return;
    revokeLink.mutate(guestId, {
      onError: (e) =>
        alert(
          (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '撤销失败',
        ),
    });
  };

  return (
    <div className="rounded-lg border bg-muted/30 p-3 space-y-2">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5 text-xs font-medium text-foreground">
          <LinkIcon className="h-3.5 w-3.5 text-amber-500" />
          游客链接
        </div>
        <div className="flex items-center gap-1.5">
          <select
            value={expiresInDays}
            onChange={(e) => setExpiresInDays(e.target.value)}
            title="链接有效期"
            className="h-7 rounded-md border border-input bg-background px-1.5 text-[11px] shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          >
            <option value="0">长期有效</option>
            <option value="7">7 天有效</option>
            <option value="30">30 天有效</option>
          </select>
          <Button
            variant="outline"
            size="sm"
            className="h-7 gap-1 text-xs"
            disabled={generateLink.isPending}
            onClick={handleGenerate}
          >
            {generateLink.isPending ? (
              <Loader2 className="h-3 w-3 animate-spin" />
            ) : (
              <Plus className="h-3 w-3" />
            )}
            生成
          </Button>
        </div>
      </div>
      <p className="text-[11px] text-muted-foreground">
        外部人员无需账号即可通过链接查看项目概况并提交 Bug / 建议，提交进入待确认队列。
      </p>

      {/* 生成成功：完整链接只展示这一次 */}
      {newToken && (
        <div className="space-y-1.5 rounded-md border border-amber-200 bg-amber-50 p-2 dark:border-amber-900 dark:bg-amber-950/30">
          <p className="text-[11px] font-medium text-amber-700 dark:text-amber-400">
            请立即复制下面的链接，关闭后将无法再次查看：
          </p>
          <div className="flex items-center gap-2">
            <Input
              id="guest-link-url"
              readOnly
              value={guestLinkUrl}
              className="h-8 text-xs font-mono"
              onFocus={(e) => e.target.select()}
            />
            <Button
              variant="outline"
              size="sm"
              className="h-8 gap-1 text-xs shrink-0"
              onClick={handleCopy}
            >
              {copied ? (
                <><Check className="h-3 w-3 text-green-500" /> 已复制</>
              ) : (
                <><Copy className="h-3 w-3" /> 复制</>
              )}
            </Button>
          </div>
        </div>
      )}

      {error && <p className="text-[11px] text-destructive">{error}</p>}

      {/* 链接列表 */}
      {isLoading ? (
        <p className="py-1 text-center text-[11px] text-muted-foreground">加载中...</p>
      ) : links.length === 0 ? (
        <p className="py-1 text-center text-[11px] text-muted-foreground">还没有生成过游客链接</p>
      ) : (
        <div className="space-y-1">
          {links.map((link) => (
            <div
              key={link.id}
              className="flex items-center gap-2 rounded-md border bg-background px-2 py-1.5"
            >
              <div className="min-w-0 flex-1">
                <p className="truncate text-[11px] font-medium">
                  {link.nickname ? `游客「${link.nickname}」` : '未有人使用'}
                  {link.email && <span className="ml-1 font-normal text-muted-foreground">{link.email}</span>}
                </p>
                <p className="text-[10px] text-muted-foreground">
                  生成于 {new Date(link.createdAt).toLocaleDateString('zh-CN')} ·{' '}
                  {link.revoked ? '已撤销' : link.expiresAt ? `${new Date(link.expiresAt).toLocaleDateString('zh-CN')} 过期` : '长期有效'}
                </p>
              </div>
              {link.revoked ? (
                <Badge variant="secondary" className="shrink-0 text-[10px]">
                  已撤销
                </Badge>
              ) : (
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-6 w-6 shrink-0 text-muted-foreground hover:text-destructive"
                  disabled={revokeLink.isPending}
                  onClick={() => handleRevoke(link.id)}
                  title="撤销链接"
                >
                  <Ban className="h-3 w-3" />
                </Button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
