'use client';

import { useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { useDocuments, useCreateDocument, useUpdateDocument } from '@/lib/hooks/use-documents';
import { DocSidePanelView, type DocSidePanel } from './doc-side-panel';
import { History, MessageSquare } from 'lucide-react';
import {
  Plus,
  FileText,
  Search,
  Edit3,
  Trash2,
  BookOpen,
  Calendar,
} from 'lucide-react';
import type { Document, DocType } from '@/types';

// 文档类型配置
const DOC_TYPES = [
  { value: 'ALL', label: '全部' },
  { value: 'DOC', label: '文档' },
  { value: 'WIKI', label: 'Wiki' },
  { value: 'MEETING', label: '会议记录' },
  { value: 'REPORT', label: '报告' },
];

// ============================================
// 文档中心内容
// ============================================
export default function DocsContent() {
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [search, setSearch] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [editingDoc, setEditingDoc] = useState<Document | null>(null);

  const { data: docsRes, isLoading } = useDocuments(
    typeFilter !== 'ALL' ? { type: typeFilter } : undefined
  );
  const createDoc = useCreateDocument();

  const docs = docsRes?.data || [];

  const filteredDocs = search
    ? docs.filter((d) => d.title.toLowerCase().includes(search.toLowerCase()))
    : docs;

  // 创建表单
  const [newDoc, setNewDoc] = useState<{
    title: string;
    type: DocType;
    content: string;
  }>({ title: '', type: 'DOC', content: '' });
  const handleCreate = async () => {
    if (!newDoc.title.trim()) return;
    await createDoc.mutateAsync(newDoc);
    setNewDoc({ title: '', type: 'DOC', content: '' });
    setCreateOpen(false);
  };

  return (
    <div className="flex h-full flex-col">
      {/* 头部 */}
      <div className="flex flex-wrap items-center gap-3 border-b px-4 py-4 sm:px-6">
        <div className="flex items-center gap-2">
          <BookOpen className="h-5 w-5 text-primary" />
          <h1 className="text-lg font-semibold">文档中心</h1>
        </div>

        <div className="ml-auto flex w-full items-center gap-2 sm:w-auto">
          <Dialog open={createOpen} onOpenChange={setCreateOpen}>
            <DialogTrigger className="inline-flex w-full items-center justify-center gap-1.5 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-primary/90 sm:w-auto">
              <Plus className="h-4 w-4" />
              新建文档
            </DialogTrigger>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>新建文档</DialogTitle>
              </DialogHeader>
              <div className="space-y-4">
                <div>
                  <Label>标题</Label>
                  <Input
                    value={newDoc.title}
                    onChange={(e) => setNewDoc({ ...newDoc, title: e.target.value })}
                    placeholder="输入文档标题"
                  />
                </div>
                <div>
                  <Label>类型</Label>
                  <select
                    value={newDoc.type}
                    onChange={(e) => setNewDoc({ ...newDoc, type: e.target.value as DocType })}
                    className="flex h-9 w-full rounded-md border border-input bg-background px-3 py-1 text-sm shadow-sm"
                  >
                    <option value="DOC">文档</option>
                    <option value="WIKI">Wiki</option>
                    <option value="MEETING">会议记录</option>
                    <option value="REPORT">报告</option>
                  </select>
                </div>
                <div className="flex justify-end gap-2">
                  <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
                  <Button onClick={handleCreate} disabled={!newDoc.title.trim()}>创建</Button>
                </div>
              </div>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {/* 过滤 + 搜索 */}
      <div className="flex flex-wrap items-center gap-3 border-b px-4 py-3 sm:px-6">
        <div className="flex gap-1">
          {DOC_TYPES.map((t) => (
            <button
              key={t.value}
              onClick={() => setTypeFilter(t.value)}
              className={`rounded-md px-2.5 py-1 text-xs font-medium transition-colors ${
                typeFilter === t.value
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted text-muted-foreground hover:bg-muted/80'
              }`}
            >
              {t.label}
            </button>
          ))}
        </div>
        <div className="relative ml-auto w-full sm:w-56">
          <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="搜索文档..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="h-8 pl-8 text-xs"
          />
        </div>
      </div>

      {/* 文档列表 */}
      <ScrollArea className="flex-1 px-6 py-4">
        {isLoading ? (
          <div className="flex h-64 items-center justify-center">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : filteredDocs.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 py-20 text-muted-foreground">
            <FileText className="h-12 w-12 opacity-30" />
            <p className="text-sm">暂无文档</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
            {filteredDocs.map((doc) => (
              <DocCard key={doc.id} doc={doc} onEdit={() => setEditingDoc(doc)} />
            ))}
          </div>
        )}
      </ScrollArea>

      {/* 文档编辑器弹窗 */}
      {editingDoc && (
        <DocEditor
          doc={editingDoc}
          onClose={() => setEditingDoc(null)}
        />
      )}
    </div>
  );
}

// ============================================
// 文档卡片
// ============================================
function DocCard({ doc, onEdit }: { doc: Document; onEdit: () => void }) {
  const typeLabels: Record<string, string> = {
    DOC: '文档',
    WIKI: 'Wiki',
    MEETING: '会议',
    REPORT: '报告',
  };

  return (
    <Card className="group cursor-pointer hover:shadow-md transition-shadow" onClick={onEdit}>
      <CardContent className="p-4">
        <div className="flex items-start justify-between">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10">
            <FileText className="h-4.5 w-4.5 text-primary" />
          </div>
          <Badge variant="outline" className="text-[10px]">
            {typeLabels[doc.type] || doc.type}
          </Badge>
        </div>
        <h3 className="mt-3 text-sm font-medium line-clamp-2">{doc.title}</h3>
        {doc.content && (
          <p className="mt-1 text-xs text-muted-foreground line-clamp-2">
            {doc.content.substring(0, 100)}
          </p>
        )}
        <div className="mt-3 flex items-center gap-1.5 text-xs text-muted-foreground">
          <Calendar className="h-3 w-3" />
          {new Date(doc.updatedAt || doc.createdAt).toLocaleDateString('zh-CN')}
        </div>
      </CardContent>
    </Card>
  );
}

// ============================================
// 文档编辑器（简单 Markdown）
// ============================================
function DocEditor({ doc, onClose }: { doc: Document; onClose: () => void }) {
  const [content, setContent] = useState(doc.content || '');
  const [title, setTitle] = useState(doc.title);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [sidePanel, setSidePanel] = useState<DocSidePanel>('preview');
  const updateDoc = useUpdateDocument(doc.id);

  const handleSave = async () => {
    setSaving(true);
    setSaved(false);
    try {
      await updateDoc.mutateAsync({ title, content });
      setSaved(true);
      setTimeout(() => setSaved(false), 2000);
    } catch {
      alert('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-background">
      {/* 编辑器头部 */}
      <div className="flex items-center gap-3 border-b px-6 py-3">
        <Input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          className="h-8 border-0 p-0 text-lg font-semibold shadow-none focus-visible:ring-0"
        />
        <div className="ml-auto flex items-center gap-2">
          <Badge variant="outline" className="text-[10px]">
            {doc.isPublished ? '已发布' : '草稿'}
          </Badge>
          <Button
            variant={sidePanel === 'versions' ? 'default' : 'outline'}
            size="sm"
            className="gap-1"
            onClick={() => setSidePanel(sidePanel === 'versions' ? 'preview' : 'versions')}
          >
            <History className="h-3.5 w-3.5" /> 版本
          </Button>
          <Button
            variant={sidePanel === 'comments' ? 'default' : 'outline'}
            size="sm"
            className="gap-1"
            onClick={() => setSidePanel(sidePanel === 'comments' ? 'preview' : 'comments')}
          >
            <MessageSquare className="h-3.5 w-3.5" /> 评论
          </Button>
          <Button variant="outline" size="sm" onClick={onClose}>
            关闭
          </Button>
          {saved && <span className="text-xs text-green-600">已保存</span>}
          <Button size="sm" onClick={handleSave} disabled={saving}>
            {saving ? '保存中...' : '保存'}
          </Button>
        </div>
      </div>

      {/* Markdown 编辑器 */}
      <div className="flex flex-1 flex-col overflow-hidden md:flex-row">
        {/* 编辑区 */}
        <div className="flex-1 border-r">
          <div className="border-b px-4 py-2 text-xs font-medium text-muted-foreground">
            Markdown 编辑
          </div>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            className="h-full w-full resize-none p-4 text-sm font-mono focus:outline-none"
            placeholder="开始编写..."
          />
        </div>

        {/* 右侧栏：预览 / 版本历史 / 评论 */}
        <div className="flex flex-1 flex-col overflow-y-auto">
          <DocSidePanelView
            mode={sidePanel}
            documentId={doc.id}
            currentTitle={title}
            currentContent={content}
            currentUserId={doc.authorId}
            onRestored={(t, c) => { setTitle(t); setContent(c); setSaved(false); }}
          />
        </div>
      </div>
    </div>
  );
}
