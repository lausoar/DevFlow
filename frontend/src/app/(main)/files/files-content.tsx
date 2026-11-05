'use client';

import { useState, useRef } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useFiles, useUploadFile, useDeleteFile } from '@/lib/hooks/use-files';
import { fileApi } from '@/lib/api/file';
import {
  Upload,
  FileText,
  Image,
  FileCode,
  File,
  Download,
  Trash2,
  FolderOpen,
} from 'lucide-react';
import type { FileItem } from '@/types';

// ============================================
// 文件中心内容
// ============================================
export default function FilesContent() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const { data: filesRes, isLoading } = useFiles();
  const uploadFile = useUploadFile();
  const deleteFile = useDeleteFile();

  const handleDownload = async (file: FileItem) => {
    const objectName = file.url?.replace('/files/download/', '');
    if (!objectName) return;
    try {
      const blob = await fileApi.download(objectName, true);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = file.name || objectName;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('文件下载失败', err);
    }
  };

  const files = filesRes?.data?.records || [];
  const totalSize = files.reduce((acc, f) => acc + (f.size || 0), 0);

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const fileList = e.target.files;
    if (!fileList?.length) return;

    for (let i = 0; i < fileList.length; i++) {
      await uploadFile.mutateAsync({ file: fileList[i] });
    }

    // 清空 input
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  return (
    <div className="flex h-full flex-col">
      {/* 头部 */}
      <div className="flex items-center gap-4 border-b px-6 py-4">
        <div className="flex items-center gap-2">
          <FolderOpen className="h-5 w-5 text-primary" />
          <h1 className="text-lg font-semibold">文件中心</h1>
        </div>
        <span className="text-xs text-muted-foreground">
          {files.length} 个文件，共 {formatSize(totalSize)}
        </span>
        <div className="ml-auto">
          <input
            ref={fileInputRef}
            type="file"
            multiple
            className="hidden"
            onChange={handleUpload}
          />
          <Button
            size="sm"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploadFile.isPending}
          >
            <Upload className="mr-1.5 h-3.5 w-3.5" />
            {uploadFile.isPending ? '上传中...' : '上传文件'}
          </Button>
        </div>
      </div>

      {/* 文件列表 */}
      <ScrollArea className="flex-1 px-6 py-4">
        {isLoading ? (
          <div className="flex h-64 items-center justify-center">
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : files.length === 0 ? (
          <div className="flex flex-col items-center justify-center gap-3 py-20 text-muted-foreground">
            <FolderOpen className="h-12 w-12 opacity-30" />
            <p className="text-sm">暂无文件</p>
            <Button
              variant="outline"
              size="sm"
              onClick={() => fileInputRef.current?.click()}
            >
              上传第一个文件
            </Button>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
            {files.map((file) => (
              <FileCard
                key={file.id}
                file={file}
                onDelete={() => deleteFile.mutate(file.id)}
                onDownload={() => handleDownload(file)}
              />
            ))}
          </div>
        )}
      </ScrollArea>
    </div>
  );
}

// ============================================
// 文件卡片
// ============================================
function FileCard({ file, onDelete, onDownload }: { file: FileItem; onDelete: () => void; onDownload: () => void }) {
  const icon = getFileIcon(file.mimeType);

  return (
    <Card className="group cursor-pointer hover:shadow-md transition-shadow">
      <CardContent className="p-3">
        {/* 文件图标 */}
        <div className="flex h-16 items-center justify-center rounded-lg bg-muted/50">
          {icon}
        </div>

        {/* 文件名 */}
        <h4 className="mt-2 text-xs font-medium line-clamp-1" title={file.name}>
          {file.name}
        </h4>

        {/* 元信息 */}
        <div className="mt-1 flex items-center justify-between text-[10px] text-muted-foreground">
          <span>{formatSize(file.size)}</span>
          <span>{new Date(file.createdAt).toLocaleDateString('zh-CN')}</span>
        </div>

        {/* 操作按钮（悬浮显示） */}
        <div className="mt-2 flex gap-1 opacity-100 transition-opacity md:opacity-0 md:group-hover:opacity-100">
          <Button
            variant="outline"
            size="icon"
            className="h-6 w-6"
            title="下载"
            onClick={(e) => {
              e.stopPropagation();
              onDownload();
            }}
          >
            <Download className="h-3 w-3" />
          </Button>
          <Button
            variant="outline"
            size="icon"
            className="h-6 w-6 text-red-500 hover:text-red-600"
            title="删除"
            onClick={(e) => {
              e.stopPropagation();
              onDelete();
            }}
          >
            <Trash2 className="h-3 w-3" />
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

// ============================================
// 工具函数
// ============================================

function getFileIcon(mimeType: string): React.ReactNode {
  if (!mimeType) return <File className="h-8 w-8 text-muted-foreground" />;

  if (mimeType.startsWith('image/')) {
    return <Image className="h-8 w-8 text-green-500" />;
  }
  if (mimeType.includes('pdf')) {
    return <FileText className="h-8 w-8 text-red-500" />;
  }
  if (mimeType.includes('json') || mimeType.includes('javascript') || mimeType.includes('typescript')) {
    return <FileCode className="h-8 w-8 text-blue-500" />;
  }
  if (mimeType.includes('word') || mimeType.includes('document')) {
    return <FileText className="h-8 w-8 text-blue-600" />;
  }
  if (mimeType.includes('sheet') || mimeType.includes('excel')) {
    return <FileText className="h-8 w-8 text-green-600" />;
  }

  return <File className="h-8 w-8 text-muted-foreground" />;
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
}
