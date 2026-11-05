'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useNotifications, useMarkRead, useMarkAllRead } from '@/lib/hooks/use-notifications';
import {
  Bell,
  Check,
  CheckCheck,
  MessageSquare,
  AtSign,
  ListTodo,
  Bug,
  Zap,
  Info,
  Bot,
  Trash2,
} from 'lucide-react';
import type { Notification, NotificationType } from '@/types';

// ============================================
// 通知类型配置
// ============================================
const TYPE_CONFIG: Record<NotificationType, { label: string; icon: React.ReactNode; color: string }> = {
  COMMENT: { label: '评论', icon: <MessageSquare className="h-3.5 w-3.5" />, color: 'text-blue-500' },
  MENTION: { label: '@提及', icon: <AtSign className="h-3.5 w-3.5" />, color: 'text-purple-500' },
  TASK_UPDATE: { label: '任务', icon: <ListTodo className="h-3.5 w-3.5" />, color: 'text-green-500' },
  BUG_UPDATE: { label: 'Bug', icon: <Bug className="h-3.5 w-3.5" />, color: 'text-red-500' },
  SPRINT_UPDATE: { label: '迭代', icon: <Zap className="h-3.5 w-3.5" />, color: 'text-amber-500' },
  SYSTEM: { label: '系统', icon: <Info className="h-3.5 w-3.5" />, color: 'text-slate-500' },
  AI: { label: 'AI', icon: <Bot className="h-3.5 w-3.5" />, color: 'text-indigo-500' },
};

// ============================================
// 通知中心内容（客户端组件）
// ============================================
export default function NotificationsContent() {
  const [typeFilter, setTypeFilter] = useState<string>('ALL');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const router = useRouter();

  const { data: notificationsRes, isLoading } = useNotifications({
    type: typeFilter === 'ALL' ? undefined : typeFilter,
    unreadOnly,
  });
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();

  /** 点击通知：未读则标记已读，并跳转到通知所在位置 */
  const handleOpen = (notification: Notification) => {
    if (!notification.actionUrl) return;
    if (!notification.isRead) markRead.mutate(notification.id);
    router.push(notification.actionUrl);
  };

  const notifications = notificationsRes?.data?.records || [];
  const unreadCount = notifications.filter((n) => !n.isRead).length;

  return (
    <div className="flex h-full flex-col">
      {/* 头部 */}
      <div className="flex flex-wrap items-center gap-3 border-b px-4 py-4 sm:px-6">
        <div className="flex items-center gap-2">
          <Bell className="h-5 w-5 text-primary" />
          <h1 className="text-lg font-semibold">通知中心</h1>
          {unreadCount > 0 && (
            <Badge variant="default" className="text-[10px]">
              {unreadCount} 条未读
            </Badge>
          )}
        </div>

        <div className="ml-auto flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => markAllRead.mutate()}
            disabled={unreadCount === 0}
          >
            <CheckCheck className="mr-1.5 h-3.5 w-3.5" />
            全部已读
          </Button>
        </div>
      </div>

      {/* 过滤器 */}
      <div className="flex flex-wrap items-center gap-2 border-b px-4 py-3 sm:px-6">
        <div className="flex gap-1">
          {['ALL', 'TASK_UPDATE', 'BUG_UPDATE', 'SPRINT_UPDATE', 'COMMENT', 'MENTION', 'SYSTEM'].map(
            (type) => (
              <button
                key={type}
                onClick={() => setTypeFilter(type)}
                className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                  typeFilter === type
                    ? 'bg-primary text-primary-foreground'
                    : 'bg-muted text-muted-foreground hover:bg-muted/80'
                }`}
              >
                {type === 'ALL' ? '全部' : TYPE_CONFIG[type as NotificationType]?.label || type}
              </button>
            )
          )}
        </div>
        <div className="ml-auto">
          <button
            onClick={() => setUnreadOnly(!unreadOnly)}
            className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
              unreadOnly
                ? 'bg-primary text-primary-foreground'
                : 'bg-muted text-muted-foreground hover:bg-muted/80'
            }`}
          >
            仅未读
          </button>
        </div>
      </div>

      {/* 通知列表 */}
      <ScrollArea className="flex-1">
        {isLoading ? (
          <div className="flex h-64 items-center justify-center">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : notifications.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 py-20 text-muted-foreground">
            <Bell className="h-12 w-12 opacity-30" />
            <p className="text-sm">暂无通知</p>
          </div>
        ) : (
          <div className="divide-y">
            {notifications.map((notification) => (
              <NotificationItem
                key={notification.id}
                notification={notification}
                onMarkRead={() => markRead.mutate(notification.id)}
                onOpen={handleOpen}
              />
            ))}
          </div>
        )}
      </ScrollArea>
    </div>
  );
}

// ============================================
// 通知项组件
// ============================================
function NotificationItem({
  notification,
  onMarkRead,
  onOpen,
}: {
  notification: Notification;
  onMarkRead: () => void;
  onOpen: (notification: Notification) => void;
}) {
  const typeConfig = TYPE_CONFIG[notification.type as NotificationType] || TYPE_CONFIG.SYSTEM;
  const isUnread = !notification.isRead;
  // 有目标地址的通知整行可点，点击跳转到通知所在地
  const clickable = !!notification.actionUrl;

  return (
    <div
      onClick={clickable ? () => onOpen(notification) : undefined}
      title={clickable ? '点击查看' : undefined}
      className={`flex items-start gap-3 px-6 py-3.5 transition-colors hover:bg-muted/50 ${
        isUnread ? 'bg-primary/[0.03]' : ''
      } ${clickable ? 'cursor-pointer' : ''}`}
    >
      {/* 类型图标 */}
      <div className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-muted ${typeConfig.color}`}>
        {typeConfig.icon}
      </div>

      {/* 内容 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <h3 className={`text-sm ${isUnread ? 'font-semibold' : 'font-medium'}`}>
            {notification.title}
          </h3>
          {isUnread && (
            <span className="h-2 w-2 rounded-full bg-primary" />
          )}
        </div>
        {notification.content && (
          <p className="mt-0.5 text-xs text-muted-foreground line-clamp-2">
            {notification.content}
          </p>
        )}
        <div className="mt-1 flex items-center gap-2">
          <Badge variant="outline" className="text-[10px]">
            {typeConfig.label}
          </Badge>
          <span className="text-xs text-muted-foreground">
            {formatTime(notification.createdAt)}
          </span>
        </div>
      </div>

      {/* 跳转提示 */}
      {clickable && (
        <span className="mt-1 shrink-0 text-xs text-muted-foreground/60" title="点击查看">
          →
        </span>
      )}

      {/* 操作 */}
      {isUnread && (
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 shrink-0"
          onClick={(e) => { e.stopPropagation(); onMarkRead(); }}
          title="标记已读"
        >
          <Check className="h-3.5 w-3.5" />
        </Button>
      )}
    </div>
  );
}

// ============================================
// 时间格式化
// ============================================
function formatTime(dateStr: string): string {
  const date = new Date(dateStr);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const minutes = Math.floor(diff / 60000);
  const hours = Math.floor(diff / 3600000);
  const days = Math.floor(diff / 86400000);

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  if (hours < 24) return `${hours} 小时前`;
  if (days < 7) return `${days} 天前`;

  return date.toLocaleDateString('zh-CN');
}
