import { MainLayout } from '@/components/layout';
import ChatContent from './chat-content';

export const metadata = {
  title: '消息',
  description: 'DevFlow 实时聊天',
};

// ============================================
// 聊天页面
// ============================================
export default function ChatPage() {
  return (
    <MainLayout>
      <ChatContent />
    </MainLayout>
  );
}
