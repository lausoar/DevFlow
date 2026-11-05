import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { guestApi } from '@/lib/api/guest';

// ============================================
// 游客分享 Hooks
// ============================================

/** 游客链接列表（成员弹窗打开时才请求） */
export function useGuestLinks(projectId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: ['guest-links', projectId],
    queryFn: () => guestApi.listLinks(projectId!),
    enabled: enabled && !!projectId,
  });
}

/** 生成游客链接（成功后失效列表；token 明文只在本次响应里） */
export function useGenerateGuestLink(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (expiresInDays?: number) => guestApi.generateLink(projectId, expiresInDays),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['guest-links', projectId] });
    },
  });
}

/** 撤销游客链接 */
export function useRevokeGuestLink(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (guestId: string) => guestApi.revokeLink(projectId, guestId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['guest-links', projectId] });
    },
  });
}

/** 项目反馈列表（任意项目成员） */
export function useProjectFeedback(projectId: string | null) {
  return useQuery({
    queryKey: ['project-feedback', projectId],
    queryFn: () => guestApi.listFeedback(projectId!),
    enabled: !!projectId,
  });
}

/** 处理反馈（接受/拒绝，bug:edit） */
export function useUpdateFeedbackStatus(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ feedbackId, status }: { feedbackId: string; status: 'ACCEPTED' | 'REJECTED' }) =>
      guestApi.updateFeedbackStatus(feedbackId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-feedback', projectId] });
    },
  });
}

/** 建议转化为 Bug（bug:create；同时刷新 Bug 列表） */
export function useConvertFeedback(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (feedbackId: string) => guestApi.convertFeedback(feedbackId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-feedback', projectId] });
      queryClient.invalidateQueries({ queryKey: ['bugs', projectId] });
    },
  });
}
