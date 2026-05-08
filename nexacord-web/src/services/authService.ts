import api from './api';
import type { EmailCodeRequest, LoginRequest, PasswordResetRequest, RegisterRequest, User } from '../types';
import {
  clearAuthSession,
  persistAuthSession,
  readAuthValue,
  shouldRememberByDefault,
  updateStoredUser,
} from '../utils/authStorage';

type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: User;
};

class AuthService {
  async login(credentials: LoginRequest, rememberMe = shouldRememberByDefault()): Promise<AuthResponse> {
    const response = await api.post<AuthResponse>('/auth/login', credentials);
    this.persistSession(response, rememberMe);

    return response;
  }

  async register(credentials: RegisterRequest): Promise<AuthResponse> {
    const response = await api.post<AuthResponse>('/auth/register', credentials);
    this.persistSession(response, true);

    return response;
  }

  async sendEmailCode(request: EmailCodeRequest): Promise<void> {
    return api.post<void>('/auth/email-code', request);
  }

  async resetPassword(request: PasswordResetRequest): Promise<void> {
    return api.post<void>('/auth/reset-password', request);
  }

  logout(): void {
    clearAuthSession();
  }

  setCurrentUser(user: User): void {
    updateStoredUser(user);
  }

  getCurrentUser(): User | null {
    const userStr = readAuthValue('user');
    if (!userStr) {
      return null;
    }

    try {
      return JSON.parse(userStr);
    } catch {
      clearAuthSession();
      return null;
    }
  }

  isAuthenticated(): boolean {
    return Boolean(readAuthValue('token') || readAuthValue('refreshToken'));
  }

  getToken(): string | null {
    return readAuthValue('token');
  }

  getRefreshToken(): string | null {
    return readAuthValue('refreshToken');
  }

  shouldRememberByDefault(): boolean {
    return shouldRememberByDefault();
  }

  private persistSession(response: AuthResponse, rememberMe: boolean): void {
    if (!response.accessToken) {
      return;
    }

    persistAuthSession(response, rememberMe);
  }
}

export default new AuthService();
