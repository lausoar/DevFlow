import { request } from './client';
import type {
  EnterpriseStats,
  ProjectHealth,
  MemberWorkloadStats,
  BurndownData,
  GanttData,
} from '@/types';

// ============================================
// 统计报表 API
// ============================================
export const statisticsApi = {
  /** 获取企业级统计报表 */
  getEnterpriseStats: () =>
    request<EnterpriseStats>('GET', '/statistics/enterprise'),

  /** 获取项目健康度评估 */
  getProjectHealth: (projectId: string) =>
    request<ProjectHealth>('GET', `/statistics/projects/${projectId}/health`),

  /** 获取成员工作量统计 */
  getMemberWorkload: (projectId: string) =>
    request<MemberWorkloadStats>('GET', `/statistics/projects/${projectId}/workload`),

  /** 获取 Sprint 燃尽图数据 */
  getSprintBurndown: (projectId: string, sprintId?: string) =>
    request<BurndownData>('GET', `/statistics/projects/${projectId}/burndown`, undefined, {
      sprintId,
    } as Record<string, unknown>),

  /** 获取项目甘特图数据 */
  getProjectGantt: (projectId: string) =>
    request<GanttData>('GET', `/statistics/projects/${projectId}/gantt`),

  /** 导出项目统计 Excel */
  exportProjectExcel: (projectId: string) => {
    // 直接在新窗口打开下载链接
    const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1';
    const token = typeof window !== 'undefined' ? localStorage.getItem('access_token') : '';
    const url = `${baseUrl}/statistics/projects/${projectId}/export/excel`;

    // 使用 fetch 下载文件
    return fetch(url, {
      headers: { Authorization: `Bearer ${token}` },
    }).then(async (res) => {
      if (!res.ok) throw new Error('导出失败');
      const blob = await res.blob();
      const disposition = res.headers.get('Content-Disposition');
      let filename = '统计报表.xlsx';
      if (disposition) {
        const match = disposition.match(/filename=(.+)/);
        if (match) filename = decodeURIComponent(match[1]);
      }
      // 触发下载
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = filename;
      a.click();
      URL.revokeObjectURL(a.href);
    });
  },

  /** 导出成员工作量 Excel */
  exportWorkloadExcel: (projectId: string) => {
    const baseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1';
    const token = typeof window !== 'undefined' ? localStorage.getItem('access_token') : '';
    const url = `${baseUrl}/statistics/projects/${projectId}/export/workload`;

    return fetch(url, {
      headers: { Authorization: `Bearer ${token}` },
    }).then(async (res) => {
      if (!res.ok) throw new Error('导出失败');
      const blob = await res.blob();
      const a = document.createElement('a');
      a.href = URL.createObjectURL(blob);
      a.download = '成员工作量.xlsx';
      a.click();
      URL.revokeObjectURL(a.href);
    });
  },
};
