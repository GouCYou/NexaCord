import api from './api';
import type { Attachment, Message } from '../types';

export type MessageCreateAttachment = Pick<
  Attachment,
  'fileName' | 'fileType' | 'fileSize' | 'url'
>;

class MessageService {
  async getChannelMessages(channelId: number, sort: 'asc' | 'desc' = 'asc'): Promise<Message[]> {
    return api.get<Message[]>(`/channels/${channelId}/messages`, { params: { sort } });
  }

  async getMessageById(messageId: number): Promise<Message> {
    return api.get<Message>(`/messages/${messageId}`);
  }

  async createMessage(
    channelId: number,
    content: string,
    attachments: MessageCreateAttachment[] = []
  ): Promise<Message> {
    return api.post<Message>(`/channels/${channelId}/messages`, {
      content,
      attachments,
    });
  }

  async updateMessage(messageId: number, content: string): Promise<Message> {
    return api.put<Message>(`/messages/${messageId}`, { content });
  }

  async deleteMessage(messageId: number): Promise<void> {
    return api.delete(`/messages/${messageId}`);
  }
}

export default new MessageService();
