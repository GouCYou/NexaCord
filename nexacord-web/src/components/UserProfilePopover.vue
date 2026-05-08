<template>
  <div v-if="isOpen && profileUser" class="popover-layer" @click="closePopover">
    <section class="user-popover" :style="popoverStyle" role="dialog" aria-label="用户资料卡" @click.stop>
      <div class="popover-banner" :style="bannerStyle">
        <div v-if="!isSelf" class="popover-tools">
          <button type="button" title="身份组" @click="toggleRoleMenu">
            <UserCheck :size="14" aria-hidden="true" />
          </button>
          <button type="button" title="更多" @click="showNotice('更多资料操作会继续放在这里。')">
            <MoreHorizontal :size="15" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div class="popover-body">
        <div class="popover-avatar">
          <AvatarImage :src="profileUser.avatarUrl || defaultAvatarUrl" :alt="displayName" />
          <i :class="['status-dot', profileUser.status || 'offline']"></i>
        </div>

        <div class="identity-block">
          <h2>{{ displayName }}</h2>
          <p>{{ usernameTag(profileUser.username) }}</p>
          <span v-if="roleText" class="role-badge">{{ roleText }}</span>
        </div>

        <p class="bio">{{ profileUser.bio || '这个用户还没有填写个人简介。' }}</p>

        <div v-if="serverName" class="mutual-server">
          <span class="server-chip">
            <img v-if="serverIconUrl" :src="serverIconUrl" :alt="serverName" />
            <ServerIcon v-else :size="14" aria-hidden="true" />
          </span>
          <span>{{ serverName }}</span>
        </div>

        <button v-if="isSelf" class="primary-profile-action" type="button" @click="openProfileEditor">
          <Pencil :size="14" aria-hidden="true" />
          <span>编辑个人资料</span>
        </button>

        <div v-else class="profile-actions">
          <button type="button" @click="openDirectMessage">
            <MessageCircle :size="14" aria-hidden="true" />
            <span>消息</span>
          </button>
          <button type="button" @click="startDirectCall">
            <PhoneCall :size="14" aria-hidden="true" />
            <span>呼叫</span>
          </button>
          <button type="button" @click="handleFriendAction">
            <UserMinus v-if="friendState === 'accepted'" :size="14" aria-hidden="true" />
            <UserPlus v-else :size="14" aria-hidden="true" />
            <span>{{ friendActionLabel }}</span>
          </button>
        </div>

        <div class="role-panel">
          <button class="add-role-button" type="button" @click="toggleRoleMenu">
            <Plus :size="14" aria-hidden="true" />
            <span>{{ role === 'ADMIN' ? '管理身份组' : '添加身份组' }}</span>
          </button>
          <div v-if="showRoleMenu" class="role-menu">
            <button type="button" :class="{ active: role === 'ADMIN' }" @click="updateRole('ADMIN')">
              管理员
            </button>
            <button type="button" :class="{ active: role === 'MEMBER' }" @click="updateRole('MEMBER')">
              成员
            </button>
          </div>
        </div>

        <p v-if="popoverNotice" class="popover-notice">{{ popoverNotice }}</p>

        <div v-if="!isSelf" class="dm-input">
          <input
            v-model="quickMessage"
            :placeholder="`私信 ${usernameTag(profileUser.username)}`"
            @keydown.enter.prevent="sendQuickMessage"
          />
          <button type="button" title="插入表情" @click="showEmojiMenu = !showEmojiMenu">
            <Smile :size="15" aria-hidden="true" />
          </button>
          <div v-if="showEmojiMenu" class="emoji-menu">
            <button v-for="emoji in emojiOptions" :key="emoji" type="button" @click="insertEmoji(emoji)">
              {{ emoji }}
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import {
  MessageCircle,
  MoreHorizontal,
  Pencil,
  PhoneCall,
  Plus,
  Server as ServerIcon,
  Smile,
  UserCheck,
  UserMinus,
  UserPlus,
} from 'lucide-vue-next';
import friendService from '../services/friendService';
import { useDirectMessageStore } from '../stores/directMessageStore';
import { useServerStore } from '../stores/serverStore';
import { useUserStore } from '../stores/userStore';
import { useVoiceStore } from '../stores/voiceStore';
import type { MemberRole, User } from '../types';
import { displayUserLabel, usernameTag } from '../utils/userDisplay';

type PopoverUser = Partial<User> & {
  id: number;
  username: string;
  displayName?: string | null;
  avatarUrl?: string | null;
  status?: User['status'];
};

type OpenUserPopoverDetail = {
  user: PopoverUser;
  memberId?: number | null;
  role?: MemberRole;
  serverName?: string | null;
  serverIconUrl?: string | null;
  x: number;
  y: number;
};

const userStore = useUserStore();
const serverStore = useServerStore();
const directMessageStore = useDirectMessageStore();
const voiceStore = useVoiceStore();
const router = useRouter();
const { currentUser } = storeToRefs(userStore);
const { currentServer } = storeToRefs(serverStore);

const defaultAvatarUrl = '/logo.png';
const isOpen = ref(false);
const profileUser = ref<PopoverUser | null>(null);
const role = ref<MemberRole | null>(null);
const serverName = ref('');
const serverIconUrl = ref<string | null>(null);
const quickMessage = ref('');
const popoverNotice = ref('');
const showRoleMenu = ref(false);
const showEmojiMenu = ref(false);
const memberId = ref<number | null>(null);
const friendshipId = ref<number | null>(null);
const friendState = ref<'unknown' | 'none' | 'accepted' | 'incoming' | 'outgoing'>('unknown');
const confirmRemoveFriend = ref(false);
const position = reactive({
  left: 0,
  top: 0,
});

const roleLabels: Record<MemberRole, string> = {
  OWNER: '服主',
  ADMIN: '管理员',
  MODERATOR: '协管',
  MEMBER: '成员',
};

const displayName = computed(() => {
  const user = profileUser.value;
  return displayUserLabel(user);
});

const isSelf = computed(() => Boolean(profileUser.value && currentUser.value?.id === profileUser.value.id));
const roleText = computed(() => (role.value ? roleLabels[role.value] : ''));
const canManageRole = computed(
  () =>
    Boolean(currentServer.value?.id) &&
    Boolean(memberId.value) &&
    !isSelf.value &&
    role.value !== 'OWNER'
);
const friendActionLabel = computed(() => {
  if (friendState.value === 'accepted') {
    return confirmRemoveFriend.value ? '确认删除好友' : '删除好友';
  }
  if (friendState.value === 'incoming') {
    return '待接受';
  }
  if (friendState.value === 'outgoing') {
    return '已申请';
  }
  return '添加好友';
});
const emojiOptions = ['😀', '😂', '👍', '🎮', '🔥', '❤️'];

const bannerStyle = computed(() => {
  const user = profileUser.value;
  const style: Record<string, string> = {
    backgroundColor: user?.bannerColor || '#5865f2',
  };

  if (user?.bannerUrl) {
    style.backgroundImage = `url(${user.bannerUrl})`;
  }

  return style;
});

const popoverStyle = computed(() => ({
  left: `${position.left}px`,
  top: `${position.top}px`,
}));

const clampPosition = (x: number, y: number) => {
  const margin = 12;
  const cardWidth = 252;
  const cardHeight = 420;
  const windowWidth = window.innerWidth || 1280;
  const windowHeight = window.innerHeight || 720;
  const preferredRight = x + 12;
  const preferredLeft = x - cardWidth - 12;

  position.left =
    preferredRight + cardWidth <= windowWidth - margin
    ? preferredRight
    : Math.max(margin, preferredLeft);
  position.top = Math.min(
    Math.max(margin, y - 20),
    Math.max(margin, windowHeight - cardHeight - margin)
  );
};

const openPopover = (event: Event) => {
  const detail = (event as CustomEvent<OpenUserPopoverDetail>).detail;
  if (!detail?.user) {
    return;
  }

  const enrichedUser =
    currentUser.value && currentUser.value.id === detail.user.id
      ? { ...detail.user, ...currentUser.value }
      : detail.user;

  profileUser.value = enrichedUser;
  role.value = detail.role || null;
  memberId.value = detail.memberId || null;
  serverName.value = detail.serverName || currentServer.value?.name || '';
  serverIconUrl.value = detail.serverIconUrl || currentServer.value?.iconUrl || null;
  quickMessage.value = '';
  popoverNotice.value = '';
  showRoleMenu.value = false;
  showEmojiMenu.value = false;
  friendState.value = 'unknown';
  friendshipId.value = null;
  confirmRemoveFriend.value = false;
  clampPosition(detail.x, detail.y);
  isOpen.value = true;
  void refreshFriendState();
};

const closePopover = () => {
  isOpen.value = false;
  popoverNotice.value = '';
  showRoleMenu.value = false;
  showEmojiMenu.value = false;
  confirmRemoveFriend.value = false;
};

const showNotice = (message: string) => {
  popoverNotice.value = message;
};

const refreshFriendState = async () => {
  if (isSelf.value || !profileUser.value) {
    friendState.value = 'none';
    return;
  }

  const targetId = profileUser.value.id;
  try {
    const [friends, incoming, outgoing] = await Promise.all([
      friendService.getFriends(),
      friendService.getIncomingRequests(),
      friendService.getOutgoingRequests(),
    ]);
    if (friends.some((friendship) => friendship.user.id === targetId)) {
      friendshipId.value = friends.find((friendship) => friendship.user.id === targetId)?.id || null;
      friendState.value = 'accepted';
      return;
    }
    if (incoming.some((friendship) => friendship.user.id === targetId)) {
      friendshipId.value = incoming.find((friendship) => friendship.user.id === targetId)?.id || null;
      friendState.value = 'incoming';
      return;
    }
    if (outgoing.some((friendship) => friendship.user.id === targetId)) {
      friendshipId.value = outgoing.find((friendship) => friendship.user.id === targetId)?.id || null;
      friendState.value = 'outgoing';
      return;
    }
    friendshipId.value = null;
    friendState.value = 'none';
  } catch {
    friendState.value = 'unknown';
  }
};

const handleFriendAction = async () => {
  if (!profileUser.value) {
    return;
  }

  if (friendState.value === 'accepted') {
    if (!confirmRemoveFriend.value) {
      confirmRemoveFriend.value = true;
      showNotice('再次点击“确认删除好友”会解除好友关系。');
      return;
    }

    if (!friendshipId.value) {
      await refreshFriendState();
      showNotice('暂时没有找到这条好友关系，请稍后再试。');
      return;
    }

    try {
      await friendService.deleteFriendship(friendshipId.value);
      friendState.value = 'none';
      friendshipId.value = null;
      confirmRemoveFriend.value = false;
      showNotice('已删除好友。');
      return;
    } catch (err: any) {
      showNotice(err.response?.data?.error || err.response?.data?.message || '删除好友失败。');
      return;
    }
  }

  confirmRemoveFriend.value = false;
  if (friendState.value === 'incoming') {
    if (friendshipId.value) {
      try {
        await friendService.acceptFriendRequest(friendshipId.value);
        await refreshFriendState();
        showNotice('好友请求已接受。');
        return;
      } catch (err: any) {
        showNotice(err.response?.data?.error || err.response?.data?.message || '接受好友请求失败。');
        return;
      }
    }
    return;
  }
  if (friendState.value === 'outgoing') {
    showNotice('好友请求已经发送，等待对方接受。');
    return;
  }

  try {
    await friendService.createFriendRequest(profileUser.value.username);
    friendState.value = 'outgoing';
    showNotice('好友请求已发送。');
  } catch (err: any) {
    showNotice(err.response?.data?.error || err.response?.data?.message || '发送好友请求失败。');
    await refreshFriendState();
  }
};

const toggleRoleMenu = () => {
  if (!profileUser.value || isSelf.value) {
    return;
  }

  if (!memberId.value) {
    showNotice('请在服务器成员列表中打开资料卡后再调整身份组。');
    return;
  }
  if (!canManageRole.value) {
    showNotice('不能修改这个成员的身份组。');
    return;
  }

  showRoleMenu.value = !showRoleMenu.value;
};

const updateRole = async (nextRole: Extract<MemberRole, 'ADMIN' | 'MEMBER'>) => {
  if (!currentServer.value?.id || !memberId.value || !canManageRole.value) {
    return;
  }

  const member = await serverStore.updateServerMemberRole(currentServer.value.id, memberId.value, nextRole);
  if (member) {
    role.value = member.role;
    showRoleMenu.value = false;
    showNotice('身份组已更新。');
  } else {
    showNotice(serverStore.error || '更新身份组失败。');
  }
};

const openProfileEditor = () => {
  closePopover();
  window.dispatchEvent(new CustomEvent('nexacord:open-profile'));
};

const openDirectMessage = async () => {
  if (!profileUser.value) {
    return;
  }

  const conversation = await directMessageStore.openConversationWithUser(profileUser.value);
  if (conversation) {
    closePopover();
    router.push(`/direct/${conversation.id}`);
  }
};

const sendQuickMessage = async () => {
  const content = quickMessage.value.trim();
  if (!profileUser.value || !content) {
    return;
  }

  const conversation = await directMessageStore.openConversationWithUser(profileUser.value);
  if (!conversation) {
    return;
  }

  const success = await directMessageStore.sendMessage(conversation.id, content);
  if (success) {
    quickMessage.value = '';
    closePopover();
    router.push(`/direct/${conversation.id}`);
  }
};

const startDirectCall = () => {
  if (!profileUser.value) {
    return;
  }

  voiceStore.startDirectCall(profileUser.value);
  closePopover();
};

const insertEmoji = (emoji: string) => {
  quickMessage.value = `${quickMessage.value}${emoji}`;
  showEmojiMenu.value = false;
};

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    closePopover();
  }
};

onMounted(() => {
  window.addEventListener('nexacord:open-user-popover', openPopover as EventListener);
  window.addEventListener('keydown', handleKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:open-user-popover', openPopover as EventListener);
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped>
.popover-layer {
  position: fixed;
  inset: 0;
  z-index: 95;
  background: transparent;
}

.user-popover {
  position: fixed;
  width: min(252px, calc(100vw - 24px));
  overflow: hidden;
  border: 1px solid var(--discord-border);
  border-radius: 9px;
  background: var(--discord-bg);
  box-shadow: 0 16px 46px rgba(0, 0, 0, 0.28);
  animation: popover-in 120ms ease-out;
}

.popover-banner {
  position: relative;
  height: 82px;
  background:
    linear-gradient(135deg, rgba(88, 101, 242, 0.72), rgba(235, 69, 158, 0.34)),
    var(--discord-brand);
  background-size: cover;
  background-position: center;
}

.popover-tools {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
}

.popover-tools button {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--discord-surface-alt) 86%, transparent);
  color: var(--discord-text);
}

.popover-tools button:hover {
  background: var(--discord-hover-strong);
}

.popover-body {
  position: relative;
  display: grid;
  gap: 8px;
  padding: 42px 12px 12px;
}

.popover-avatar {
  position: absolute;
  top: -36px;
  left: 12px;
  width: 74px;
  height: 74px;
  border: 5px solid var(--discord-bg);
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
}

.popover-avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.status-dot {
  position: absolute;
  right: 4px;
  bottom: 5px;
  width: 13px;
  height: 13px;
  border: 3px solid var(--discord-bg);
  border-radius: 50%;
  background: var(--discord-green);
}

.status-dot.away {
  background: #f0b232;
}

.status-dot.dnd {
  background: var(--discord-red);
}

.status-dot.offline {
  background: #80848e;
}

.identity-block {
  display: grid;
  gap: 3px;
}

.identity-block h2,
.identity-block p,
.bio {
  margin: 0;
}

.identity-block h2 {
  font-size: 18px;
  line-height: 1.1;
}

.identity-block p {
  color: var(--discord-text-muted);
  font-size: 12px;
}

.role-badge {
  width: fit-content;
  padding: 3px 6px;
  border-radius: 999px;
  background: var(--discord-muted-surface);
  color: var(--discord-text);
  font-size: 11px;
  font-weight: 900;
}

.bio {
  color: var(--discord-text);
  font-size: 12px;
  line-height: 1.4;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.mutual-server {
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 8px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  color: var(--discord-text);
  font-size: 12px;
  font-weight: 800;
}

.server-chip {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: #f2f3f5;
  color: #1e1f22;
  font-size: 11px;
  flex: 0 0 auto;
  overflow: hidden;
}

.server-chip img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.mutual-server span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.primary-profile-action,
.profile-actions button {
  min-height: 34px;
  border-radius: 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 900;
}

.primary-profile-action {
  background: var(--discord-brand);
  color: white;
}

.primary-profile-action:hover {
  background: var(--discord-brand-hover);
}

.profile-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
}

.profile-actions button:nth-child(3) {
  grid-column: 1 / -1;
}

.profile-actions button {
  background: var(--discord-muted-surface);
  color: var(--discord-text);
}

.profile-actions button:hover,
.add-role-button:hover {
  background: var(--discord-hover-strong);
}

.add-role-button {
  width: fit-content;
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  background: transparent;
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.role-panel {
  position: relative;
}

.role-menu {
  position: absolute;
  left: 0;
  top: calc(100% + 6px);
  z-index: 2;
  min-width: 150px;
  display: grid;
  gap: 4px;
  padding: 8px;
  border: 1px solid var(--discord-border);
  border-radius: 10px;
  background: var(--discord-elevated);
  box-shadow: var(--discord-shadow);
}

.role-menu button {
  min-height: 34px;
  border-radius: 7px;
  background: transparent;
  color: var(--discord-text);
  font-size: 12px;
  font-weight: 900;
  text-align: left;
}

.role-menu button:hover,
.role-menu button.active {
  background: var(--discord-hover);
}

.popover-notice {
  margin: 0;
  padding: 8px 9px;
  border-radius: 8px;
  background: var(--discord-subtle);
  color: var(--discord-text-muted);
  font-size: 12px;
  line-height: 1.45;
}

.dm-input {
  position: relative;
  min-height: 38px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 0 9px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  background: var(--discord-input);
  color: var(--discord-text-faint);
}

.dm-input input {
  min-width: 0;
  border: 0;
  background: transparent;
  color: var(--discord-text);
  font-size: 12px;
}

.dm-input input:focus {
  outline: none;
}

.dm-input button {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--discord-text-faint);
}

.dm-input button:hover {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.emoji-menu {
  position: absolute;
  right: 0;
  bottom: calc(100% + 6px);
  display: flex;
  gap: 4px;
  padding: 7px;
  border: 1px solid var(--discord-border);
  border-radius: 999px;
  background: var(--discord-elevated);
  box-shadow: var(--discord-shadow);
}

.emoji-menu button {
  width: 28px;
  height: 28px;
  font-size: 15px;
}

@keyframes popover-in {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
