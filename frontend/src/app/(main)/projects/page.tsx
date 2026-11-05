import { MainLayout } from '@/components/layout';
import ProjectsContent from './projects-content';

export const metadata = {
  title: '项目中心',
  description: 'DevFlow 项目管理',
};

// ============================================
// 项目中心页面
// ============================================
export default function ProjectsPage() {
  return (
    <MainLayout>
      <ProjectsContent />
    </MainLayout>
  );
}
