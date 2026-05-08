<template>
  <aside class="friends-sidebar">
    <header class="friends-header">
      <h2>私信</h2>
      <button class="header-icon" type="button" title="添加好友" @click="focusAddFriend">
        <UserPlus :size="18" aria-hidden="true" />
      </button>
    </header>

    <div class="dm-search">
      <Search :size="17" aria-hidden="true" />
      <input v-model="searchQuery" type="text" placeholder="寻找或开始新的对话" aria-label="寻找或开始新的对话" />
    </div>

    <nav class="friends-nav" aria-label="好友导航">
      <button class="nav-item" :class="{ active: activeHomeTarget === 'friends' && route.name === 'Friends' }" type="button" @click="openFriends">
        <Users :size="20" aria-hidden="true" />
        <span>好友</span>
      </button>
      <button class="nav-item" :class="{ active: activeHomeTarget === 'pending' && route.name === 'Friends' }" type="button" @click="focusPendingRequests">
        <Inbox :size="20" aria-hidden="true" />
        <span>好友请求</span>
      </button>
    </nav>

    <div class="dm-list">
      <span class="dm-title">直接消息</span>
      <p v-if="isLoading">正在加载私信……</p>
      <p v-else-if="filteredConversations.length === 0">还没有私信会话。</p>
      <button
        v-for="conversation in filteredConversations"
        :key="conversation.id"
        class="dm-item"
        :class="{ active: currentConversationId === conversation.id }"
        type="button"
        @click="openConversation(conversation.id)"
      >
        <span class="dm-avatar">
          <AvatarImage :src="conversation.otherUser.avatarUrl || defaultAvatarUrl" :alt="displayNameOf(conversation.otherUser)" />
          <i :class="['status-dot', conversation.otherUser.status || 'offline']"></i>
        </span>
        <span class="dm-copy">
          <strong>{{ displayNameOf(conversation.otherUser) }}</strong>
          <small>{{ lastMessagePreview(conversation) }}</small>
        </span>
        <span v-if="hasDirectUnread(conversation.id)" class="dm-red-dot" aria-hidden="true"></span>
      </button>
    </div>

    <UserControlPanel show-logout @logout="logout" />
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import { Inbox, Search, UserPlus, Users } from 'lucide-vue-next';
import UserControlPanel from './UserControlPanel.vue';
import { useDirectMessageStore } from '../stores/directMessageStore';
import { useUnreadStore } from '../stores/unreadStore';
import { useUserStore } from '../stores/userStore';
import { useVoiceStore } from '../stores/voiceStore';
import type { DirectConversation, User } from '../types';
import { parseDirectInviteMessage } from '../utils/inviteMessage';
import { displayUserLabel } from '../utils/userDisplay';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const voiceStore = useVoiceStore();
const directMessageStore = useDirectMessageStore();
const unreadStore = useUnreadStore();
const { conversations, currentConversationId, isLoading } = storeToRefs(directMessageStore);
const { hasDirectUnread, markDirectRead } = unreadStore;

const defaultAvatarUrl = '/logo.png';
const searchQuery = ref('');
const activeHomeTarget = ref<'friends' | 'pending'>('friends');

const focusAddFriend = () => {
  activeHomeTarget.value = 'friends';
  if (route.name !== 'Friends') {
    router.push('/');
  }
  window.setTimeout(() => {
    window.dispatchEvent(new CustomEvent('nexacord:focus-friend-tab', { detail: { tab: 'add' } }));
  }, 30);
};

const focusPendingRequests = () => {
  activeHomeTarget.value = 'pending';
  if (route.name !== 'Friends') {
    router.push('/');
  }
  window.setTimeout(() => {
    window.dispatchEvent(new CustomEvent('nexacord:focus-friend-tab', { detail: { tab: 'pending' } }));
  }, 30);
};

const openFriends = () => {
  activeHomeTarget.value = 'friends';
  if (route.name !== 'Friends') {
    router.push('/');
  }
  window.setTimeout(() => {
    window.dispatchEvent(new CustomEvent('nexacord:focus-friend-tab', { detail: { tab: 'online' } }));
  }, 30);
};

const filteredConversations = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) {
    return conversations.value;
  }

  return conversations.value.filter((conversation) => {
    const user = conversation.otherUser;
    return (
      user.username.toLowerCase().includes(query) ||
      (user.displayName || '').toLowerCase().includes(query)
    );
  });
});

const displayNameOf = (user: Pick<User, 'username' | 'displayName'>) => displayUserLabel(user);

const statusLabel = (status: User['status']) => {
  const labels = {
    online: '在线',
    offline: '离线',
    away: '闲置',
    dnd: '请勿打扰',
  } as const;

  return labels[status] || '离线';
};

const lastMessagePreview = (conversation: DirectConversation) => {
  if (!conversation.lastMessage) {
    return statusLabel(conversation.otherUser.status);
  }

  if (parseDirectInviteMessage(conversation.lastMessage.content)) {
    return '[服务器邀请]';
  }

  return conversation.lastMessage.content || '[图片]';
};

const openConversation = (conversationId: number) => {
  activeHomeTarget.value = 'friends';
  markDirectRead(conversationId);
  router.push(`/direct/${conversationId}`);
};

const logout = () => {
  voiceStore.leaveChannel();
  userStore.logout();
  router.push('/login');
};

onMounted(() => {
  directMessageStore.initializeRealtime();
  void directMessageStore.fetchConversations();
});

</script>

<style scoped>
.friends-sidebar {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-rows: auto auto auto 1fr auto;
  background: var(--discord-surface);
}

.friends-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--discord-border);
}

.friends-header h2 {
  margin: 0;
  font-size: 16px;
}

.header-icon,
.panel-action {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--discord-text-faint);
}

.header-icon:hover,
.panel-action:hover {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.dm-search {
  position: relative;
  padding: 10px;
}

.dm-search svg {
  position: absolute;
  left: 22px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--discord-text-faint);
}

.dm-search input {
  width: 100%;
  height: 36px;
  border: 0;
  border-radius: 6px;
  padding: 0 12px 0 38px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.friends-nav {
  display: grid;
  gap: 4px;
  padding: 10px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 42px;
  padding: 0 12px;
  border-radius: 6px;
  background: transparent;
  color: var(--discord-text-muted);
  font-weight: 800;
  text-align: left;
}

.nav-item:hover,
.nav-item.active {
  background: var(--discord-channel-hover);
  color: var(--discord-text);
}

.dm-list {
  min-height: 0;
  padding: 12px 16px;
  display: grid;
  align-content: start;
  gap: 4px;
  overflow-y: auto;
  color: var(--discord-text-faint);
  font-size: 13px;
}

.dm-title {
  display: block;
  margin-bottom: 8px;
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.dm-list p {
  margin: 0;
  line-height: 1.5;
}

.dm-item {
  position: relative;
  min-width: 0;
  min-height: 44px;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 6px;
  border-radius: 8px;
  background: transparent;
  color: var(--discord-text-muted);
  text-align: left;
}

.dm-red-dot {
  position: absolute;
  right: 8px;
  top: 50%;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--discord-red);
  transform: translateY(-50%);
}

.dm-item:hover,
.dm-item.active {
  background: var(--discord-channel-hover);
  color: var(--discord-text);
}

.dm-avatar {
  position: relative;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
}

.dm-avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.status-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 11px;
  height: 11px;
  border: 3px solid var(--discord-surface);
  border-radius: 50%;
  background: var(--discord-green);
}

.status-dot.offline {
  background: #80848e;
}

.status-dot.away {
  background: #f0b232;
}

.status-dot.dnd {
  background: var(--discord-red);
}

.dm-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.dm-copy strong,
.dm-copy small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dm-copy strong {
  font-size: 14px;
}

.dm-copy small {
  color: var(--discord-text-faint);
  font-size: 12px;
}

.user-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 6px;
  padding: 8px;
  background: var(--discord-surface-soft);
}

.user-card {
  min-width: 0;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  border-radius: 8px;
  padding: 6px;
  background: transparent;
  color: var(--discord-text);
  text-align: left;
}

.user-card:hover {
  background: var(--discord-hover);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
  overflow: hidden;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-meta {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.user-meta strong,
.user-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta strong {
  font-size: 14px;
}

.user-meta span {
  color: var(--discord-text-faint);
  font-size: 12px;
}
</style>
