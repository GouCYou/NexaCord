import api from './api';
import type { Server } from '../types';

class ServerService {
  async getUserServers(): Promise<Server[]> {
    return api.get<Server[]>('/servers/user');
  }

  async getServerById(serverId: number): Promise<Server> {
    return api.get<Server>(`/servers/${serverId}`);
  }

  async searchServers(name: string): Promise<Server[]> {
    return api.get<Server[]>('/servers/search', { params: { name } });
  }

  async createServer(
    server: Omit<Server, 'id' | 'createdAt' | 'updatedAt' | 'ownerId'>
  ): Promise<Server> {
    return api.post<Server>('/servers', server);
  }

  async updateServer(serverId: number, server: Partial<Server>): Promise<Server> {
    return api.put<Server>(`/servers/${serverId}`, server);
  }

  async deleteServer(serverId: number): Promise<void> {
    return api.delete(`/servers/${serverId}`);
  }
}

export default new ServerService();
