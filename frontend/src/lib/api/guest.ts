import { request } from './client';
import type {
  Bug,
  GuestLink,
  ProjectFeedback,
  GuestProjectInfo,
  GuestSubmissions,
} from '@/types';

// ============================================
// 游客分享 API（第 3 期）
// 管理端/反馈端需登录；游客端（/guest/**）免登录由后端白名单放行
// ============================================
export const guestApi = {
  // ---------- 管理端（member:manage = OWNER/MANAGER） ----------

  /** 生成游客链接，token 明文仅此一次返回 */
  generateLink: (projectId: string, expiresInDays?: number) =>
    request<{ guestId: string; token: string }>(
      'POST',
      `/projects/${projectId}/guest-links`,
      expiresInDays ? { expiresInDays } : {},
    ),

  /** 游客链接列表（脱敏，无 token） */
  listLinks: (projectId: string) =>
    request<GuestLink[]>('GET', `/projects/${projectId}/guest-links`),

  /** 撤销游客链接 */
  revokeLink: (projectId: string, guestId: string) =>
    request<void>('DELETE', `/projects/${projectId}/guest-links/${guestId}`),

  // ---------- 反馈（站内成员） ----------

  /** 项目反馈列表（任意项目成员可看） */
  listFeedback: (projectId: string) =>
    request<ProjectFeedback[]>('GET', `/projects/${projectId}/feedback`),

  /** 处理反馈：接受/拒绝（bug:edit = 测试人员及以上） */
  updateFeedbackStatus: (feedbackId: string, status: 'ACCEPTED' | 'REJECTED') =>
    request<ProjectFeedback>('PUT', `/feedback/${feedbackId}/status`, { status }),

  /** 建议转化为 Bug（bug:create = 测试人员及以上） */
  convertFeedback: (feedbackId: string) =>
    request<Bug>('POST', `/feedback/${feedbackId}/convert`),

  // ---------- 游客端（免登录） ----------

  /** 游客查看项目脱敏信息 */
  guestProject: (token: string) =>
    request<GuestProjectInfo>('GET', `/guest/${token}/project`),

  /** 游客提交 Bug（进待确认队列） */
  submitGuestBug: (
    token: string,
    data: { title: string; description?: string; environment?: string; nickname: string; email?: string },
  ) => request<Bug>('POST', `/guest/${token}/bugs`, data),

  /** 游客提交建议 */
  submitGuestFeedback: (
    token: string,
    data: { type: string; title: string; content?: string; nickname: string; email?: string },
  ) => request<ProjectFeedback>('POST', `/guest/${token}/feedback`, data),

  /** 游客查看自己（该链接）的提交与状态 */
  guestSubmissions: (token: string) =>
    request<GuestSubmissions>('GET', `/guest/${token}/submissions`),
};
