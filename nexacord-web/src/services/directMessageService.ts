import api from './api';
import type { DirectConversation, DirectMessage } from '../types';

class DirectMessageService {
  async getConversations(): Promise<DirectConversation[]> {
    return api.get<DirectConversation[]>('/direct-conversations');
  }

  async getConversation(conversationId: number): Promise<DirectConversation> {
    return api.get<DirectConversation>(`/direct-conversations/${conversationId}`);
  }

  async createConversation(userId: number): Promise<DirectConversation> {
    return api.post<DirectConversation>(`/direct-conversations/users/${userId}`);
  }

  async getMessages(conversationId: number, sort: 'asc' | 'desc' = 'asc'): Promise<DirectMessage[]> {
    return api.get<DirectMessage[]>(`/direct-conversations/${conversationId}/messages`, {
      params: { sort },
    });
  }

  async sendMessage(conversationId: number, content: string): Promise<DirectMessage> {
    return api.post<DirectMessage>(`/direct-conversations/${conversationId}/messages`, {
      content,
    });
  }
}

export default new DirectMessageService();
