import { MainLayout } from '@/components/layout';
import DocsContent from './docs-content';

export const metadata = {
  title: '文档中心',
  description: 'DevFlow 文档管理',
};

// ============================================
// 文档中心页面
// ============================================
export default function DocsPage() {
  return (
    <MainLayout>
      <DocsContent />
    </MainLayout>
  );
}
