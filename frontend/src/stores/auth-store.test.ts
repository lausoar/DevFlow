import { beforeEach, describe, expect, it } from 'vitest';
import type { User } from '@/types';
import { useAuthStore } from './auth-store';

const testUser: User = {
  id: 'user-1',
  username: 'developer',
  email: 'developer@example.com',
  role: 'MEMBER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('auth-store token persistence', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.cookie = 'df_token=; path=/; Max-Age=0';
    useAuthStore.setState({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      isLoading: true,
    });
  });

  it('stores both tokens and clears the logged-out marker on authentication', () => {
    window.localStorage.setItem('devflow-logged-out', '1');

    useAuthStore.getState().setAuth(testUser, 'access-token', 'refresh-token');

    expect(useAuthStore.getState()).toMatchObject({
      user: testUser,
      accessToken: 'access-token',
      isAuthenticated: true,
      isLoading: false,
    });
    expect(window.localStorage.getItem('access_token')).toBe('access-token');
    expect(window.localStorage.getItem('refresh_token')).toBe('refresh-token');
    expect(window.localStorage.getItem('devflow-logged-out')).toBeNull();
    expect(JSON.parse(window.localStorage.getItem('devflow-auth') || '{}')).toMatchObject({
      state: {
        user: testUser,
        accessToken: 'access-token',
        isAuthenticated: true,
      },
    });
  });

  it('removes tokens, persisted identity, and the auth cookie on logout', () => {
    useAuthStore.getState().setAuth(testUser, 'access-token', 'refresh-token');
    document.cookie = 'df_token=access-token; path=/';

    useAuthStore.getState().logout();

    expect(useAuthStore.getState()).toMatchObject({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      isLoading: false,
    });
    expect(window.localStorage.getItem('access_token')).toBeNull();
    expect(window.localStorage.getItem('refresh_token')).toBeNull();
    expect(JSON.parse(window.localStorage.getItem('devflow-auth') || '{}')).toMatchObject({
      state: {
        user: null,
        accessToken: null,
        isAuthenticated: false,
      },
    });
    expect(document.cookie).not.toContain('df_token=');
  });
});
