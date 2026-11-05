import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { projectApi } from '@/lib/api/project';
import type { Project, ProjectMember, PageQuery } from '@/types';

// ============================================
// 项目相关 Hooks
// ============================================

/** 获取项目列表 */
export function useProjects(params?: PageQuery) {
  return useQuery({
    queryKey: ['projects', params],
    queryFn: () => projectApi.getList(params),
    staleTime: 1000 * 60 * 2,
  });
}

/** 获取项目详情 */
export function useProject(id: string | null) {
  return useQuery({
    queryKey: ['projects', id],
    queryFn: () => projectApi.getById(id!),
    enabled: !!id,
  });
}

/** 创建项目 */
export function useCreateProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: Partial<Project>) => projectApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

/** 更新项目 */
export function useUpdateProject(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: Partial<Project>) => projectApi.update(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

/** 删除项目 */
export function useDeleteProject() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (id: string) => projectApi.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}

// ============================================
// 项目成员 Hooks
// ============================================

/** 获取项目成员列表 */
export function useProjectMembers(projectId: string | null) {
  return useQuery({
    queryKey: ['project-members', projectId],
    queryFn: () => projectApi.getMembers(projectId!),
    enabled: !!projectId,
  });
}

/** 添加项目成员 */
export function useAddProjectMember(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { userId: string; role?: string }) =>
      projectApi.addMember(projectId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-members', projectId] });
    },
  });
}

/** 更新成员角色 */
export function useUpdateMemberRole(projectId: string, memberId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: { role: string }) =>
      projectApi.updateMemberRole(projectId, memberId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-members', projectId] });
    },
  });
}

/** 移除项目成员 */
export function useRemoveProjectMember(projectId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (memberId: string) =>
      projectApi.removeMember(projectId, memberId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['project-members', projectId] });
    },
  });
}

// ============================================
// 项目邀请链接
// ============================================

/** 获取项目邀请码（弹窗打开时才请求） */
export function useProjectInviteCode(projectId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: ['project-invite-code', projectId],
    queryFn: () => projectApi.getInviteCode(projectId!),
    enabled: !!projectId && enabled,
    staleTime: 1000 * 60 * 5,
  });
}

/** 凭邀请码加入项目 */
export function useJoinProjectByCode() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (inviteCode: string) => projectApi.joinByInviteCode(inviteCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['projects'] });
    },
  });
}
