import { request } from './client';
import type { TestCase, TestCaseView, TestCaseStats, TestExecution } from '@/types';

// ============================================
// 测试用例 API
// ============================================
export const testCaseApi = {
  /** 用例列表（含统计） */
  list: (projectId: string, keyword?: string) =>
    request<{ cases: TestCaseView[]; stats: TestCaseStats }>(
      'GET', `/projects/${projectId}/test-cases`, undefined,
      keyword ? { keyword } : undefined),

  /** 用例详情（含执行历史） */
  detail: (id: string) =>
    request<{ case: TestCase; executions: TestExecution[] }>('GET', `/test-cases/${id}`),

  create: (projectId: string, data: Partial<TestCase>) =>
    request<TestCase>('POST', `/projects/${projectId}/test-cases`, data),

  update: (id: string, data: Partial<TestCase>) =>
    request<TestCase>('PUT', `/test-cases/${id}`, data),

  remove: (id: string) =>
    request<void>('DELETE', `/test-cases/${id}`),

  /** 记录执行结果 */
  execute: (id: string, data: { result: string; note?: string }) =>
    request<TestExecution>('POST', `/test-cases/${id}/executions`, data),

  /** 执行历史 */
  executions: (id: string) =>
    request<TestExecution[]>('GET', `/test-cases/${id}/executions`),

  /** 失败转缺陷 */
  toBug: (id: string, note?: string) =>
    request<import('@/types').Bug>('POST', `/test-cases/${id}/to-bug`, { note }),
};
