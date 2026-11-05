'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { useAuthStore } from '@/stores';
import { authApi } from '@/lib/api/auth';
import { Zap, Eye, EyeOff, Loader2 } from 'lucide-react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

// 登录表单校验
const loginSchema = z.object({
  username: z.string().min(1, '请输入用户名或邮箱'),
  password: z.string().min(6, '密码至少 6 位'),
});

type LoginFormData = z.infer<typeof loginSchema>;

// ============================================
// 登录页面
// ============================================
export default function LoginPage() {
  const router = useRouter();
  const { setAuth } = useAuthStore();
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginFormData) => {
    setIsLoading(true);
    setError(null);

    try {
      // 调用后端登录接口
      const res = await authApi.login(data);
      // 清除退出标记，允许后续自动登录
      if (typeof window !== 'undefined') {
        localStorage.removeItem('devflow-logged-out');
      }
      // res 已被 interceptor 解包为 R<AuthResponse>，res.data 即 AuthResponse
      setAuth(res.data.user, res.data.accessToken);
      // 登录前被守卫重定向过来时（?redirect=...），登录后回跳原页面
      const redirect = new URLSearchParams(window.location.search).get('redirect');
      // 只接受站内路径，防开放重定向
      const target = redirect && redirect.startsWith('/') && !redirect.startsWith('//')
        ? redirect
        : '/workspace';
      router.push(target);
    } catch (err) {
      setError('登录失败，请检查用户名和密码');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-background via-muted/30 to-background p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="mb-8 text-center">
          <div className="mb-4 inline-flex h-14 w-14 items-center justify-center rounded-2xl bg-brand text-white shadow-lg">
            <Zap className="h-7 w-7" />
          </div>
          <h1 className="text-2xl font-bold text-foreground">DevFlow</h1>
          <p className="mt-1 text-sm text-muted-foreground">企业级 AI 协作研发平台</p>
        </div>

        <Card className="shadow-xl">
          <CardHeader className="text-center">
            <CardTitle className="text-xl">欢迎回来</CardTitle>
            <CardDescription>登录您的 DevFlow 账号</CardDescription>
          </CardHeader>

          <form onSubmit={handleSubmit(onSubmit)}>
            <CardContent className="space-y-4">
              {error && (
                <div className="rounded-lg border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                  {error}
                </div>
              )}

              <div className="space-y-2">
                <Label htmlFor="username">用户名或邮箱</Label>
                <Input
                  id="username"
                  placeholder="请输入用户名或邮箱"
                  autoComplete="username"
                  {...register('username')}
                />
                {errors.username && (
                  <p className="text-xs text-destructive">{errors.username.message}</p>
                )}
              </div>

              <div className="space-y-2">
                <Label htmlFor="password">密码</Label>
                <div className="relative">
                  <Input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    placeholder="请输入密码"
                    autoComplete="current-password"
                    {...register('password')}
                  />
                  <button
                    type="button"
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    onClick={() => setShowPassword(!showPassword)}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {errors.password && (
                  <p className="text-xs text-destructive">{errors.password.message}</p>
                )}
              </div>
            </CardContent>

            <CardFooter className="flex flex-col gap-4">
              <Button type="submit" className="w-full" size="lg" disabled={isLoading}>
                {isLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    登录中...
                  </>
                ) : (
                  '登录'
                )}
              </Button>
              <p className="text-center text-sm text-muted-foreground">
                还没有账号？{' '}
                <Link href="/register" className="font-medium text-brand hover:underline">
                  立即注册
                </Link>
              </p>
            </CardFooter>
          </form>
        </Card>

        <p className="mt-6 text-center text-xs text-muted-foreground">
          © 2026 DevFlow. All rights reserved.
        </p>
      </div>
    </div>
  );
}
