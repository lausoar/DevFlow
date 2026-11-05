'use client';

import { useState, useRef, useEffect } from 'react';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { useTaskComments, useCreateComment, useDeleteComment } from '@/lib/hooks/use-task-comments';
import { useAuthStore } from '@/stores/auth-store';
import { MessageSquare, Send, Trash2, Reply, Loader2, CornerDownRight } from 'lucide-react';
import type { TaskComment } from '@/types';

// ============================================
// 任务评论面板
// ============================================
export function TaskCommentsPanel({ taskId }: { taskId: string }) {
  const { user } = useAuthStore();
  const [content, setContent] = useState('');
  const [replyTo, setReplyTo] = useState<{ id: string; username: string } | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  const { data: commentsRes, isLoading } = useTaskComments(taskId);
  const createComment = useCreateComment(taskId);
  const deleteComment = useDeleteComment(taskId);
  const comments: TaskComment[] = commentsRes?.data || [];

  // 发送评论
  const handleSend = async () => {
    if (!content.trim()) return;
    try {
      await createComment.mutateAsync({
        content: content.trim(),
        replyToId: replyTo?.id,
      });
      setContent('');
      setReplyTo(null);
    } catch {
      // 错误已在拦截器处理
    }
  };

  // 回车发送（Shift+Enter 换行）
  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  // 自动滚动到底部
  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [comments.length]);

  return (
    <div className="flex flex-col h-full">
      {/* 标题 */}
      <div className="flex items-center gap-2 px-3 py-2 border-b border-border">
        <MessageSquare className="h-4 w-4 text-muted-foreground" />
        <span className="text-sm font-medium">评论</span>
        <span className="text-xs text-muted-foreground">({comments.length})</span>
      </div>

      {/* 评论列表 */}
      <div ref={listRef} className="flex-1 overflow-y-auto px-3 py-2 space-y-3 max-h-[300px]">
        {isLoading && (
          <div className="flex justify-center py-4">
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          </div>
        )}

        {!isLoading && comments.length === 0 && (
          <div className="text-center text-xs text-muted-foreground py-4">
            暂无评论，来说点什么吧
          </div>
        )}

        {comments.map((comment) => (
          <CommentItem
            key={comment.id}
            comment={comment}
            currentUserId={user?.id}
            onReply={(username) => setReplyTo({ id: comment.id, username })}
            onDelete={() => deleteComment.mutate(comment.id)}
          />
        ))}
      </div>

      {/* 输入区域 */}
      <div className="border-t border-border px-3 py-2 space-y-2">
        {/* 回复提示 */}
        {replyTo && (
          <div className="flex items-center gap-2 text-xs text-muted-foreground bg-muted/50 rounded px-2 py-1">
            <CornerDownRight className="h-3 w-3" />
            <span>回复 <strong>{replyTo.username}</strong></span>
            <Button
              variant="ghost"
              size="sm"
              className="h-4 px-1 text-xs ml-auto"
              onClick={() => setReplyTo(null)}
            >
              取消
            </Button>
          </div>
        )}

        <div className="flex gap-2">
          <Avatar className="h-7 w-7 shrink-0">
            <AvatarImage src={user?.avatar || undefined} />
            <AvatarFallback className="text-xs">
              {user?.username?.charAt(0)?.toUpperCase() || 'U'}
            </AvatarFallback>
          </Avatar>
          <Textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入评论...（Enter 发送，Shift+Enter 换行）"
            className="min-h-[60px] text-sm resize-none"
            disabled={createComment.isPending}
          />
        </div>
        <div className="flex justify-end">
          <Button
            size="sm"
            onClick={handleSend}
            disabled={!content.trim() || createComment.isPending}
            className="gap-1"
          >
            {createComment.isPending ? (
              <Loader2 className="h-3 w-3 animate-spin" />
            ) : (
              <Send className="h-3 w-3" />
            )}
            发送
          </Button>
        </div>
      </div>
    </div>
  );
}

// ============================================
// 单条评论项
// ============================================
function CommentItem({
  comment,
  currentUserId,
  onReply,
  onDelete,
}: {
  comment: TaskComment;
  currentUserId?: string;
  onReply: (username: string) => void;
  onDelete: () => void;
}) {
  const [showActions, setShowActions] = useState(false);
  const isOwner = comment.userId === currentUserId;
  const displayName = comment.username || comment.userId.slice(0, 6);

  return (
    <div
      className="group flex gap-2"
      onMouseEnter={() => setShowActions(true)}
      onMouseLeave={() => setShowActions(false)}
    >
      <Avatar className="h-7 w-7 shrink-0">
        <AvatarImage src={comment.avatar || undefined} />
        <AvatarFallback className="text-xs">
          {displayName.charAt(0).toUpperCase()}
        </AvatarFallback>
      </Avatar>
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{displayName}</span>
          <span className="text-xs text-muted-foreground">
            {formatTime(comment.createdAt)}
          </span>
          {/* 操作按钮 */}
          {showActions && (
            <div className="flex gap-1 ml-auto">
              <Button
                variant="ghost"
                size="sm"
                className="h-5 px-1"
                onClick={() => onReply(displayName)}
              >
                <Reply className="h-3 w-3" />
              </Button>
              {isOwner && (
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-5 px-1 text-red-500 hover:text-red-600"
                  onClick={onDelete}
                >
                  <Trash2 className="h-3 w-3" />
                </Button>
              )}
            </div>
          )}
        </div>
        <p className="text-sm text-foreground whitespace-pre-wrap break-words mt-0.5">
          {comment.content}
        </p>
      </div>
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

  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  const days = Math.floor(hours / 24);
  if (days < 7) return `${days} 天前`;

  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}
