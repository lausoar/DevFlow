'use client';

import { useAppStore, useAuthStore } from '@/stores';
import { useNotifications, useUnreadCount, useMarkRead, useMarkAllRead } from '@/lib/hooks/use-notifications';
import { authApi } from '@/lib/api/auth';
import { Button } from '@/components/ui/button';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Badge } from '@/components/ui/badge';
import {
  Bell,
  Moon,
  Sun,
  Menu,
  Bot,
  Command,
  Search,
} from 'lucide-react';
import { useTheme } from 'next-themes';
import { useState, useEffect } from 'react';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useRouter } from 'next/navigation';
import type { Notification as NotifItem } from '@/types';

// ============================================
// 顶部工具栏组件
// ============================================
export function TopBar() {
  const { theme, setTheme } = useTheme();
  const { user, logout } = useAuthStore();
  const { setMobileMenuOpen, setAiAssistantOpen, sidebarCollapsed, setSearchOpen } = useAppStore();
  const [mounted, setMounted] = useState(false);
  const router = useRouter();
  const { data: unreadRes } = useUnreadCount();
  const unreadCount = unreadRes?.data?.count ?? 0;
  const router2 = useRouter();
  const { data: recentNotifRes } = useNotifications({ page: 1, pageSize: 6 });
  const recentNotifications = recentNotifRes?.data?.records || [];
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();

  /** 点击通知：未读先标已读，跳转到通知所在地 */
  const openNotification = (n: NotifItem) => {
    if (!n.isRead) markRead.mutate(n.id);
    if (n.actionUrl) {
      router2.push(n.actionUrl);
    }
  };

  // 避免主题切换闪烁
  useEffect(() => setMounted(true), []);

  const handleLogout = async () => {
    try {
      // 调用后端接口将 Token 加入黑名单
      await authApi.logout();
    } catch {
      // 后端不可用时仍继续清除本地状态
    }
    // 标记用户已主动退出，防止 layout 自动登录
    if (typeof window !== 'undefined') {
      localStorage.setItem('devflow-logged-out', '1');
    }
    logout();
    router.push('/login');
  };

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-4 border-b border-border bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/60 lg:px-6">
      {/* 移动端菜单按钮 */}
      <Button
        variant="ghost"
        size="icon"
        className="md:hidden"
        onClick={() => setMobileMenuOpen(true)}
      >
        <Menu className="h-5 w-5" />
      </Button>

      <div className="flex flex-1 items-center justify-end gap-2">
        {/* 全局搜索入口（Cmd/Ctrl + K） */}
        <button
          onClick={() => setSearchOpen(true)}
          className="hidden sm:flex h-8 w-48 items-center gap-2 rounded-md border border-border bg-muted/50 px-3 text-xs text-muted-foreground transition-colors hover:bg-muted"
        >
          <Search className="h-3.5 w-3.5" />
          <span className="flex-1 text-left">搜索…</span>
          <kbd className="rounded border bg-background px-1 py-0.5 text-[10px]">⌘K</kbd>
        </button>
        <Button
          variant="ghost"
          size="icon"
          className="sm:hidden"
          onClick={() => setSearchOpen(true)}
          title="全局搜索"
        >
          <Search className="h-5 w-5" />
        </Button>

        {/* AI 助手快捷入口 */}
        <Button
          variant="ghost"
          size="icon"
          className="relative"
          onClick={() => setAiAssistantOpen(true)}
        >
          <Bot className="h-5 w-5" />
        </Button>

        {/* 通知入口：下拉悬浮面板 */}
        <DropdownMenu>
          <DropdownMenuTrigger
            className="relative inline-flex h-9 w-9 items-center justify-center rounded-md transition-colors hover:bg-accent"
            title="通知"
          >
            <Bell className="h-5 w-5" />
            {unreadCount > 0 && (
              <span className="absolute -right-0.5 -top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-destructive px-1 text-[10px] leading-none text-white">
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-84 p-0">
            <div className="flex items-center justify-between border-b px-3 py-2">
              <span className="text-sm font-semibold">通知</span>
              <div className="flex items-center gap-2">
                <button
                  className="text-[11px] text-muted-foreground transition-colors hover:text-foreground disabled:opacity-50"
                  disabled={markAllRead.isPending || unreadCount === 0}
                  onClick={() => markAllRead.mutate()}
                >
                  全部已读
                </button>
                <button
                  className="text-[11px] text-primary transition-colors hover:underline"
                  onClick={() => router.push('/notifications')}
                >
                  查看全部
                </button>
              </div>
            </div>
            <div className="max-h-80 overflow-y-auto">
              {recentNotifications.length === 0 ? (
                <p className="py-8 text-center text-xs text-muted-foreground">暂无通知</p>
              ) : (
                recentNotifications.map((n) => (
                  <div
                    key={n.id}
                    onClick={() => openNotification(n)}
                    className={`flex cursor-pointer items-start gap-2.5 border-b px-3 py-2.5 transition-colors last:border-b-0 hover:bg-muted/60 ${
                      !n.isRead ? 'bg-primary/[0.04]' : ''
                    }`}
                  >
                    <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" style={{ opacity: n.isRead ? 0.15 : 1 }} />
                    <div className="min-w-0 flex-1">
                      <p className={`truncate text-xs ${n.isRead ? 'text-foreground/80' : 'font-semibold text-foreground'}`}>
                        {n.title}
                      </p>
                      {n.content && (
                        <p className="mt-0.5 line-clamp-1 text-[11px] text-muted-foreground">{n.content}</p>
                      )}
                      <p className="mt-0.5 text-[10px] text-muted-foreground">
                        {new Date(n.createdAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                        {n.actionUrl ? ' · 点击查看' : ''}
                      </p>
                    </div>
                  </div>
                ))
              )}
            </div>
          </DropdownMenuContent>
        </DropdownMenu>

        {/* 主题切换 */}
        {mounted && (
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          >
            {theme === 'dark' ? (
              <Sun className="h-5 w-5" />
            ) : (
              <Moon className="h-5 w-5" />
            )}
          </Button>
        )}

        {/* 用户菜单 */}
        <DropdownMenu>
          <DropdownMenuTrigger className="flex items-center gap-2 px-2 rounded-lg hover:bg-accent">
            <Avatar className="h-7 w-7">
              <AvatarImage src={user?.avatar} alt={user?.realName || user?.username} />
              <AvatarFallback className="text-xs">
                {(user?.realName || user?.username)?.charAt(0)?.toUpperCase() || 'U'}
              </AvatarFallback>
            </Avatar>
            <span className="hidden text-sm font-medium lg:block">
              {user?.realName || user?.username || '用户'}
            </span>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-48">
            <DropdownMenuGroup>
              <DropdownMenuLabel>
                <div className="flex flex-col space-y-1">
                  <p className="text-sm font-medium">{user?.realName || user?.username}</p>
                  <p className="text-xs text-muted-foreground">{user?.email}</p>
                </div>
              </DropdownMenuLabel>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => router.push('/settings')}>
              个人设置
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => router.push('/enterprise')}>
              企业管理
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              className="text-destructive focus:text-destructive"
              onClick={handleLogout}
            >
              退出登录
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}