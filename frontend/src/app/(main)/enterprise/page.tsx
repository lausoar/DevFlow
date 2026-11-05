import { MainLayout } from '@/components/layout';
import EnterpriseContent from './enterprise-content';

export const metadata = {
  title: '企业管理',
  description: 'DevFlow 企业管理 - 成员、部门、设置',
};

// ============================================
// 企业管理页面
// ============================================
export default function EnterprisePage() {
  return (
    <MainLayout>
      <EnterpriseContent />
    </MainLayout>
  );
}
