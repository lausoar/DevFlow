import { request } from './client';

// ============================================
// 通知偏好 API
// ============================================

export interface NotificationPreference {
  types: Record<string, boolean>;
  dndEnabled: boolean;
  dndStart?: string | null;
  dndEnd?: string | null;
}

export const notificationPreferenceApi = {
  get: () => request<NotificationPreference>('GET', '/notification-preferences'),

  update: (data: Partial<NotificationPreference>) =>
    request<void>('PUT', '/notification-preferences', data),

  bulk: (enabled: boolean) =>
    request<void>('POST', '/notification-preferences/bulk', { enabled }),
};
