import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { automationRuleApi, type AutomationRuleInfo } from '@/lib/api/automation-rule';

export function useAutomationRules(projectId: string | null) {
  return useQuery({
    queryKey: ['automation-rules', projectId],
    queryFn: () => automationRuleApi.list(projectId!),
    enabled: !!projectId,
  });
}

export function useCreateAutomationRule(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<AutomationRuleInfo>) => automationRuleApi.create(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['automation-rules', projectId] });
    },
  });
}

export function useUpdateAutomationRule(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ ruleId, data }: { ruleId: string; data: Partial<AutomationRuleInfo> }) =>
      automationRuleApi.update(projectId, ruleId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['automation-rules', projectId] });
    },
  });
}

export function useDeleteAutomationRule(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (ruleId: string) => automationRuleApi.remove(projectId, ruleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['automation-rules', projectId] });
    },
  });
}
