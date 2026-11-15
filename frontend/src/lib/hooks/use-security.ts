import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { securityApi } from '@/lib/api/security';

// ============================================
// 账户安全 Hooks
// ============================================

export function useMfaStatus() {
  return useQuery({
    queryKey: ['mfa-status'],
    queryFn: () => securityApi.mfaStatus(),
  });
}

export function useBeginMfaBind() {
  return useMutation({ mutationFn: () => securityApi.beginBind() });
}

export function useConfirmMfaBind() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ secret, code }: { secret: string; code: string }) =>
      securityApi.confirmBind(secret, code),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mfa-status'] }),
  });
}

export function useDisableMfa() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) => securityApi.disableMfa(code),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['mfa-status'] }),
  });
}
