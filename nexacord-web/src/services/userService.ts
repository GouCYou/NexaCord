import api from './api';
import type { PasswordChangeRequest, User, UserProfileUpdateRequest } from '../types';

class UserService {
  async getCurrentUser(): Promise<User> {
    return api.get<User>('/users/me');
  }

  async updateCurrentUser(profile: UserProfileUpdateRequest): Promise<User> {
    return api.patch<User>('/users/me', profile);
  }

  async sendCurrentEmailCode(): Promise<void> {
    return api.post<void>('/users/me/email-code');
  }

  async verifyCurrentEmailCode(verificationCode: string): Promise<void> {
    return api.post<void>('/users/me/email-code/verify', { verificationCode });
  }

  async changePassword(request: PasswordChangeRequest): Promise<void> {
    return api.post<void>('/users/me/password', request);
  }

  async searchUsers(query: string): Promise<User[]> {
    return api.get<User[]>('/users/search', { params: { query } });
  }
}

export default new UserService();
