import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { testCaseApi } from '@/lib/api/test-case';
import type { TestCase } from '@/types';

// ============================================
// 测试用例 Hooks
// ============================================

export function useTestCases(projectId: string | null, keyword?: string) {
  return useQuery({
    queryKey: ['test-cases', projectId, keyword || ''],
    queryFn: () => testCaseApi.list(projectId!, keyword),
    enabled: !!projectId,
  });
}

export function useCreateTestCase(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: Partial<TestCase>) => testCaseApi.create(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', projectId] });
    },
  });
}

export function useUpdateTestCase(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: Partial<TestCase> }) =>
      testCaseApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', projectId] });
    },
  });
}

export function useDeleteTestCase(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => testCaseApi.remove(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', projectId] });
    },
  });
}

export function useExecuteTestCase(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, result, note }: { id: string; result: string; note?: string }) =>
      testCaseApi.execute(id, { result, note }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', projectId] });
    },
  });
}

export function useToBug(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, note }: { id: string; note?: string }) =>
      testCaseApi.toBug(id, note),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['test-cases', projectId] });
      queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
    },
  });
}
