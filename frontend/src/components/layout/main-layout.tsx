'use client';

import { Sidebar } from './sidebar';
import { TopBar } from './topbar';
import { GlobalSearch } from './global-search';
import { useAppStore } from '@/stores';
import { cn } from '@/lib/utils';
import { AiAssistant } from '@/components/ai-assistant';
import { Button } from '@/components/ui/button';
import { Sheet, SheetContent, SheetTitle } from '@/components/ui/sheet';
import { X, Zap } from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import type { ReactNode } from 'react';

// ============================================
// 主布局组件 - 带侧边栏和顶部工具栏
// ============================================
export function MainLayout({ children }: { children: ReactNode }) {
  const { sidebarCollapsed, mobileMenuOpen, setMobileMenuOpen } = useAppStore();
  const router = useRouter();

  const mobileNavItems = [
    { title: '工作台', href: '/workspace', icon: '🏠' },
    { title: '项目', href: '/projects', icon: '📁' },
    { title: '报表', href: '/reports', icon: '📊' },
    { title: '企业', href: '/enterprise', icon: '🏢' },
    { title: 'AI', href: '/ai', icon: '🤖' },
    { title: '我的', href: '/settings', icon: '⚙️' },
  ];

  return (
    <div className="min-h-screen bg-background">
      {/* 桌面端侧边栏 */}
      <div className="hidden md:block">
        <Sidebar />
      </div>

      {/* 移动端侧边栏 */}
      <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
        <SheetContent side="left" className="w-64 p-0">
          <SheetTitle className="sr-only">导航菜单</SheetTitle>
          <div className="flex h-full flex-col">
            <div className="flex h-14 items-center justify-between border-b px-4">
              <Link href="/workspace" className="flex items-center gap-2">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand text-white">
                  <Zap className="h-4 w-4" />
                </div>
                <span className="text-lg font-bold">DevFlow</span>
              </Link>
              <Button variant="ghost" size="icon" onClick={() => setMobileMenuOpen(false)}>
                <X className="h-5 w-5" />
              </Button>
            </div>
            <nav className="flex-1 space-y-1 p-2">
              {mobileNavItems.map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setMobileMenuOpen(false)}
                  className="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium text-muted-foreground transition-devflow hover:bg-accent hover:text-foreground"
                >
                  <span className="text-lg">{item.icon}</span>
                  {item.title}
                </Link>
              ))}
            </nav>
          </div>
        </SheetContent>
      </Sheet>

      {/* 主内容区 */}
      <div
        className={cn(
          'flex h-dvh flex-col transition-all duration-200',
          'md:ml-60',
          sidebarCollapsed && 'md:ml-16'
        )}
      >
        <TopBar />
        <main className="flex-1 min-h-0 overflow-auto p-4 pb-20 md:p-6 md:pb-6">
          {children}
        </main>
      </div>

      {/* 移动端底部导航 */}
      <nav className="fixed bottom-0 left-0 right-0 z-40 flex h-16 items-center justify-around border-t border-border bg-background/95 backdrop-blur md:hidden">
        {mobileNavItems.map((item) => (
          <button
            key={item.href}
            onClick={() => router.push(item.href)}
            className="flex flex-col items-center gap-1 px-3 py-1 text-xs text-muted-foreground transition-devflow hover:text-foreground"
          >
            <span className="text-xl">{item.icon}</span>
            <span>{item.title}</span>
          </button>
        ))}
      </nav>
      <AiAssistant />
      <GlobalSearch />
    </div>
  );
}
