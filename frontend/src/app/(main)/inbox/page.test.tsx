import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import InboxPage from './page';
import MainRouteLayout from '../layout';

vi.mock('@/components/layout', () => ({
  MainLayout: ({ children }: { children: React.ReactNode }) => (
    <div>
      <nav aria-label="应用侧边栏">工作台</nav>
      {children}
    </div>
  ),
}));

vi.mock('@/lib/ws/notification-socket', () => ({
  NotificationSocket: () => null,
}));

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/inbox',
}));

vi.mock('@/stores', () => ({
  useAuthStore: Object.assign(() => ({ accessToken: 'test-token' }), {
    persist: {
      hasHydrated: () => true,
      onFinishHydration: () => () => {},
    },
  }),
}));

vi.mock('@/lib/hooks/use-inbox', () => ({
  useInbox: () => ({
    data: {
      data: {
        total: 2,
        approvals: [{ id: 'd1', title: '部署 v1.2 待审批', createdAt: '2026-11-21' }],
        triage: [],
        assignments: [{ id: 't1', title: '修复登录问题', projectId: 'p1', priority: 'HIGH' }],
      },
    },
    isLoading: false,
  }),
  useApproveDeployment: () => ({ mutate: vi.fn(), isPending: false }),
  useRejectDeployment: () => ({ mutate: vi.fn(), isPending: false }),
}));

describe('InboxPage', () => {
  it('keeps the application layout and prioritizes approval actions', () => {
    render(
      <MainRouteLayout>
        <InboxPage />
      </MainRouteLayout>,
    );

    // 回归：页面曾经独立全屏渲染，导致左侧栏消失
    expect(screen.getByRole('navigation', { name: '应用侧边栏' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '收件箱' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '等我审批' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '通过' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '驳回' })).toBeInTheDocument();
    expect(screen.getByText('2 件待处理')).toBeInTheDocument();
    expect(screen.getByText('修复登录问题')).toBeInTheDocument();
  });
});
