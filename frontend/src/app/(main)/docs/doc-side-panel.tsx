import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { docExtraApi, type DocumentVersionInfo, type DocumentCommentInfo } from '@/lib/api/document';
import {
  useDocVersions,
  useRestoreDocVersion,
  useDocComments,
  useAddDocComment,
  useDeleteDocComment,
} from '@/lib/hooks/use-doc-extras';
import { History, MessageSquare, RotateCcw, Trash2, Loader2 } from 'lucide-react';

// ============================================
// 文档右侧面板：版本历史 / 评论（DocEditor 内切换）
// ============================================
export type DocSidePanel = 'preview' | 'versions' | 'comments';

export function DocSidePanelView({
  mode,
  documentId,
  currentTitle,
  currentContent,
  onRestored,
  currentUserId,
}: {
  mode: DocSidePanel;
  documentId: string;
  currentTitle: string;
  currentContent: string;
  /** 恢复版本后回调（父组件刷新编辑器内容） */
  onRestored?: (title: string, content: string) => void;
  currentUserId?: string;
}) {
  if (mode === 'versions') {
    return <VersionsPanel documentId={documentId} currentTitle={currentTitle} currentContent={currentContent} onRestored={onRestored} />;
  }
  if (mode === 'comments') {
    return <CommentsPanel documentId={documentId} currentUserId={currentUserId} />;
  }
  return (
    <>
      <div className="border-b px-4 py-2 text-xs font-medium text-muted-foreground">预览</div>
      <div className="p-4 text-sm prose prose-sm dark:prose-invert max-w-none">
        {currentContent || <span className="text-muted-foreground">预览区域</span>}
      </div>
    </>
  );
}

// ============================================
// 版本历史面板
// ============================================
function VersionsPanel({
  documentId,
  currentTitle,
  currentContent,
  onRestored,
}: {
  documentId: string;
  currentTitle: string;
  currentContent: string;
  onRestored?: (title: string, content: string) => void;
}) {
  const { data: res, isLoading } = useDocVersions(documentId);
  const restore = useRestoreDocVersion(documentId);
  const [preview, setPreview] = useState<{ version: number; title: string; content: string } | null>(null);

  const versions: DocumentVersionInfo[] = res?.data || [];

  const handleRestore = async (v: DocumentVersionInfo) => {
    if (!confirm(`确定要恢复到版本 ${v.version}（「${v.title}」）吗？\n当前内容会先自动保存为一个新版本。`)) return;
    try {
      const detail = await docExtraApi.versionDetail(documentId, v.id);
      await restore.mutateAsync(v.id);
      onRestored?.(detail.data.title, detail.data.content || '');
      setPreview(null);
      alert(`已恢复到版本 ${v.version}`);
    } catch (e) {
      alert((e as { response?: { data?: { message?: string } } })?.response?.data?.message || '恢复失败');
    }
  };

  const loadPreview = async (v: DocumentVersionInfo) => {
    try {
      const detail = await docExtraApi.versionDetail(documentId, v.id);
      setPreview({ version: v.version, title: detail.data.title, content: detail.data.content || '' });
    } catch {
      setPreview({ version: v.version, title: v.title, content: '(加载失败)' });
    }
  };

  return (
    <>
      <div className="flex items-center justify-between border-b px-4 py-2">
        <span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
          <History className="h-3.5 w-3.5" /> 版本历史（保存前的内容快照）
        </span>
        {preview && (
          <Button variant="ghost" size="sm" className="h-6 text-xs" onClick={() => setPreview(null)}>
            返回列表
          </Button>
        )}
      </div>

      {preview ? (
        <div className="flex flex-1 flex-col overflow-hidden">
          <div className="border-b bg-muted/30 px-4 py-2 text-xs text-muted-foreground">
            版本 {preview.version} · {preview.title}
          </div>
          <pre className="flex-1 overflow-auto whitespace-pre-wrap p-4 text-xs">{preview.content || '（空）'}</pre>
        </div>
      ) : (
        <div className="flex-1 space-y-1.5 overflow-y-auto p-3">
          {isLoading ? (
            <p className="py-6 text-center text-xs text-muted-foreground">加载中...</p>
          ) : versions.length === 0 ? (
            <p className="py-6 text-center text-xs text-muted-foreground">
              还没有历史版本——下次保存后会自动保存本次内容为版本快照
            </p>
          ) : (
            versions.map((v) => (
              <div key={v.id} className="rounded-md border px-3 py-2">
                <div className="flex items-center gap-2">
                  <Badge variant="outline" className="text-[10px]">v{v.version}</Badge>
                  <span className="min-w-0 flex-1 truncate text-xs font-medium">{v.title}</span>
                  <Button
                    variant="outline"
                    size="sm"
                    className="h-6 shrink-0 gap-1 text-[11px]"
                    disabled={restore.isPending}
                    onClick={() => handleRestore(v)}
                  >
                    <RotateCcw className="h-3 w-3" /> 恢复
                  </Button>
                </div>
                <div className="mt-1 flex items-center gap-2 text-[10px] text-muted-foreground">
                  <span>{v.editorName || '未知'}</span>
                  <span>·</span>
                  <span>{new Date(v.createdAt).toLocaleString('zh-CN')}</span>
                  <button className="ml-auto hover:text-foreground hover:underline" onClick={() => loadPreview(v)}>
                    预览
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </>
  );
}

// ============================================
// 评论面板
// ============================================
function CommentsPanel({ documentId, currentUserId }: { documentId: string; currentUserId?: string }) {
  const { data: res, isLoading } = useDocComments(documentId);
  const addComment = useAddDocComment(documentId);
  const deleteComment = useDeleteDocComment(documentId);
  const [content, setContent] = useState('');
  const [sending, setSending] = useState(false);

  const comments: DocumentCommentInfo[] = res?.data || [];

  const handleSend = async () => {
    const text = content.trim();
    if (!text) return;
    setSending(true);
    try {
      await addComment.mutateAsync(text);
      setContent('');
    } catch {
      alert('评论失败，请重试');
    } finally {
      setSending(false);
    }
  };

  return (
    <>
      <div className="flex items-center gap-1.5 border-b px-4 py-2 text-xs font-medium text-muted-foreground">
        <MessageSquare className="h-3.5 w-3.5" /> 评论（{comments.length}）
      </div>

      <div className="flex-1 space-y-2 overflow-y-auto p-3">
        {isLoading ? (
          <p className="py-6 text-center text-xs text-muted-foreground">加载中...</p>
        ) : comments.length === 0 ? (
          <p className="py-6 text-center text-xs text-muted-foreground">还没有评论</p>
        ) : (
          comments.map((c) => (
            <div key={c.id} className="group rounded-md border bg-muted/30 p-2.5">
              <div className="flex items-center gap-2">
                <span className="text-xs font-medium">{c.userName || '未知用户'}</span>
                {c.userId === currentUserId && (
                  <span className="text-[10px] text-muted-foreground">（我）</span>
                )}
                <span className="ml-auto text-[10px] text-muted-foreground">
                  {new Date(c.createdAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                </span>
                {c.userId === currentUserId && (
                  <button
                    className="shrink-0 text-muted-foreground opacity-0 transition-opacity hover:text-destructive group-hover:opacity-100"
                    title="删除"
                    onClick={async () => {
                      if (!confirm('确定删除这条评论吗？')) return;
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
              <p className="mt-1 whitespace-pre-wrap text-xs text-foreground/90">{c.content}</p>
            </div>
          ))
        )}
      </div>

      <div className="border-t p-3">
        <div className="flex items-center gap-2">
          <Input
            value={content}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey && content.trim()) { e.preventDefault(); handleSend(); } }}
            placeholder="写下评论，回车发送"
            className="h-8 text-xs"
          />
          <Button size="sm" className="h-8 shrink-0 text-xs" disabled={sending || !content.trim()} onClick={handleSend}>
            {sending ? <Loader2 className="h-3 w-3 animate-spin" /> : '发送'}
          </Button>
        </div>
      </div>
    </>
  );
}
