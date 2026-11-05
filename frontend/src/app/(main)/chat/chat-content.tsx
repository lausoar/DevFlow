'use client';

import { useState, useRef, useEffect } from 'react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { useQuery } from '@tanstack/react-query';
import { useChannels, useMessages, useSendMessage } from '@/lib/hooks/use-chat';
import { chatApi } from '@/lib/api/chat';
import { useAuthStore } from '@/stores';
import { usePresenceStore } from '@/stores/presence-store';
import { useChatUnreadStore } from '@/stores/chat-unread-store';
import { fileApi } from '@/lib/api/file';
import {
  Hash,
  Send,
  Search,
  Smile,
  Paperclip,
  MessageSquare,
  ChevronDown,
  FileText,
  Image as ImageIcon,
  Loader2,
  Users,
} from 'lucide-react';
import type { ChatChannel, ChatMessage, ProjectMember, Attachment } from '@/types';

// 常用表情列表
const EMOJI_LIST = [
  '😀', '😁', '😂', '🤣', '😊', '😍', '😘', '😎', '🤔', '😅',
  '😉', '🙂', '😢', '😭', '😡', '👍', '👎', '👏', '🙏', '💪',
  '🎉', '🔥', '❤️', '✅', '❌', '⭐', '💡', '🚀', '📌', '💯',
];

// 后端服务地址（含 /api/v1 上下文，文件接口同样位于其下）
const API_BASE = (process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1').replace(/\/$/, '');

// 将后端返回的相对路径（如 /files/download/xxx）转换为可访问的绝对地址
function resolveFileUrl(url?: string): string {
  if (!url) return '';
  if (/^https?:\/\//.test(url)) return url;
  return API_BASE + url;
}

// 解析消息内容：兼容「纯文本 / 旧版 {files} / 新版 {text, attachments} 信封」
interface ParsedContent {
  text?: string;
  attachments: Attachment[];
}
function parseMessageContent(content: string): ParsedContent | null {
  try {
    const obj = JSON.parse(content);
    if (obj && (Array.isArray(obj.files) || Array.isArray(obj.attachments))) {
      const attachments = (obj.files || obj.attachments) as Attachment[];
      return { text: obj.text, attachments };
    }
    return null;
  } catch {
    return null;
  }
}

// 格式化文件大小
function formatFileSize(bytes: number): string {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
}

// ============================================
// 聊天内容（客户端组件）
// ============================================
export default function ChatContent() {
  const [selectedChannel, setSelectedChannel] = useState<string | null>(null);
  const [messageInput, setMessageInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [mobileChannelOpen, setMobileChannelOpen] = useState(false);
  const [showEmoji, setShowEmoji] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [pendingAttachments, setPendingAttachments] = useState<Attachment[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { data: channelsRes } = useChannels();
  const channels = channelsRes?.data || [];

  // 支持通知等深链 /chat?channel=xxx 直达指定频道
  useEffect(() => {
    const ch = new URLSearchParams(window.location.search).get('channel');
    if (ch) setSelectedChannel(ch);
  }, []);
  const { user } = useAuthStore();
  const currentUserId = user?.id;
  const onlineUserIds = usePresenceStore((s) => s.onlineUserIds);

  // 群成员弹窗（项目群成员 = 项目成员，动态派生）
  const [membersOpen, setMembersOpen] = useState(false);
  const membersQuery = useQuery({
    queryKey: ['channel-members', selectedChannel],
    queryFn: () => chatApi.getChannelMembers(selectedChannel!),
    enabled: membersOpen && !!selectedChannel,
  });

  const selectedChannelData = channels.find((c) => c.id === selectedChannel);

  // 正在查看的频道不计数，进入即清零未读
  useEffect(() => {
    const store = useChatUnreadStore.getState();
    if (selectedChannel) {
      store.setActiveChannel(selectedChannel);
      store.clearChannel(selectedChannel);
    }
    return () => store.setActiveChannel(null);
  }, [selectedChannel]);

  // 发送消息 hook
  const sendMessage = useSendMessage(selectedChannel || '');

  // 发送消息处理函数（文本 + 暂存附件一起发送）
  const handleSendMessage = async () => {
    if (!selectedChannel) return;
    const text = messageInput.trim();
    if (!text && pendingAttachments.length === 0) return;

    try {
      const content =
        pendingAttachments.length > 0
          ? JSON.stringify({ text, attachments: pendingAttachments })
          : text;
      await sendMessage.mutateAsync({ content });
      setMessageInput('');
      setPendingAttachments([]);
    } catch {
      // 错误已在拦截器处理
    }
  };

  // 在光标处插入表情
  const handleInsertEmoji = (emoji: string) => {
    const input = inputRef.current;
    const start = input?.selectionStart ?? messageInput.length;
    const end = input?.selectionEnd ?? messageInput.length;
    const next = messageInput.slice(0, start) + emoji + messageInput.slice(end);
    setMessageInput(next);
    // 重置光标位置
    requestAnimationFrame(() => {
      if (input) {
        const pos = start + emoji.length;
        input.focus();
        input.setSelectionRange(pos, pos);
      }
    });
  };

  // 移除已暂存的附件
  const handleRemoveAttachment = (index: number) => {
    setPendingAttachments((prev) => prev.filter((_, i) => i !== index));
  };

  // 选择文件后上传并暂存（不直接发送，等待与文本一并发送）
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (!files || files.length === 0) {
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }
    setUploading(true);
    try {
      const uploaded: Attachment[] = [];
      for (const file of Array.from(files)) {
        const res = await fileApi.upload(file);
        const item = res.data;
        uploaded.push({
          id: item.id,
          name: item.name,
          url: item.url,
          size: item.size,
          mimeType: item.mimeType || file.type,
        });
      }
      setPendingAttachments((prev) => [...prev, ...uploaded]);
    } catch {
      // 错误已在拦截器处理
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  // 过滤频道
  const filteredChannels = searchQuery
    ? channels.filter((c) => c.name.toLowerCase().includes(searchQuery.toLowerCase()))
    : channels;

  // 自动选择第一个频道
  useEffect(() => {
    if (!selectedChannel && channels.length > 0) {
      setSelectedChannel(channels[0].id);
    }
  }, [channels, selectedChannel]);

  return (
    <div className="flex h-full min-h-[600px] flex-row overflow-hidden rounded-2xl border bg-card shadow-sm">
      {/* 左侧：频道列表（桌面/平板常驻） */}
      <div className="hidden w-64 shrink-0 flex-col border-r bg-muted/30 lg:flex">
        {/* 品牌标题 */}
        <div className="flex items-center gap-2 border-b px-4 py-3.5">
          <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <MessageSquare className="h-4 w-4" />
          </div>
          <span className="font-semibold">消息频道</span>
        </div>

        {/* 搜索 */}
        <div className="border-b p-3">
          <div className="relative">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              placeholder="搜索频道..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="h-8 pl-8 text-xs"
            />
          </div>
        </div>

        {/* 频道列表（固定高度内滚动） */}
        <div className="min-h-0 flex-1 overflow-y-auto">
          <div className="p-2">
            <div className="mb-1 px-2 text-xs font-medium text-muted-foreground">
              频道列表
            </div>
            {filteredChannels.length === 0 ? (
              <div className="flex flex-col items-center gap-2 py-8 text-muted-foreground">
                <MessageSquare className="h-8 w-8 opacity-30" />
                <p className="text-xs">暂无频道</p>
              </div>
            ) : (
              filteredChannels.map((channel) => (
                <ChannelItem
                  key={channel.id}
                  channel={channel}
                  isActive={channel.id === selectedChannel}
                  onClick={() => setSelectedChannel(channel.id)}
                />
              ))
            )}
          </div>
        </div>
      </div>

      {/* 右侧：消息区 */}
      <div className="flex min-h-0 flex-1 flex-col">
        {/* 移动端/平板频道切换（桌面端频道栏常驻，故隐藏） */}
        <div className="relative border-b lg:hidden">
          <button
            onClick={() => setMobileChannelOpen((o) => !o)}
            className="flex w-full items-center gap-2 px-4 py-3 text-left transition-colors hover:bg-muted/50"
          >
            <Hash className="h-4 w-4 text-muted-foreground" />
            <span className="font-medium">{selectedChannelData?.name || '选择频道'}</span>
            <ChevronDown
              className={`ml-auto h-4 w-4 text-muted-foreground transition-transform ${mobileChannelOpen ? 'rotate-180' : ''}`}
            />
          </button>
          {mobileChannelOpen && (
            <div className="max-h-64 overflow-y-auto border-t bg-popover p-2">
              {filteredChannels.length === 0 ? (
                <div className="py-6 text-center text-xs text-muted-foreground">暂无频道</div>
              ) : (
                filteredChannels.map((channel) => (
                  <button
                    key={channel.id}
                    onClick={() => {
                      setSelectedChannel(channel.id);
                      setMobileChannelOpen(false);
                    }}
                    className={`flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm transition-colors ${
                      channel.id === selectedChannel
                        ? 'bg-primary/10 font-medium text-primary'
                        : 'text-muted-foreground hover:bg-muted hover:text-foreground'
                    }`}
                  >
                    <Hash className="h-3.5 w-3.5 shrink-0" />
                    <span className="truncate">{channel.name}</span>
                  </button>
                ))
              )}
            </div>
          )}
        </div>

        {selectedChannel ? (
          <>
            {/* 频道头部 */}
            <div className="flex items-center gap-2 border-b px-4 py-3">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-muted">
                <Hash className="h-4 w-4 text-muted-foreground" />
              </div>
              <div className="min-w-0">
                <h2 className="truncate font-semibold leading-tight">
                  {selectedChannelData?.name || '频道'}
                </h2>
                <p className="text-xs text-muted-foreground">消息频道</p>
              </div>
              {selectedChannelData?.type && (
                <Badge variant="outline" className="text-[10px]">
                  {channelTypeLabel(selectedChannelData.type)}
                </Badge>
              )}
              {/* 查看群成员（项目群成员=项目成员） */}
              <button
                onClick={() => setMembersOpen(true)}
                title="查看群成员"
                className="ml-auto flex shrink-0 items-center gap-1.5 rounded-md px-2 py-1 text-xs text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
              >
                <Users className="h-3.5 w-3.5" />
                群成员
              </button>
            </div>

            {/* 消息列表（固定高度内滚动，超出隐藏） */}
            <MessageList
              channelId={selectedChannel}
              messagesEndRef={messagesEndRef}
              currentUserId={currentUserId}
            />

            {/* 消息输入 */}
            <div className="relative border-t p-3">
              {/* 表情选择面板 */}
              {showEmoji && (
                <>
                  <div
                    className="fixed inset-0 z-10"
                    onClick={() => setShowEmoji(false)}
                  />
                  <div className="absolute bottom-full left-3 z-20 mb-2 w-72 rounded-xl border bg-popover p-2 shadow-lg">
                    <div className="mb-1 flex items-center justify-between px-1">
                      <span className="text-xs font-medium text-muted-foreground">选择表情</span>
                      <button
                        type="button"
                        className="text-xs text-muted-foreground hover:text-foreground"
                        onClick={() => setShowEmoji(false)}
                      >
                        关闭
                      </button>
                    </div>
                    <div className="grid grid-cols-8 gap-1">
                      {EMOJI_LIST.map((emoji) => (
                        <button
                          key={emoji}
                          type="button"
                          className="flex h-8 w-8 items-center justify-center rounded-md text-lg transition-colors hover:bg-muted"
                          onClick={() => handleInsertEmoji(emoji)}
                        >
                          {emoji}
                        </button>
                      ))}
                    </div>
                  </div>
                </>
              )}

              <div className="flex items-end gap-1.5 rounded-2xl border bg-background p-1.5 transition-shadow focus-within:ring-2 focus-within:ring-ring">
                <input
                  ref={fileInputRef}
                  type="file"
                  multiple
                  className="hidden"
                  onChange={handleFileChange}
                />
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-9 w-9 shrink-0 rounded-full"
                  type="button"
                  disabled={uploading}
                  onClick={() => fileInputRef.current?.click()}
                  title="添加附件"
                >
                  {uploading ? (
                    <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                  ) : (
                    <Paperclip className="h-4 w-4 text-muted-foreground" />
                  )}
                </Button>
                {pendingAttachments.length > 0 && (
                  <div className="flex max-w-[60%] flex-wrap gap-1.5 overflow-y-auto">
                    {pendingAttachments.map((file, idx) => {
                      const isImage = (file.mimeType || '').startsWith('image/');
                      return (
                        <div
                          key={idx}
                          className="relative flex items-center gap-1 rounded-md border bg-muted/50 p-1 pr-5"
                        >
                          {isImage ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img
                              src={resolveFileUrl(file.url)}
                              alt={file.name}
                              className="h-7 w-7 rounded object-cover"
                            />
                          ) : (
                            <FileText className="h-4 w-4 shrink-0 text-primary" />
                          )}
                          <span className="max-w-24 truncate text-xs">{file.name}</span>
                          <button
                            type="button"
                            className="absolute right-0.5 top-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-muted-foreground/40 text-[10px] text-white hover:bg-muted-foreground/70"
                            onClick={() => handleRemoveAttachment(idx)}
                            title="移除"
                          >
                            ×
                          </button>
                        </div>
                      );
                    })}
                  </div>
                )}
                <Input
                  ref={inputRef}
                  placeholder={`发送消息到 #${selectedChannelData?.name || '频道'}，@成员名 通知对方，@AI 召唤助手...`}
                  value={messageInput}
                  onChange={(e) => setMessageInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && !e.shiftKey && (messageInput.trim() || pendingAttachments.length > 0)) {
                      e.preventDefault();
                      handleSendMessage();
                    }
                  }}
                  className="flex-1 border-0 bg-transparent px-1 shadow-none focus-visible:ring-0"
                />
                <Button
                  variant="ghost"
                  size="icon"
                  className={`h-9 w-9 shrink-0 rounded-full ${showEmoji ? 'bg-muted' : ''}`}
                  type="button"
                  onClick={() => setShowEmoji((o) => !o)}
                  title="表情"
                >
                  <Smile className="h-4 w-4 text-muted-foreground" />
                </Button>
                <Button
                  size="icon"
                  className="h-9 w-9 shrink-0 rounded-full"
                  disabled={(!messageInput.trim() && pendingAttachments.length === 0) || sendMessage.isPending}
                  onClick={handleSendMessage}
                  type="button"
                >
                  <Send className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </>
        ) : (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 text-muted-foreground">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-muted">
              <MessageSquare className="h-8 w-8 opacity-40" />
            </div>
            <p className="text-sm">选择一个频道开始聊天</p>
          </div>
        )}
      </div>

      {/* 群成员弹窗：项目群成员 = 项目成员（动态派生，加人/移除自动同步） */}
      <Dialog open={membersOpen} onOpenChange={setMembersOpen}>
        <DialogContent className="sm:max-w-sm max-h-[75vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>群成员</DialogTitle>
            <DialogDescription>
              「{selectedChannelData?.name || '频道'}」的成员即项目成员，项目里加人/移人会自动同步
            </DialogDescription>
          </DialogHeader>
          {membersQuery.isLoading ? (
            <div className="flex justify-center py-8">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : (
            <div className="space-y-1">
              {(membersQuery.data?.data || []).map((m: ProjectMember) => {
                const online = onlineUserIds.includes(m.userId);
                return (
                  <div key={m.id} className="flex items-center gap-2.5 rounded-md px-2 py-1.5 hover:bg-muted/50">
                    <div className="relative shrink-0">
                      <Avatar size="sm">
                        {m.avatar && <AvatarImage src={m.avatar} />}
                        <AvatarFallback>{(m.username || '?').charAt(0).toUpperCase()}</AvatarFallback>
                      </Avatar>
                      <span
                        title={online ? '在线' : '离线'}
                        className={`absolute -bottom-0.5 -right-0.5 h-2.5 w-2.5 rounded-full border-2 border-background ${
                          online ? 'bg-green-500' : 'bg-gray-300'
                        }`}
                      />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className="truncate text-sm font-medium">
                        {m.username || '未知用户'}
                        {m.userId === currentUserId && (
                          <span className="ml-1 text-[10px] text-muted-foreground">（我）</span>
                        )}
                      </p>
                      <p className="truncate text-[11px] text-muted-foreground">{m.email}</p>
                    </div>
                    <span className="shrink-0 text-[11px] text-muted-foreground">
                      {CHANNEL_ROLE_LABELS[m.role as keyof typeof CHANNEL_ROLE_LABELS] || '成员'}
                    </span>
                  </div>
                );
              })}
              {!membersQuery.isLoading && (membersQuery.data?.data || []).length === 0 && (
                <p className="py-4 text-center text-xs text-muted-foreground">暂无成员</p>
              )}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** 项目角色中文标签 */
const CHANNEL_ROLE_LABELS = {
  OWNER: '负责人',
  MANAGER: '管理员',
  TESTER: '测试人员',
  MEMBER: '成员',
} as const;

// ============================================
// 频道列表项
// ============================================
function ChannelItem({
  channel,
  isActive,
  onClick,
}: {
  channel: ChatChannel;
  isActive: boolean;
  onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex w-full items-center gap-2 rounded-md border-l-2 border-transparent px-2 py-1.5 text-left text-sm transition-colors ${
        isActive
          ? 'bg-primary/10 font-medium text-primary'
          : 'text-muted-foreground hover:bg-muted hover:text-foreground'
      }`}
    >
      <Hash className="h-3.5 w-3.5 shrink-0" />
      <span className="truncate">{channel.name}</span>
    </button>
  );
}

// ============================================
// 消息列表（固定高度，超出滚动）
// ============================================
function MessageList({
  channelId,
  messagesEndRef,
  currentUserId,
}: {
  channelId: string;
  messagesEndRef: React.RefObject<HTMLDivElement | null>;
  currentUserId?: string;
}) {
  const { data: messagesRes, isLoading } = useMessages(channelId);
  const messages = messagesRes?.data?.records || [];

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length, messagesEndRef]);

  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center">
        <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  if (messages.length === 0) {
    return (
      <div className="flex flex-1 flex-col items-center justify-center gap-2 text-muted-foreground">
        <MessageSquare className="h-10 w-10 opacity-30" />
        <p className="text-sm">暂无消息，发送第一条吧！</p>
      </div>
    );
  }

  return (
    <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
      <div className="space-y-4">
        {messages.map((msg) => (
          <MessageItem key={msg.id} message={msg} currentUserId={currentUserId} />
        ))}
        <div ref={messagesEndRef} />
      </div>
    </div>
  );
}

// ============================================
// 消息项（区分自己 / 他人）
// ============================================
function MessageItem({
  message,
  currentUserId,
}: {
  message: ChatMessage;
  currentUserId?: string;
}) {
  const displayName =
    message.senderName || message.senderId?.substring(0, 8) || 'Unknown';
  const isSelf =
    !!message.senderId && !!currentUserId && message.senderId === currentUserId;
  const time = new Date(message.createdAt).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  });

  if (isSelf) {
    return (
      <div className="flex flex-row-reverse gap-3">
        <Avatar className="h-8 w-8 shrink-0">
          <AvatarImage src={message.senderAvatar || undefined} />
          <AvatarFallback className="text-xs">
            {displayName.charAt(0).toUpperCase()}
          </AvatarFallback>
        </Avatar>
        <div className="flex max-w-[75%] flex-col items-end">
          <div className="mb-0.5 flex items-baseline gap-2">
            <span className="text-xs text-muted-foreground">{time}</span>
            <span className="text-sm font-medium">{displayName}</span>
          </div>
          <div className="break-words rounded-2xl rounded-tr-sm bg-primary px-3 py-2 text-sm text-primary-foreground">
            <MessageContent message={message} />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex gap-3">
      <Avatar className="h-8 w-8 shrink-0">
        <AvatarImage src={message.senderAvatar || undefined} />
        <AvatarFallback className="text-xs">
          {displayName.charAt(0).toUpperCase()}
        </AvatarFallback>
      </Avatar>
      <div className="flex max-w-[75%] flex-col items-start">
        <div className="mb-0.5 flex items-baseline gap-2">
          <span className="text-sm font-medium">{displayName}</span>
          <span className="text-xs text-muted-foreground">{time}</span>
        </div>
          <div className="break-words rounded-2xl rounded-tl-sm bg-muted px-3 py-2 text-sm">
            <MessageContent message={message} />
          </div>
      </div>
    </div>
  );
}

// ============================================
// 频道类型标签
// ============================================
function channelTypeLabel(type: string): string {
  switch (type) {
    case 'PROJECT': return '项目';
    case 'DEPARTMENT': return '部门';
    case 'ENTERPRISE': return '企业';
    case 'PRIVATE': return '私聊';
    default: return type;
  }
}

// ============================================
// 消息内容（文本 / 附件）
// ============================================
function MessageContent({ message }: { message: ChatMessage }) {
  const parsed = parseMessageContent(message.content);

  // 含附件的消息：渲染文本 + 附件卡片
  if (parsed && parsed.attachments.length > 0) {
    return (
      <div className="flex flex-col gap-2">
        {parsed.text ? <span className="whitespace-pre-wrap">{parsed.text}</span> : null}
        {parsed.attachments.map((file, idx) => {
          const isImage = (file.mimeType || '').startsWith('image/');
          const fileUrl = resolveFileUrl(file.url);
          if (isImage) {
            return (
              <a
                key={idx}
                href={fileUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="block overflow-hidden rounded-lg"
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={fileUrl}
                  alt={file.name}
                  className="max-h-64 w-auto rounded-lg object-cover"
                />
              </a>
            );
          }
          return (
            <a
              key={idx}
              href={fileUrl}
              target="_blank"
              rel="noopener noreferrer"
              download={file.name}
              className="flex items-center gap-2 rounded-lg bg-background/80 px-3 py-2 transition-colors hover:bg-background"
            >
              <FileText className="h-5 w-5 shrink-0 text-primary" />
              <div className="min-w-0 flex-1">
                <div className="truncate text-sm font-medium text-foreground">
                  {file.name}
                </div>
                {file.size ? (
                  <div className="text-xs text-muted-foreground">
                    {formatFileSize(file.size)}
                  </div>
                ) : null}
              </div>
              <ImageIcon className="h-4 w-4 shrink-0 text-muted-foreground" />
            </a>
          );
        })}
      </div>
    );
  }

  // 纯文本消息直接展示
  return <>{message.content}</>;
}
