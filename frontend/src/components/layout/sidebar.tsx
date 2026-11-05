'use client';

import { cn } from '@/lib/utils';
import { useAppStore } from '@/stores';
import { useChatUnreadStore } from '@/stores/chat-unread-store';
import {
  LayoutDashboard,
  FolderKanban,
  Bell,
  Settings,
  Bot,
  Search,
  MessageSquare,
  FileText,
  Bug,
  ListTodo,
  Zap,
  ChevronLeft,
  Building2,
  FolderOpen,
  BarChart3,
} from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Separator } from '@/components/ui/separator';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Button } from '@/components/ui/button';
import { useState } from 'react';
import { useNotifications } from '@/lib/hooks/use-notifications';

// ============================================
// 侧边栏导航项配置
// ============================================
const navItems = [
  {
    title: '工作台',
    href: '/workspace',
    icon: LayoutDashboard,
  },
  {
    title: '项目',
    href: '/projects',
    icon: FolderKanban,
  },
  {
    title: '消息',
    href: '/chat',
    icon: MessageSquare,
    badgeKey: 'chat',
  },
  {
    title: '通知',
    href: '/notifications',
    icon: Bell,
    badgeKey: 'notification',
  },
  {
    title: '文档',
    href: '/docs',
    icon: FileText,
  },
  {
    title: '文件',
    href: '/files',
    icon: FolderOpen,
  },
  {
    title: 'AI 助手',
    href: '/ai',
    icon: Bot,
  },
  {
    title: '报表',
    href: '/reports',
    icon: BarChart3,
  },
  {
    title: '企业',
    href: '/enterprise',
    icon: Building2,
  },
];

// ============================================
// 侧边栏组件
// ============================================
export function Sidebar() {
  const pathname = usePathname();
  const { sidebarCollapsed, toggleSidebar } = useAppStore();

  // 从后端获取未读通知数
  const { data: notifRes } = useNotifications({ unreadOnly: true });
  const unreadNotifCount = notifRes?.data?.total || 0;
  const chatUnreadMap = useChatUnreadStore((s) => s.unreadMap);
  const unreadChatCount = Object.values(chatUnreadMap).reduce((sum, n) => sum + n, 0);

  // 动态 badge 映射
  const badgeMap: Record<string, number> = {
    notification: unreadNotifCount,
    chat: unreadChatCount,
  };

  return (
    <aside
      className={cn(
        'fixed left-0 top-0 z-40 h-screen border-r border-border bg-sidebar transition-all duration-200 ease-in-out',
        sidebarCollapsed ? 'w-16' : 'w-60'
      )}
    >
      <div className="flex h-full flex-col">
        {/* Logo 区域 */}
        <div className="flex h-14 items-center justify-between px-4">
          {!sidebarCollapsed && (
            <Link href="/workspace" className="flex items-center gap-2">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand text-white">
                <Zap className="h-4 w-4" />
              </div>
              <span className="text-lg font-bold text-foreground">DevFlow</span>
            </Link>
          )}
          {sidebarCollapsed && (
            <Link href="/workspace" className="mx-auto">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand text-white">
                <Zap className="h-4 w-4" />
              </div>
            </Link>
          )}
          <Button
            variant="ghost"
            size="icon"
            className={cn('h-7 w-7', sidebarCollapsed && 'hidden')}
            onClick={toggleSidebar}
          >
            <ChevronLeft className="h-4 w-4" />
          </Button>
        </div>

        <Separator />

        {/* 导航区域 */}
        <nav className="flex-1 space-y-1 overflow-y-auto p-2">
          {navItems.map((item) => {
            const isActive = pathname === item.href || pathname?.startsWith(item.href + '/');
            const Icon = item.icon;

            const linkContent = (
              <Link
                key={item.href}
                href={item.href}
                className={cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-devflow',
                  isActive
                    ? 'bg-sidebar-accent text-sidebar-primary'
                    : 'text-muted-foreground hover:bg-sidebar-accent/50 hover:text-foreground',
                  sidebarCollapsed && 'justify-center px-2'
                )}
              >
                <Icon className="h-4.5 w-4.5 shrink-0" />
                {!sidebarCollapsed && (
                  <>
                    <span className="flex-1">{item.title}</span>
                    {'badgeKey' in item && item.badgeKey && badgeMap[item.badgeKey] > 0 && (
                      <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-brand px-1.5 text-xs text-white">
                        {badgeMap[item.badgeKey]}
                      </span>
                    )}
                  </>
                )}
              </Link>
            );

            // 折叠时显示 Tooltip
            if (sidebarCollapsed) {
              return (
                <Tooltip key={item.href}>
                  {linkContent}
                  <TooltipContent side="right">
                    {item.title}
                  </TooltipContent>
                </Tooltip>
              );
            }

            return linkContent;
          })}
        </nav>

        <Separator />

        {/* 底部区域 */}
        <div className="p-2">
          {!sidebarCollapsed ? (
            <>
              <Link
                href="/settings"
                className={cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-devflow',
                  pathname === '/settings'
                    ? 'bg-sidebar-accent text-sidebar-primary'
                    : 'text-muted-foreground hover:bg-sidebar-accent/50 hover:text-foreground'
                )}
              >
                <Settings className="h-4.5 w-4.5" />
                <span>设置</span>
              </Link>
            </>
          ) : (
            <Button
              variant="ghost"
              size="icon"
              className="mx-auto h-8 w-8"
              onClick={toggleSidebar}
            >
              <Settings className="h-4 w-4" />
            </Button>
          )}
        </div>
      </div>
    </aside>
  );
}
