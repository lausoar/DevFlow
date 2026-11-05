import type { Metadata } from 'next';
import { MainLayout } from '@/components/layout';
import ReportsContent from './reports-content';

// ============================================
// 统计报表页面
// ============================================
export const metadata: Metadata = {
  title: '统计报表 - DevFlow',
  description: '企业级统计报表、项目健康度评估、成员工作量分析',
};

export default function ReportsPage() {
  return (
    <MainLayout>
      <ReportsContent />
    </MainLayout>
  );
}
