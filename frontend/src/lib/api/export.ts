import apiClient from './client';

// ============================================
// 导出 API（服务端生成 CSV，带审计与公式注入防护）
// ============================================

export type IssueExportKind = 'tasks' | 'bugs';

export interface IssueExportFilters {
  keyword?: string;
  status?: string;
  severity?: string;
  priority?: string;
  sprintId?: string;
  /** 保存视图的结构化表达式；命中集合必须与当前视图一致 */
  expression?: string;
}

const LABELS: Record<IssueExportKind, string> = {
  tasks: '任务',
  bugs: '缺陷',
};

/** 触发浏览器下载：Blob 已由 axios 携带鉴权头取回，不走 <a href> 直连 */
function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

export const exportApi = {
  /** 导出项目任务/缺陷 CSV；后端按可见性与当前筛选取数并写审计 */
  downloadIssues: async (
    projectId: string,
    kind: IssueExportKind,
    filters: IssueExportFilters = {},
    projectName?: string,
  ): Promise<void> => {
    const blob = await apiClient.get(`/projects/${projectId}/export/${kind}.csv`, {
      params: filters,
      responseType: 'blob',
    }) as unknown as Blob;
    const date = new Date().toISOString().slice(0, 10);
    saveBlob(blob, `${projectName || projectId}-${LABELS[kind]}-${date}.csv`);
  },

  /** 导出测试用例（与列表同一份最新执行结果） */
  downloadTestCases: async (projectId: string, projectName?: string): Promise<void> => {
    const blob = await apiClient.get(`/projects/${projectId}/export/test-cases.csv`, {
      responseType: 'blob',
    }) as unknown as Blob;
    saveBlob(blob, `${projectName || projectId}-测试用例-${new Date().toISOString().slice(0, 10)}.csv`);
  },

  /** 导出企业统计摘要 */
  downloadEnterpriseSummary: async (enterpriseId: string): Promise<void> => {
    const blob = await apiClient.get(`/statistics/enterprises/${enterpriseId}/export/summary.csv`, {
      responseType: 'blob',
    }) as unknown as Blob;
    saveBlob(blob, `企业统计报表-${new Date().toISOString().slice(0, 10)}.csv`);
  },
};
