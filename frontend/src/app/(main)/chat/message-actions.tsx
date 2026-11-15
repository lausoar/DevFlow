'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Check, Pencil, Trash2, Smile, RotateCcw, X } from 'lucide-react';
import type { ChatMessage } from '@/types';

/**
 * 消息气泡的悬停操作条 + 表情条 + 就地编辑。
 *
 * 交互取舍：
 * - 悬停出现操作条（移动端常显），而不是右键菜单 —— 右键在触屏上不可用；
 * - 表情条是固定的一组常用表情，不做全量 emoji picker（企业 IM 高频就那么几个）；
 * - 编辑是"就地替换气泡内容"，不弹窗，减少打断。
 */

const QUICK_EMOJIS = ['👍', '👏', '🎉', '❤️', '👀', '😄'];

export function MessageActions({
  message,
  isSelf,
  onEdit,
  onRecall,
  onReact,
}: {
  message: ChatMessage;
  isSelf: boolean;
  onEdit: (content: string) => Promise<void>;
  onRecall: () => void;
  onReact: (emoji: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(message.content);
  const [showEmoji, setShowEmoji] = useState(false);

  const recalled = !!message.recalledAt;

  const handleSave = async () => {
    const next = draft.trim();
    if (!next || next === message.content) {
      setEditing(false);
      return;
    }
    await onEdit(next);
    setEditing(false);
  };

  if (recalled) {
    return null;
  }

  if (editing) {
    return (
      <div className="mt-1 flex w-full items-center gap-1.5">
        <Input
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') handleSave();
            if (e.key === 'Escape') setEditing(false);
          }}
          className="h-8 text-sm"
          autoFocus
        />
        <Button size="icon" variant="ghost" className="h-7 w-7" onClick={handleSave} title="保存">
          <Check className="h-3.5 w-3.5" />
        </Button>
        <Button
          size="icon" variant="ghost" className="h-7 w-7"
          onClick={() => { setEditing(false); setDraft(message.content); }}
          title="取消"
        >
          <X className="h-3.5 w-3.5" />
        </Button>
      </div>
    );
  }

  return (
    <div className="mt-1 flex items-center gap-1 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100">
      <div className="relative">
        <Button
          size="icon" variant="ghost" className="h-6 w-6"
          onClick={() => setShowEmoji((v) => !v)}
          title="添加表情"
        >
          <Smile className="h-3.5 w-3.5" />
        </Button>
        {showEmoji && (
          <div className="absolute bottom-7 left-0 z-20 flex gap-0.5 rounded-md border bg-popover p-1 shadow-md">
            {QUICK_EMOJIS.map((emoji) => (
              <button
                key={emoji}
                className="rounded px-1 text-base hover:bg-muted"
                onClick={() => { onReact(emoji); setShowEmoji(false); }}
              >
                {emoji}
              </button>
            ))}
          </div>
        )}
      </div>

      {isSelf && (
        <Button
          size="icon" variant="ghost" className="h-6 w-6"
          onClick={() => {
            // 时间窗在点击时才判断（渲染期读取当前时间会让组件不纯）
            const withinWindow = Date.now() - new Date(message.createdAt).getTime() < 10 * 60 * 1000;
            if (!withinWindow) {
              alert('超过 10 分钟的消息不可编辑，可撤回后重发');
              return;
            }
            setDraft(message.content);
            setEditing(true);
          }}
          title="编辑"
        >
          <Pencil className="h-3.5 w-3.5" />
        </Button>
      )}
      {isSelf && (
        <Button
          size="icon" variant="ghost" className="h-6 w-6 text-destructive hover:text-destructive"
          onClick={() => {
            if (confirm('撤回后消息内容将不可见（原文保留供审计）。确认撤回？')) onRecall();
          }}
          title="撤回"
        >
          <RotateCcw className="h-3.5 w-3.5" />
        </Button>
      )}
    </div>
  );
}

/** 消息下方的表情聚合条：点击即切换自己对该表情的回应 */
export function ReactionBar({
  reactions,
  currentUserId,
  onToggle,
}: {
  reactions?: Record<string, string[]>;
  currentUserId?: string;
  onToggle: (emoji: string) => void;
}) {
  const entries = Object.entries(reactions || {}).filter(([, users]) => users.length > 0);
  if (entries.length === 0) return null;

  return (
    <div className="mt-1 flex flex-wrap gap-1">
      {entries.map(([emoji, users]) => {
        const mine = !!currentUserId && users.includes(currentUserId);
        return (
          <button
            key={emoji}
            onClick={() => onToggle(emoji)}
            className={`flex items-center gap-1 rounded-full border px-1.5 py-0.5 text-[11px] transition-colors ${
              mine ? 'border-primary bg-brand/10 text-brand' : 'bg-muted/60 text-muted-foreground hover:bg-muted'
            }`}
            title={users.length > 3 ? `${users.slice(0, 3).join(', ')} 等 ${users.length} 人` : users.join(', ')}
          >
            <span>{emoji}</span>
            <span>{users.length}</span>
          </button>
        );
      })}
    </div>
  );
}

/** 撤回消息的占位气泡 */
export function RecalledBubble({ isSelf }: { isSelf: boolean }) {
  return (
    <div
      className={`rounded-2xl px-3 py-2 text-xs italic ${
        isSelf ? 'rounded-tr-sm bg-muted text-muted-foreground' : 'rounded-tl-sm bg-muted text-muted-foreground'
      }`}
    >
      此消息已撤回
      <Trash2 className="ml-1 inline h-3 w-3" />
    </div>
  );
}
