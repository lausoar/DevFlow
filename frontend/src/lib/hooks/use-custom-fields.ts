import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { customFieldApi, type CustomFieldDef } from '@/lib/api/custom-field';

// ============================================
// 自定义字段 Hooks
// ============================================

export function useCustomFields(projectId: string | null, issueType: 'TASK' | 'BUG' | 'REQUIREMENT') {
  return useQuery({
    queryKey: ['custom-fields', projectId, issueType],
    queryFn: () => customFieldApi.list(projectId!, issueType),
    enabled: !!projectId,
    staleTime: 60 * 1000,
  });
}

export function useCustomFieldValues(issueType: string | null, issueId: string | null) {
  return useQuery({
    queryKey: ['custom-field-values', issueType, issueId],
    queryFn: () => customFieldApi.values(issueType!, issueId!),
    enabled: !!issueType && !!issueId,
  });
}

export function useSaveCustomFieldValues(issueType: string, issueId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (values: Record<string, unknown>) =>
      customFieldApi.saveValues(issueType, issueId, values),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['custom-field-values', issueType, issueId] });
    },
  });
}

export function useCreateCustomField(projectId: string, issueType: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<CustomFieldDef>) => customFieldApi.create(projectId, issueType, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['custom-fields', projectId, issueType] }),
  });
}

export function useDeleteCustomField(projectId: string, issueType: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => customFieldApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['custom-fields', projectId, issueType] }),
  });
}
