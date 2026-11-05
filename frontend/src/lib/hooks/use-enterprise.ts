import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { enterpriseApi, type Enterprise, type EnterpriseDetail } from '@/lib/api/enterprise';

// ============================================
// 企业相关 Hooks
// ============================================

/** 获取当前企业 */
export function useCurrentEnterprise() {
  return useQuery({
    queryKey: ['enterprise', 'current'],
    queryFn: () => enterpriseApi.getCurrent(),
    retry: false,
    staleTime: 1000 * 60 * 5,
  });
}

/** 获取企业详情 */
export function useEnterpriseDetail(enterpriseId: string | null) {
  return useQuery({
    queryKey: ['enterprise', enterpriseId, 'detail'],
    queryFn: () => enterpriseApi.getDetail(enterpriseId!),
    enabled: !!enterpriseId,
    staleTime: 1000 * 60 * 2,
  });
}

/** 创建企业 */
export function useCreateEnterprise() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { name: string; logo?: string }) => enterpriseApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise'] });
    },
  });
}

/** 修改成员角色 */
export function useUpdateMemberRole(enterpriseId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { memberId: string; role: string }) =>
      enterpriseApi.updateMemberRole(enterpriseId, data.memberId, data.role),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise'] });
    },
  });
}

/** 调整成员部门 */
export function useUpdateMemberDepartment(enterpriseId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { memberId: string; departmentId?: string }) =>
      enterpriseApi.updateMemberDepartment(enterpriseId, data.memberId, data.departmentId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise'] });
    },
  });
}

/** 解散企业 */
export function useDissolveEnterprise() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (enterpriseId: string) => enterpriseApi.dissolve(enterpriseId),
    onSuccess: () => {
      queryClient.clear();
    },
  });
}

/** 自助退出企业（成功后由调用方刷新用户态，见 enterprise-content） */
export function useLeaveEnterprise() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (enterpriseId: string) => enterpriseApi.leave(enterpriseId),
    onSuccess: () => {
      // 退出后所有企业/项目/权限缓存全部失效
      queryClient.clear();
    },
  });
}

/** 邀请成员（返回含一次性初始密码） */
export function useInviteMember(enterpriseId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { email: string; realName?: string; role?: string; departmentId?: string }) =>
      enterpriseApi.inviteMember(enterpriseId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise', enterpriseId] });
    },
  });
}

/** 删除部门（有子部门会被后端拒绝） */
export function useDeleteDepartment() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => enterpriseApi.deleteDepartment(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise'] });
    },
  });
}

/** 更新部门（重命名/调整上级） */
export function useUpdateDepartment() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { id: string; name?: string; parentId?: string }) =>
      enterpriseApi.updateDepartment(data.id, { name: data.name, parentId: data.parentId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise'] });
    },
  });
}

/** 超管重置成员密码（返回一次性临时密码） */
export function useResetMemberPassword(enterpriseId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (memberId: string) => enterpriseApi.resetMemberPassword(enterpriseId, memberId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise', enterpriseId] });
    },
  });
}

/** 移除成员 */
export function useRemoveMember(enterpriseId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (memberId: string) => enterpriseApi.removeMember(enterpriseId, memberId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise', enterpriseId] });
    },
  });
}

/** 加入企业 */
export function useJoinEnterprise() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (inviteCode: string) => enterpriseApi.joinByInviteCode(inviteCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise'] });
    },
  });
}

/** 更新企业信息 */
export function useUpdateEnterprise(enterpriseId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { name?: string; logo?: string; regenerateInviteCode?: boolean; wecomWebhook?: string }) =>
      enterpriseApi.update(enterpriseId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise'] });
    },
  });
}

/** 创建部门 */
export function useCreateDepartment(enterpriseId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { name: string; parentId?: string; sort?: number }) =>
      enterpriseApi.createDepartment(enterpriseId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['enterprise', enterpriseId] });
    },
  });
}
