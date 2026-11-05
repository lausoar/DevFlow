import React from 'react';
import { MainLayout } from '@/components/layout';
import ProjectDetailContent from './project-detail-content';

export const metadata = {
  title: '项目详情',
  description: 'DevFlow 项目管理',
};

// 允许的标签页，防止任意值注入
const TABS = new Set(['dashboard', 'requirements', 'tasks', 'bugs', 'feedback', 'testing']);

// ============================================
// 项目详情页面（支持 ?tab= 直达指定标签页，供全局搜索跳转）
// ============================================
export default async function ProjectDetailPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ tab?: string }>;
}) {
  const { id } = await params;
  const { tab } = await searchParams;

  return (
    <MainLayout>
      <ProjectDetailContent projectId={id} initialTab={TABS.has(tab || '') ? tab : undefined} />
    </MainLayout>
  );
}
