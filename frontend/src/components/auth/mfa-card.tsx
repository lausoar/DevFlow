'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { ShieldCheck, Loader2, Copy } from 'lucide-react';
import {
  useMfaStatus,
  useBeginMfaBind,
  useConfirmMfaBind,
  useDisableMfa,
} from '@/lib/hooks/use-security';

/**
 * 双因素认证卡片（设置页）。
 *
 * 绑定流程：点「启用」→ 服务端返回密钥与 otpauth 链接 →
 * 用户把密钥手动录入验证器 App（或把链接生成二维码）→ 输入一次动态码确认 →
 * 服务端校验通过后才真正启用。
 *
 * 为什么必须先确认再启用：否则用户可能录入了一个自己扫不出来的密钥，
 * 下次登录就永久进不去。
 */
export function MfaCard() {
  const { data: statusRes, isLoading } = useMfaStatus();
  const beginBind = useBeginMfaBind();
  const confirmBind = useConfirmMfaBind();
  const disableMfa = useDisableMfa();

  const status = statusRes?.data;
  const [bindInfo, setBindInfo] = useState<{ secret: string; otpauthUrl: string } | null>(null);
  const [code, setCode] = useState('');
  const [disableCode, setDisableCode] = useState('');
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  const handleBegin = async () => {
    setMsg(null);
    const res = await beginBind.mutateAsync();
    setBindInfo(res.data);
  };

  const handleConfirm = async () => {
    if (!bindInfo) return;
    try {
      await confirmBind.mutateAsync({ secret: bindInfo.secret, code });
      setBindInfo(null);
      setCode('');
      setMsg({ ok: true, text: '双因素认证已启用，下次登录需要输入动态码' });
    } catch (e) {
      setMsg({
        ok: false,
        text: (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '动态码校验失败',
      });
    }
  };

  const handleDisable = async () => {
    try {
      await disableMfa.mutateAsync(disableCode);
      setDisableCode('');
      setMsg({ ok: true, text: '双因素认证已关闭' });
    } catch (e) {
      setMsg({
        ok: false,
        text: (e as { response?: { data?: { message?: string } } })?.response?.data?.message || '关闭失败',
      });
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <ShieldCheck className="h-4 w-4" />
          双因素认证
          {!isLoading && status && (
            <Badge variant="outline" className={`text-[10px] ${status.enabled ? 'text-success' : 'text-muted-foreground'}`}>
              {status.enabled ? '已启用' : '未启用'}
            </Badge>
          )}
        </CardTitle>
        <CardDescription>
          启用后登录需要「密码 + 验证器动态码」两个因子，账号被盗也难被直接登录
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {isLoading ? (
          <div className="flex h-12 items-center justify-center">
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          </div>
        ) : status?.enabled ? (
          <div className="space-y-2">
            <p className="text-xs text-muted-foreground">
              {status.boundAt ? `启用时间：${new Date(status.boundAt).toLocaleString('zh-CN')}` : '已启用'}
            </p>
            <div className="flex items-end gap-2">
              <div className="flex-1">
                <Label className="text-xs">输入当前动态码以关闭</Label>
                <Input
                  value={disableCode}
                  onChange={(e) => setDisableCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  placeholder="6 位数字"
                  inputMode="numeric"
                  maxLength={6}
                />
              </div>
              <Button
                variant="outline"
                onClick={handleDisable}
                disabled={disableCode.length !== 6 || disableMfa.isPending}
              >
                关闭
              </Button>
            </div>
          </div>
        ) : bindInfo ? (
          <div className="space-y-3">
            <div className="rounded-md border bg-muted/30 p-3">
              <p className="mb-1 text-xs font-medium">1. 在验证器 App 中手动添加账号</p>
              <p className="mb-1 text-[11px] text-muted-foreground">
                打开 Google Authenticator / 1Password / 微软验证器等，选择「输入设置密钥」，粘贴下面的密钥：
              </p>
              <div className="flex items-center gap-2">
                <code className="flex-1 break-all rounded bg-background px-2 py-1 font-mono text-xs">
                  {bindInfo.secret}
                </code>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7"
                  onClick={() => navigator.clipboard?.writeText(bindInfo.secret)}
                  title="复制密钥"
                >
                  <Copy className="h-3.5 w-3.5" />
                </Button>
              </div>
              <p className="mt-2 break-all text-[10px] text-muted-foreground">{bindInfo.otpauthUrl}</p>
            </div>
            <div>
              <Label className="text-xs">2. 输入 App 中显示的 6 位动态码</Label>
              <Input
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                placeholder="6 位数字"
                inputMode="numeric"
                maxLength={6}
              />
            </div>
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => { setBindInfo(null); setCode(''); }}>
                取消
              </Button>
              <Button onClick={handleConfirm} disabled={code.length !== 6 || confirmBind.isPending}>
                {confirmBind.isPending ? '校验中...' : '确认启用'}
              </Button>
            </div>
          </div>
        ) : (
          <Button variant="outline" onClick={handleBegin} disabled={beginBind.isPending}>
            {beginBind.isPending ? '准备中...' : '启用双因素认证'}
          </Button>
        )}

        {msg && (
          <p className={`text-xs ${msg.ok ? 'text-success' : 'text-destructive'}`}>{msg.text}</p>
        )}
      </CardContent>
    </Card>
  );
}
