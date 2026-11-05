import { MainLayout } from '@/components/layout';
import FilesContent from './files-content';

export const metadata = {
  title: '文件中心',
  description: 'DevFlow 文件管理',
};

// ============================================
// 文件中心页面
// ============================================
export default function FilesPage() {
  return (
    <MainLayout>
      <FilesContent />
    </MainLayout>
  );
}
