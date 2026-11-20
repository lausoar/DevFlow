import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '@/lib/api/auth';
import { useAuthStore } from '@/stores/auth-store';
import type { ApiResponse, AuthResponse, User } from '@/types';
import LoginPage from './page';

const routerPush = vi.hoisted(() => vi.fn());

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: routerPush }),
}));

vi.mock('@/lib/api/auth', () => ({
  authApi: { login: vi.fn() },
}));

const testUser: User = {
  id: 'user-1',
  username: 'developer',
  email: 'developer@example.com',
  role: 'MEMBER',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

function loginResponse(data: Partial<AuthResponse> = {}): ApiResponse<AuthResponse> {
  return {
    code: 200,
    message: 'ok',
    data: {
      user: testUser,
      accessToken: 'access-token',
      refreshToken: 'refresh-token',
      ...data,
    },
  };
}

const mfaChallenge = loginResponse({
  accessToken: '',
  refreshToken: '',
  mfaRequired: true,
  mfaToken: 'mfa-challenge',
});

async function enterPasswordCredentials(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('用户名或邮箱'), 'developer');
  await user.type(screen.getByLabelText('密码'), 'password');
}

describe('LoginPage MFA flow', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.history.replaceState({}, '', '/login');
    useAuthStore.setState({
      user: null,
      accessToken: null,
      isAuthenticated: false,
      isLoading: true,
    });
    routerPush.mockReset();
    vi.mocked(authApi.login).mockReset();
  });

  it('requires six digits, submits the challenge, and completes login', async () => {
    const login = vi.mocked(authApi.login);
    login
      .mockResolvedValueOnce(mfaChallenge)
      .mockResolvedValueOnce(loginResponse());
    const user = userEvent.setup();
    render(<LoginPage />);

    await enterPasswordCredentials(user);
    await user.click(screen.getByRole('button', { name: '登录' }));

    const mfaInput = await screen.findByLabelText('验证器动态码');
    const verifyButton = screen.getByRole('button', { name: '验证并登录' });
    expect(login).toHaveBeenNthCalledWith(1, {
      username: 'developer',
      password: 'password',
    });
    expect(verifyButton).toBeDisabled();

    await user.type(mfaInput, '12ab');
    expect(mfaInput).toHaveValue('12');
    expect(verifyButton).toBeDisabled();

    await user.type(mfaInput, '3456');
    expect(mfaInput).toHaveValue('123456');
    expect(verifyButton).toBeEnabled();
    await user.click(verifyButton);

    await waitFor(() => expect(routerPush).toHaveBeenCalledWith('/workspace'));
    expect(login).toHaveBeenNthCalledWith(2, {
      username: 'developer',
      password: 'password',
      mfaToken: 'mfa-challenge',
      mfaCode: '123456',
    });
    expect(window.localStorage.getItem('access_token')).toBe('access-token');
    expect(window.localStorage.getItem('refresh_token')).toBe('refresh-token');
  });

  it('clears a failed MFA challenge and retries with password credentials only', async () => {
    const login = vi.mocked(authApi.login);
    login
      .mockResolvedValueOnce(mfaChallenge)
      .mockRejectedValueOnce({ response: { data: { message: '动态码已失效，请重新登录' } } })
      .mockResolvedValueOnce(loginResponse());
    const user = userEvent.setup();
    render(<LoginPage />);

    await enterPasswordCredentials(user);
    await user.click(screen.getByRole('button', { name: '登录' }));
    const mfaInput = await screen.findByLabelText('验证器动态码');
    await user.type(mfaInput, '123456');
    await user.click(screen.getByRole('button', { name: '验证并登录' }));

    expect(await screen.findByText('动态码已失效，请重新登录')).toBeInTheDocument();
    expect(screen.queryByLabelText('验证器动态码')).not.toBeInTheDocument();
    const loginButton = screen.getByRole('button', { name: '登录' });
    expect(loginButton).toBeEnabled();

    await user.click(loginButton);
    await waitFor(() => expect(routerPush).toHaveBeenCalledWith('/workspace'));
    expect(login).toHaveBeenNthCalledWith(3, {
      username: 'developer',
      password: 'password',
    });
  });
});
