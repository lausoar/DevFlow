import { MainLayout } from '@/components/layout';
import DocsContent from './docs-content';

export const metadata = {
  title: '文档中心',
  description: 'DevFlow 文档管理',
};

// ============================================
// 文档中心页面（支持 ?docId=&start=&end= 从 AI 引用直达原文）
// ============================================
export default async function DocsPage({
  searchParams,
}: {
  searchParams: Promise<{
    docId?: string;
    start?: string;
    end?: string;
    chunk?: string;
    v?: string;
  }>;
}) {
  const params = await searchParams;
  const toInt = (value?: string) => {
    const parsed = Number(value);
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
  };
  return (
    <MainLayout>
      <DocsContent
        initialDocId={params.docId}
        initialHighlight={{
          start: toInt(params.start),
          end: toInt(params.end),
          version: toInt(params.v),
        }}
      />
    </MainLayout>
  );
}
