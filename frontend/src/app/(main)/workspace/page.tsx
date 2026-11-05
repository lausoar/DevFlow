import { MainLayout } from '@/components/layout';
import WorkspaceContent from './workspace-content';

export const metadata = {
  title: '工作台',
  description: 'DevFlow 工作台 - 您的研发起点',
};

// ============================================
// 工作台页面
// ============================================
export default function WorkspacePage() {
  return (
    <MainLayout>
      <WorkspaceContent />
    </MainLayout>
  );
}
