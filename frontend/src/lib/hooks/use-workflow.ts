import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { workflowApi, type WorkflowCategory } from '@/lib/api/workflow';

// ============================================
// 工作流 Hooks
// ============================================

/** 项目某问题类型的生效工作流（看板列 / 状态标签 / 可流转目标） */
export function useWorkflow(
  projectId: string | null,
  issueType: 'TASK' | 'BUG' | 'REQUIREMENT' = 'TASK',
) {
  return useQuery({
    queryKey: ['workflow', projectId, issueType],
    queryFn: () => workflowApi.get(projectId!, issueType),
    enabled: !!projectId,
    staleTime: 60 * 1000,
  });
}

/** 保存工作流（覆盖式） */
export function useUpdateWorkflow(
  projectId: string,
  issueType: 'TASK' | 'BUG' | 'REQUIREMENT' = 'TASK',
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (statuses: Array<{
      code: string;
      name: string;
      category: WorkflowCategory;
      color?: string;
      sort?: number;
      isInitial?: boolean;
      isTerminal?: boolean;
      next?: string[];
    }>) => workflowApi.update(projectId, issueType, statuses),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow', projectId, issueType] });
      // 状态集合变了，任务列表与统计口径都要刷新
      queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
      queryClient.invalidateQueries({ queryKey: ['dashboard', projectId] });
    },
  });
}

/** 恢复默认工作流 */
export function useResetWorkflow(
  projectId: string,
  issueType: 'TASK' | 'BUG' | 'REQUIREMENT' = 'TASK',
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => workflowApi.reset(projectId, issueType),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['workflow', projectId, issueType] });
      queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
    },
  });
}
