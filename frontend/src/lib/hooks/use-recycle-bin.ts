import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { recycleBinApi } from '@/lib/api/recycle-bin';

// ============================================
// 回收站 Hooks
// ============================================

/** 回收站列表（弹窗打开才请求） */
export function useRecycleBin(projectId: string | null) {
  return useQuery({
    queryKey: ['recycle-bin', projectId],
    queryFn: () => recycleBinApi.list(projectId!),
    enabled: !!projectId,
  });
}

function useRestore(projectId: string, kind: 'task' | 'bug' | 'requirement') {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      kind === 'task'
        ? recycleBinApi.restoreTask(id)
        : kind === 'bug'
          ? recycleBinApi.restoreBug(id)
          : recycleBinApi.restoreRequirement(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recycle-bin', projectId] });
      // 恢复后对应业务列表同步刷新
      queryClient.invalidateQueries({ queryKey: ['tasks', projectId] });
      queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
      queryClient.invalidateQueries({ queryKey: ['requirements', projectId] });
    },
  });
}

export const useRestoreTask = (projectId: string) => useRestore(projectId, 'task');
export const useRestoreBug = (projectId: string) => useRestore(projectId, 'bug');
export const useRestoreRequirement = (projectId: string) => useRestore(projectId, 'requirement');
