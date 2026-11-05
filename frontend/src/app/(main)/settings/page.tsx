'use client';

import { useState } from 'react';
import { MainLayout } from '@/components/layout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Separator } from '@/components/ui/separator';
import { useAuthStore } from '@/stores/auth-store';
import { useUpdateProfile, useChangePassword, useUpdateAvatar } from '@/lib/hooks';
import type { User as UserType } from '@/types';
import { User, Lock, Save, Loader2, CheckCircle2, XCircle } from 'lucide-react';

// ============================================
// 设置页面 - 个人信息和密码修改
// ============================================
export default function SettingsPage() {
  const { user, setUser } = useAuthStore();

  return (
    <MainLayout>
      <div className="space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-foreground">设置</h1>
          <p className="text-sm text-muted-foreground">管理个人信息和安全设置</p>
        </div>

        <div className="grid gap-6 max-w-2xl">
          {/* 个人信息 */}
          <ProfileCard user={user} setUser={setUser} />

          {/* 修改密码 */}
          <PasswordCard />
        </div>
      </div>
    </MainLayout>
  );
}

// ============================================
// 个人信息卡片
// ============================================
function ProfileCard({ user, setUser }: {
  user: UserType | null;
  setUser: (user: UserType) => void;
}) {
  const [username, setUsername] = useState(user?.username || '');
  const [realName, setRealName] = useState(user?.realName || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [avatarUrl, setAvatarUrl] = useState(user?.avatar || '');
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');

  const updateProfile = useUpdateProfile();
  const updateAvatar = useUpdateAvatar();

  // 保存个人信息
  const handleSave = async () => {
    setSuccess(false);
    setError('');
    try {
      const res = await updateProfile.mutateAsync({
        username,
        realName: realName.trim(),
        phone,
        avatar: avatarUrl,
      });
      // 更新全局 store
      setUser(res.data);
      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    } catch (e: any) {
      setError(e?.response?.data?.message || '保存失败');
    }
  };

  if (!user) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base flex items-center gap-2">
          <User className="h-4 w-4" />
          个人信息
        </CardTitle>
        <CardDescription>修改真实姓名、头像、手机号</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 头像区域 */}
        <div className="flex items-center gap-4">
          <Avatar className="h-16 w-16">
            <AvatarImage src={avatarUrl || undefined} alt={username} />
            <AvatarFallback className="text-lg">
              {username?.charAt(0)?.toUpperCase() || 'U'}
            </AvatarFallback>
          </Avatar>
          <div className="flex-1">
            <Label className="text-xs text-muted-foreground">头像 URL</Label>
            <Input
              value={avatarUrl}
              onChange={(e) => setAvatarUrl(e.target.value)}
              placeholder="https://example.com/avatar.png"
              className="mt-1"
            />
          </div>
        </div>

        <Separator />

        {/* 真实姓名 */}
        <div className="space-y-1">
          <Label>真实姓名</Label>
          <Input
            value={realName}
            onChange={(e) => setRealName(e.target.value)}
            placeholder="请输入真实姓名（展示给协作成员）"
          />
        </div>

        {/* 用户名（登录用） */}
        <div className="space-y-1">
          <Label>用户名（登录用）</Label>
          <Input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="请输入用户名"
          />
        </div>

        {/* 手机号 */}
        <div className="space-y-1">
          <Label>手机号</Label>
          <Input
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="请输入手机号"
          />
        </div>

        {/* 邮箱（只读） */}
        <div className="space-y-1">
          <Label>邮箱</Label>
          <Input value={user.email} disabled className="bg-muted" />
          <p className="text-xs text-muted-foreground">邮箱暂不支持修改</p>
        </div>

        {/* 操作按钮和状态 */}
        <div className="flex items-center gap-3">
          <Button
            onClick={handleSave}
            disabled={updateProfile.isPending}
            className="gap-2"
          >
            {updateProfile.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Save className="h-4 w-4" />
            )}
            保存修改
          </Button>
          {success && (
            <span className="flex items-center gap-1 text-sm text-green-600">
              <CheckCircle2 className="h-4 w-4" /> 保存成功
            </span>
          )}
          {error && (
            <span className="flex items-center gap-1 text-sm text-red-600">
              <XCircle className="h-4 w-4" /> {error}
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

// ============================================
// 修改密码卡片
// ============================================
function PasswordCard() {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState('');

  const changePassword = useChangePassword();

  // 提交修改密码
  const handleSubmit = async () => {
    setSuccess(false);
    setError('');

    // 前端校验
    if (!oldPassword || !newPassword) {
      setError('请填写完整');
      return;
    }
    if (newPassword.length < 6) {
      setError('新密码至少 6 位');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('两次输入的新密码不一致');
      return;
    }

    try {
      await changePassword.mutateAsync({ oldPassword, newPassword });
      setSuccess(true);
      // 清空表单
      setOldPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setTimeout(() => setSuccess(false), 3000);
    } catch (e: any) {
      setError(e?.response?.data?.message || '修改密码失败');
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base flex items-center gap-2">
          <Lock className="h-4 w-4" />
          安全设置
        </CardTitle>
        <CardDescription>修改登录密码</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 原密码 */}
        <div className="space-y-1">
          <Label>当前密码</Label>
          <Input
            type="password"
            value={oldPassword}
            onChange={(e) => setOldPassword(e.target.value)}
            placeholder="请输入当前密码"
          />
        </div>

        {/* 新密码 */}
        <div className="space-y-1">
          <Label>新密码</Label>
          <Input
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            placeholder="至少 6 位"
          />
        </div>

        {/* 确认新密码 */}
        <div className="space-y-1">
          <Label>确认新密码</Label>
          <Input
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            placeholder="再次输入新密码"
          />
        </div>

        {/* 操作按钮和状态 */}
        <div className="flex items-center gap-3">
          <Button
            onClick={handleSubmit}
            disabled={changePassword.isPending}
            className="gap-2"
          >
            {changePassword.isPending ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Lock className="h-4 w-4" />
            )}
            修改密码
          </Button>
          {success && (
            <span className="flex items-center gap-1 text-sm text-green-600">
              <CheckCircle2 className="h-4 w-4" /> 密码修改成功
            </span>
          )}
          {error && (
            <span className="flex items-center gap-1 text-sm text-red-600">
              <XCircle className="h-4 w-4" /> {error}
            </span>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
