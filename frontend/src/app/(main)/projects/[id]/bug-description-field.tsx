'use client';

import { useRef, useState } from 'react';
import { Button } from '@/components/ui/button';
import { fileApi } from '@/lib/api/file';
import { ImagePlus, Loader2 } from 'lucide-react';

// ============================================
// Bug 描述字段（新建/编辑弹窗共用）
// 描述为 Markdown 文本，支持插入截图：选图或 Ctrl+V 粘贴图片
// 自动上传后在光标处插入 ![截图](url)，详情页按同格式渲染为内嵌图片
// ============================================
export function BugDescriptionField({
  projectId,
  value,
  onChange,
  label = '描述',
  placeholder = '描述复现步骤和预期行为，可直接粘贴截图…',
}: {
  projectId: string;
  value: string;
  onChange: (text: string) => void;
  label?: string;
  placeholder?: string;
}) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  /** 在光标处插入文本（无光标时追加到末尾） */
  const insertAtCursor = (text: string) => {
    const el = textareaRef.current;
    if (!el) {
      onChange(value + text);
      return;
    }
    const start = el.selectionStart ?? value.length;
    const end = el.selectionEnd ?? start;
    const next = value.slice(0, start) + text + value.slice(end);
    onChange(next);
    requestAnimationFrame(() => {
      el.focus();
      const pos = start + text.length;
      el.setSelectionRange(pos, pos);
    });
  };

  const uploadAndInsert = async (files: FileList | File[]) => {
    const images = Array.from(files).filter((f) => f.type.startsWith('image/'));
    if (images.length === 0) {
      setError('只能插入图片文件');
      return;
    }
    setError(null);
    setUploading(true);
    try {
      for (const file of images) {
        const res = await fileApi.upload(file, projectId);
        if (res.data?.url) {
          insertAtCursor(`\n![截图](${res.data.url})\n`);
        }
      }
    } catch {
      setError('图片上传失败，请重试');
    } finally {
      setUploading(false);
      if (fileRef.current) fileRef.current.value = '';
    }
  };

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <label className="text-sm font-medium">{label}</label>
        {/* 插入截图：选图上传后在光标处插入 */}
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          multiple
          className="hidden"
          onChange={(e) => {
            if (e.target.files && e.target.files.length > 0) {
              uploadAndInsert(e.target.files);
            }
          }}
        />
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="h-7 gap-1 text-xs text-muted-foreground hover:text-foreground"
          disabled={uploading}
          onClick={() => fileRef.current?.click()}
          title="上传图片并插入到描述（也可以直接在描述里粘贴截图）"
        >
          {uploading ? (
            <Loader2 className="h-3 w-3 animate-spin" />
          ) : (
            <ImagePlus className="h-3 w-3" />
          )}
          {uploading ? '上传中...' : '插入截图'}
        </Button>
      </div>

      <textarea
        ref={textareaRef}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onPaste={(e) => {
          // 剪贴板里有图片时拦截粘贴：自动上传并插入
          const files = Array.from(e.clipboardData?.files || []).filter((f) =>
            f.type.startsWith('image/'),
          );
          if (files.length > 0) {
            e.preventDefault();
            uploadAndInsert(files);
          }
        }}
        placeholder={placeholder}
        className="flex min-h-[100px] w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      />

      {error && <p className="text-[11px] text-destructive">{error}</p>}
      <p className="text-[11px] text-muted-foreground">
        支持粘贴截图（Ctrl+V）或点「插入截图」上传，图片会显示在描述里
      </p>
    </div>
  );
}
