export interface User {
  id: number;
  username: string;
  email: string;
  avatarUrl: string | null;
  status: 'online' | 'offline' | 'away' | 'dnd';
}

export interface Server {
  id: number;
  name: string;
  description: string;
  iconUrl: string | null;
  bannerUrl: string | null;
  createdAt: string;
  updatedAt: string;
  ownerId?: number;
}

export interface Channel {
  id: number;
  name: string;
  type: 'TEXT' | 'VOICE' | 'CATEGORY';
  topic: string | null;
  nsfw?: boolean;
  parentId?: number | null;
  createdAt: string;
  updatedAt: string;
  server?: {
    id: number;
    name: string;
  };
}

export interface Message {
  id: number;
  content: string;
  createdAt: string;
  updatedAt: string;
  author: User;
  channel: { id: number; name: string };
  attachments?: Attachment[];
  edited: boolean;
  deleted: boolean;
}

export interface Attachment {
  id: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  url: string;
}

export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface ApiResponse<T> {
  data: T;
  message?: string;
  status: number;
}
