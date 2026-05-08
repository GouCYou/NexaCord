export interface User {
  id: number;
  username: string;
  displayName?: string | null;
  email: string;
  avatarUrl: string | null;
  bannerUrl?: string | null;
  bannerColor?: string | null;
  bio?: string | null;
  status: 'online' | 'offline' | 'away' | 'dnd';
  createdAt?: string;
  updatedAt?: string;
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

export type MemberRole = 'OWNER' | 'ADMIN' | 'MODERATOR' | 'MEMBER';

export interface Member {
  id: number;
  user: User;
  nickname: string | null;
  avatarUrl: string | null;
  role: MemberRole;
  joinedAt: string;
}

export interface ServerInvite {
  id: number;
  code: string;
  serverId: number;
  serverName: string;
  serverIconUrl: string | null;
  creatorName: string;
  expiresAt: string | null;
  maxUses: number | null;
  useCount: number | null;
  createdAt: string;
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
  displayName?: string;
  email: string;
  password: string;
  verificationCode: string;
}

export interface UserProfileUpdateRequest {
  username?: string;
  displayName?: string;
  email?: string;
  emailVerificationCode?: string;
  avatarUrl?: string | null;
  bannerUrl?: string | null;
  bannerColor?: string | null;
  bio?: string | null;
  status?: User['status'];
}

export interface EmailCodeRequest {
  email: string;
  purpose: 'REGISTER' | 'CHANGE_EMAIL' | 'RESET_PASSWORD';
}

export interface PasswordResetRequest {
  email: string;
  verificationCode: string;
  password: string;
}

export interface PasswordChangeRequest {
  verificationCode: string;
  password: string;
}

export interface Friendship {
  id: number;
  status: 'PENDING' | 'ACCEPTED' | 'BLOCKED';
  direction: 'INCOMING' | 'OUTGOING';
  user: User;
  createdAt: string;
  updatedAt: string;
}

export interface DirectMessage {
  id: number;
  conversationId: number;
  content: string;
  author: User;
  createdAt: string;
  updatedAt: string;
  edited: boolean;
  deleted: boolean;
  attachments?: Attachment[];
}

export interface DirectConversation {
  id: number;
  otherUser: User;
  lastMessage: DirectMessage | null;
  createdAt: string;
  updatedAt: string;
}

export interface DirectRealtimeEvent {
  type: 'CONVERSATION_UPDATED' | 'MESSAGE_CREATED' | 'MESSAGE_UPDATED' | 'MESSAGE_DELETED';
  conversation: DirectConversation;
  message: DirectMessage | null;
}

export interface ApiResponse<T> {
  data: T;
  message?: string;
  status: number;
}
