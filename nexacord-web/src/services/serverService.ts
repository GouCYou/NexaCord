import api from './api';
import type { Member, MemberRole, Server, ServerInvite } from '../types';

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

  async getServerMembers(serverId: number): Promise<Member[]> {
    return api.get<Member[]>(`/servers/${serverId}/members`);
  }

  async addServerMember(serverId: number, userId: number): Promise<Member> {
    return api.post<Member>(`/servers/${serverId}/members/${userId}`);
  }

  async updateServerMemberRole(
    serverId: number,
    memberId: number,
    role: Extract<MemberRole, 'ADMIN' | 'MEMBER'>
  ): Promise<Member> {
    return api.patch<Member>(`/servers/${serverId}/members/${memberId}/role`, { role });
  }

  async removeServerMember(serverId: number, memberId: number): Promise<void> {
    return api.delete(`/servers/${serverId}/members/${memberId}`);
  }

  async createInvite(serverId: number): Promise<ServerInvite> {
    return api.post<ServerInvite>(`/servers/${serverId}/invites`);
  }

  async getInvite(code: string): Promise<ServerInvite> {
    return api.get<ServerInvite>(`/invites/${code}`);
  }

  async joinInvite(code: string): Promise<Server> {
    return api.post<Server>(`/invites/${code}/join`);
  }
}

export default new ServerService();
