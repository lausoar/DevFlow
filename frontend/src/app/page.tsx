import { redirect } from 'next/navigation';

// ============================================
// 根页面 - 重定向到工作台
// ============================================
export default function RootPage() {
  redirect('/workspace');
}
