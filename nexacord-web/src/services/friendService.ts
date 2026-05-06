import api from './api';
import type { Friendship } from '../types';

class FriendService {
  async getFriends(): Promise<Friendship[]> {
    return api.get<Friendship[]>('/friends');
  }

  async getIncomingRequests(): Promise<Friendship[]> {
    return api.get<Friendship[]>('/friends/requests/incoming');
  }

  async getOutgoingRequests(): Promise<Friendship[]> {
    return api.get<Friendship[]>('/friends/requests/outgoing');
  }

  async createFriendRequest(usernameOrEmail: string): Promise<Friendship> {
    return api.post<Friendship>('/friends/requests', { usernameOrEmail });
  }

  async acceptFriendRequest(friendshipId: number): Promise<Friendship> {
    return api.post<Friendship>(`/friends/${friendshipId}/accept`);
  }

  async deleteFriendship(friendshipId: number): Promise<void> {
    return api.delete(`/friends/${friendshipId}`);
  }
}

export default new FriendService();
