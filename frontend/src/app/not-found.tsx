import Link from 'next/link';
import { MainLayout } from '@/components/layout';
import { FileQuestion } from 'lucide-react';

/** 全站 404：之前落到 Next 默认页，与产品视觉完全脱节 */
export default function NotFound() {
  return (
    <MainLayout>
      <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 text-center">
        <FileQuestion className="h-10 w-10 text-muted-foreground opacity-40" />
        <h1 className="text-lg font-semibold">页面不存在</h1>
        <p className="text-sm text-muted-foreground">链接可能已失效，或你没有访问权限。</p>
        <Link href="/workspace" className="text-sm text-brand hover:underline">
          返回工作台
        </Link>
      </div>
    </MainLayout>
  );
}
