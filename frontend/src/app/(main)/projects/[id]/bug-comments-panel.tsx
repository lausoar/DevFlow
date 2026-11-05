'use client';

import { useEffect, useRef, useState } from 'react';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { useBugComments, useCreateBugComment, useDeleteBugComment } from '@/lib/hooks/use-bug-comments';
import { useAuthStore } from '@/stores/auth-store';
import { MessageSquare, Send, Trash2, Loader2 } from 'lucide-react';
import type { BugCommentInfo } from '@/lib/api/bug-comment';

// ============================================
// Bug 评论面板（详情弹窗内）
// 支持 @提及：@用户名 或 @真实姓名，被提及的项目成员会收到通知
// ============================================
export function BugCommentsPanel({ bugId }: { bugId: string }) {
  const { user } = useAuthStore();
  const [content, setContent] = useState('');
  const listRef = useRef<HTMLDivElement>(null);

  const { data: commentsRes, isLoading } = useBugComments(bugId);
  const createComment = useCreateBugComment(bugId);
  const deleteComment = useDeleteBugComment(bugId);
  const comments: BugCommentInfo[] = commentsRes?.data || [];

  const handleSend = async () => {
    const text = content.trim();
    if (!text) return;
    try {
      await createComment.mutateAsync(text);
      setContent('');
    } catch {
      // 错误已在拦截器处理
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  useEffect(() => {
    if (listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [comments.length]);

  /** 评论内容渲染：@提及 高亮 */
  const renderContent = (text: string) => {
    const parts = text.split(/(@[A-Za-z0-9_.\-\u4e00-\u9fa5]+)/g);
    return parts.map((part, i) =>
      part.startsWith('@') ? (
        <span key={i} className="font-medium text-blue-600">{part}</span>
      ) : (
        <span key={i}>{part}</span>
      ),
    );
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1.5 text-sm font-medium">
          <MessageSquare className="h-3.5 w-3.5 text-muted-foreground" />
          评论（{comments.length}）
        </span>
        <span className="text-[11px] text-muted-foreground">支持 @用户名 或 @真实姓名 提醒对方</span>
      </div>

      <div ref={listRef} className="max-h-40 space-y-2 overflow-y-auto">
        {isLoading ? (
          <p className="py-3 text-center text-xs text-muted-foreground">加载中...</p>
        ) : comments.length === 0 ? (
          <p className="py-3 text-center text-xs text-muted-foreground">还没有评论</p>
        ) : (
          comments.map((c) => (
            <div key={c.id} className="group flex items-start gap-2 rounded-md border bg-muted/30 p-2">
              <Avatar className="h-6 w-6">
                {c.userAvatar && <AvatarImage src={c.userAvatar} />}
                <AvatarFallback className="text-[10px]">{(c.userName || '?').charAt(0)}</AvatarFallback>
              </Avatar>
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="text-xs font-medium">
                    {c.userName || '未知用户'}
                    {c.userId === user?.id && <span className="ml-1 text-[10px] text-muted-foreground">（我）</span>}
                  </span>
                  <span className="text-[10px] text-muted-foreground">
                    {new Date(c.createdAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                  </span>
                  {c.userId === user?.id && (
                    <button
                      className="ml-auto text-muted-foreground opacity-0 transition-opacity hover:text-destructive group-hover:opacity-100"
                      title="删除"
                      onClick={async () => {
                        try {
                          await deleteComment.mutateAsync(c.id);
                        } catch {
                          alert('删除失败');
                        }
                      }}
                    >
                      <Trash2 className="h-3 w-3" />
                    </button>
                  )}
                </div>
                <p className="mt-0.5 whitespace-pre-wrap break-words text-xs text-foreground/90">
                  {renderContent(c.content)}
                </p>
              </div>
            </div>
          ))
        )}
      </div>

      <div className="flex items-start gap-2">
        <Textarea
          value={content}
          onChange={(e) => setContent(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="写下评论…（@成员名 可提醒对方，Shift+Enter 换行）"
          className="min-h-[60px] text-xs"
        />
        <Button size="sm" className="h-8 shrink-0 px-3" disabled={!content.trim() || createComment.isPending} onClick={handleSend}>
          {createComment.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Send className="h-3.5 w-3.5" />}
        </Button>
      </div>
    </div>
  );
}
