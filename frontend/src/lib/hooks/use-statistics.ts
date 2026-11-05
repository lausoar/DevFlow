import { useQuery } from '@tanstack/react-query';
import { statisticsApi } from '@/lib/api/statistics';

// ============================================
// 统计报表相关 Hooks
// ============================================

/** 获取企业级统计报表 */
export function useEnterpriseStats() {
  return useQuery({
    queryKey: ['enterprise-stats'],
    queryFn: () => statisticsApi.getEnterpriseStats(),
    staleTime: 5 * 60 * 1000, // 5 分钟内不重复请求
  });
}

/** 获取项目健康度评估 */
export function useProjectHealth(projectId: string | null) {
  return useQuery({
    queryKey: ['project-health', projectId],
    queryFn: () => statisticsApi.getProjectHealth(projectId!),
    enabled: !!projectId,
    staleTime: 5 * 60 * 1000,
  });
}

/** 获取成员工作量统计 */
export function useMemberWorkload(projectId: string | null) {
  return useQuery({
    queryKey: ['member-workload', projectId],
    queryFn: () => statisticsApi.getMemberWorkload(projectId!),
    enabled: !!projectId,
    staleTime: 5 * 60 * 1000,
  });
}

/** 获取 Sprint 燃尽图数据 */
export function useSprintBurndown(projectId: string | null, sprintId?: string) {
  return useQuery({
    queryKey: ['sprint-burndown', projectId, sprintId],
    queryFn: () => statisticsApi.getSprintBurndown(projectId!, sprintId),
    enabled: !!projectId,
    staleTime: 2 * 60 * 1000, // 2 分钟缓存
  });
}

/** 获取项目甘特图数据 */
export function useProjectGantt(projectId: string | null) {
  return useQuery({
    queryKey: ['project-gantt', projectId],
    queryFn: () => statisticsApi.getProjectGantt(projectId!),
    enabled: !!projectId,
    staleTime: 5 * 60 * 1000,
  });
}
