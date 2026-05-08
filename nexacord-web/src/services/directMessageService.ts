import api from './api';
import type { Attachment, DirectConversation, DirectMessage } from '../types';

export type DirectMessageCreateAttachment = Pick<
  Attachment,
  'fileName' | 'fileType' | 'fileSize' | 'url'
>;

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

  async sendMessage(
    conversationId: number,
    content: string,
    attachments: DirectMessageCreateAttachment[] = []
  ): Promise<DirectMessage> {
    return api.post<DirectMessage>(`/direct-conversations/${conversationId}/messages`, {
      content,
      attachments,
    });
  }

  async updateMessage(
    conversationId: number,
    messageId: number,
    content: string
  ): Promise<DirectMessage> {
    return api.put<DirectMessage>(`/direct-conversations/${conversationId}/messages/${messageId}`, {
      content,
    });
  }

  async deleteMessage(conversationId: number, messageId: number): Promise<void> {
    return api.delete(`/direct-conversations/${conversationId}/messages/${messageId}`);
  }
}

export default new DirectMessageService();
