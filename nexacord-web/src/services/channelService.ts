import api from './api';
import type { Channel } from '../types';

class ChannelService {
  async getServerChannels(serverId: number): Promise<Channel[]> {
    return api.get<Channel[]>(`/channels/server/${serverId}`);
  }

  async getChannelById(channelId: number): Promise<Channel> {
    return api.get<Channel>(`/channels/${channelId}`);
  }

  async createChannel(
    serverId: number,
    channel: Pick<Channel, 'name' | 'type' | 'topic' | 'nsfw' | 'parentId'>
  ): Promise<Channel> {
    return api.post<Channel>(`/channels/server/${serverId}`, channel);
  }

  async updateChannel(channelId: number, channel: Partial<Channel>): Promise<Channel> {
    return api.put<Channel>(`/channels/${channelId}`, channel);
  }

  async deleteChannel(channelId: number): Promise<void> {
    return api.delete(`/channels/${channelId}`);
  }
}

export default new ChannelService();
