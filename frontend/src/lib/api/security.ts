import { request } from './client';

// ============================================
// 账户安全 API（双因素 / 密码策略）
// ============================================

export interface MfaStatus {
  enabled: boolean;
  boundAt?: string | null;
  mustChangePassword: boolean;
  passwordUpdatedAt?: string | null;
}

export interface MfaBindResult {
  secret: string;
  otpauthUrl: string;
}

export interface PasswordPolicy {
  minLength: number;
  requireComplexity: boolean;
  mustChangePassword: boolean;
  passwordUpdatedAt?: string | null;
  expireDays: number;
}

export const securityApi = {
  mfaStatus: () => request<MfaStatus>('GET', '/security/mfa'),

  beginBind: () => request<MfaBindResult>('POST', '/security/mfa/bind'),

  confirmBind: (secret: string, code: string) =>
    request<void>('POST', '/security/mfa/confirm', { secret, code }),

  disableMfa: (code: string) =>
    request<void>('POST', '/security/mfa/disable', { code }),

  passwordPolicy: () => request<PasswordPolicy>('GET', '/security/password-policy'),

  checkPassword: (code: string) =>
    request<{ ok: boolean; problems: string[] }>('POST', '/security/password/check', { code }),
};
