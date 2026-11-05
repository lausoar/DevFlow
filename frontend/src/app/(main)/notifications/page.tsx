import { MainLayout } from '@/components/layout';
import NotificationsContent from './notifications-content';

export const metadata = {
  title: '通知中心',
  description: 'DevFlow 通知管理',
};

// ============================================
// 通知中心页面
// ============================================
export default function NotificationsPage() {
  return (
    <MainLayout>
      <NotificationsContent />
    </MainLayout>
  );
}
