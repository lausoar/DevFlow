import { MainLayout } from '@/components/layout';
import AiCenterContent from './ai-center-content';

export const metadata = {
  title: 'AI 中心',
  description: 'DevFlow AI 智能平台',
};

// ============================================
// AI 中心页面
// ============================================
export default function AiCenterPage() {
  return (
    <MainLayout>
      <AiCenterContent />
    </MainLayout>
  );
}
