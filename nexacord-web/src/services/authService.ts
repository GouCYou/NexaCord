import api from './api';
import type { EmailCodeRequest, LoginRequest, PasswordResetRequest, RegisterRequest, User } from '../types';

type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: User;
};

class AuthService {
  async login(credentials: LoginRequest): Promise<AuthResponse> {
    const response = await api.post<AuthResponse>('/auth/login', credentials);
    if (response.accessToken) {
      localStorage.setItem('token', response.accessToken);
      localStorage.setItem('user', JSON.stringify(response.user));
    }

    return response;
  }

  async register(credentials: RegisterRequest): Promise<AuthResponse> {
    const response = await api.post<AuthResponse>('/auth/register', credentials);
    if (response.accessToken) {
      localStorage.setItem('token', response.accessToken);
      localStorage.setItem('user', JSON.stringify(response.user));
    }

    return response;
  }

  async sendEmailCode(request: EmailCodeRequest): Promise<void> {
    return api.post<void>('/auth/email-code', request);
  }

  async resetPassword(request: PasswordResetRequest): Promise<void> {
    return api.post<void>('/auth/reset-password', request);
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }

  setCurrentUser(user: User): void {
    localStorage.setItem('user', JSON.stringify(user));
  }

  getCurrentUser(): User | null {
    const userStr = localStorage.getItem('user');
    if (!userStr) {
      return null;
    }

    try {
      return JSON.parse(userStr);
    } catch {
      localStorage.removeItem('user');
      localStorage.removeItem('token');
      return null;
    }
  }

  isAuthenticated(): boolean {
    return Boolean(localStorage.getItem('token'));
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }
}

export default new AuthService();
