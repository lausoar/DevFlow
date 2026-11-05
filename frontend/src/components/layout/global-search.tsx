'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { searchApi, type SearchResultItem } from '@/lib/api/search';
import { useAppStore } from '@/stores';
import type { GlobalSearchResult } from '@/lib/api/search';
import {
  Search,
  FolderKanban,
  ListTodo,
  Bug as BugIcon,
  FileText,
  ClipboardList,
  CornerDownLeft,
  Loader2,
} from 'lucide-react';

// ============================================
// 全局搜索弹窗（Cmd/Ctrl + K）
// 结果点击后跳转到所属项目并定位到对应标签页
// ============================================

type GroupKey = keyof Pick<GlobalSearchResult, 'projects' | 'tasks' | 'bugs' | 'requirements' | 'documents'>;

const GROUPS: { key: GroupKey; label: string; icon: React.ElementType; path: (item: SearchResultItem) => string }[] = [
  { key: 'projects', label: '项目', icon: FolderKanban, path: (it) => `/projects/${it.projectId}` },
  { key: 'tasks', label: '任务', icon: ListTodo, path: (it) => `/projects/${it.projectId}?tab=tasks` },
  { key: 'bugs', label: 'Bug', icon: BugIcon, path: (it) => `/projects/${it.projectId}?tab=bugs` },
  { key: 'requirements', label: '需求', icon: ClipboardList, path: (it) => `/projects/${it.projectId}?tab=requirements` },
  { key: 'documents', label: '文档', icon: FileText, path: (it) => `/docs` },
];

export function GlobalSearch() {
  const router = useRouter();
  const { searchOpen, setSearchOpen } = useAppStore();
  const [keyword, setKeyword] = useState('');
  const [debounced, setDebounced] = useState('');

  // 输入防抖 300ms
  useEffect(() => {
    const t = setTimeout(() => setDebounced(keyword.trim()), 300);
    return () => clearTimeout(t);
  }, [keyword]);

  // 全局快捷键 Cmd/Ctrl + K 打开，Esc 关闭（Dialog 自带 Esc）
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        useAppStore.getState().setSearchOpen(true);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // 关闭时清空关键词
  useEffect(() => {
    if (!searchOpen) {
      setKeyword('');
      setDebounced('');
    }
  }, [searchOpen]);

  const { data, isFetching } = useQuery({
    queryKey: ['global-search', debounced],
    queryFn: () => searchApi.search(debounced),
    enabled: searchOpen && debounced.length > 0,
    staleTime: 10 * 1000,
  });

  const result = data?.data;
  const total =
    result ? GROUPS.reduce((n, g) => n + (result[g.key]?.length || 0), 0) : 0;

  const go = (path: string) => {
    setSearchOpen(false);
    router.push(path);
  };

  return (
    <Dialog open={searchOpen} onOpenChange={setSearchOpen}>
      <DialogContent className="sm:max-w-xl p-0 gap-0 overflow-hidden top-[15%] translate-y-0">
        <DialogHeader className="sr-only">
          <DialogTitle>全局搜索</DialogTitle>
          <DialogDescription>搜索你的项目、任务、Bug、需求与文档</DialogDescription>
        </DialogHeader>
        <div className="flex items-center gap-2 border-b px-4">
          <Search className="h-4 w-4 shrink-0 text-muted-foreground" />
          <Input
            autoFocus
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="搜索项目 / 任务 / Bug / 需求 / 文档…"
            className="h-12 border-0 bg-transparent px-0 text-sm shadow-none focus-visible:ring-0"
          />
          {isFetching && <Loader2 className="h-4 w-4 shrink-0 animate-spin text-muted-foreground" />}
          <kbd className="shrink-0 rounded border bg-muted px-1.5 py-0.5 text-[10px] text-muted-foreground">
            Esc
          </kbd>
        </div>

        <div className="max-h-[50vh] overflow-y-auto p-2">
          {!debounced ? (
            <p className="py-8 text-center text-xs text-muted-foreground">
              输入关键词开始搜索，范围是你参与的项目及其内容
            </p>
          ) : total === 0 && !isFetching ? (
            <p className="py-8 text-center text-xs text-muted-foreground">没有匹配的结果</p>
          ) : (
            GROUPS.map(({ key, label, icon: Icon, path }) => {
              const items = result?.[key] || [];
              if (items.length === 0) return null;
              return (
                <div key={key} className="mb-1">
                  <p className="px-2 py-1 text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                    {label}（{items.length}）
                  </p>
                  {items.map((it) => (
                    <button
                      key={`${key}-${it.id}`}
                      onClick={() => go(path(it))}
                      className="flex w-full items-center gap-2.5 rounded-md px-2 py-2 text-left hover:bg-muted"
                    >
                      <Icon className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
                      <span className="min-w-0 flex-1 truncate text-sm">{it.title}</span>
                      {it.subtitle && (
                        <span className="shrink-0 max-w-[30%] truncate text-[10px] text-muted-foreground">
                          {it.subtitle}
                        </span>
                      )}
                      <CornerDownLeft className="h-3 w-3 shrink-0 text-muted-foreground/50" />
                    </button>
                  ))}
                </div>
              );
            })
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
