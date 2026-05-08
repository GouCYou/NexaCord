<template>
  <div v-if="isOpen" class="invite-overlay" @click="close">
    <section class="invite-modal" role="dialog" aria-modal="true" @click.stop>
      <header class="invite-header">
        <div>
          <h2>邀请朋友加入 {{ currentServer?.name || '服务器' }}</h2>
          <p>接收者将直接进入 {{ channelEntryLabel }}</p>
        </div>
        <button class="icon-button" type="button" aria-label="关闭邀请弹窗" @click="close">
          <X :size="26" aria-hidden="true" />
        </button>
      </header>

      <label class="invite-search">
        <Search :size="22" aria-hidden="true" />
        <input v-model="searchQuery" type="text" placeholder="搜索好友" />
      </label>

      <div class="friend-scroll">
        <div v-if="isLoading" class="invite-status">正在加载好友……</div>
        <div v-else-if="filteredFriends.length === 0" class="invite-status">
          暂时没有可邀请的好友。
        </div>

        <template v-else>
          <article
            v-for="friendship in filteredFriends"
            :key="friendship.id"
            class="friend-row"
          >
            <div class="friend-avatar">
              <img :src="friendship.user.avatarUrl || defaultAvatarUrl" :alt="usernameTag(friendship.user.username)" />
            </div>
            <div class="friend-copy">
              <strong>{{ usernameTag(friendship.user.username) }}</strong>
              <span>{{ friendship.user.email }}</span>
            </div>
            <button
              class="invite-button"
              type="button"
              :disabled="invitedUserIds.has(friendship.user.id) || invitingUserId === friendship.user.id"
              @click="inviteFriend(friendship.user.id)"
            >
              {{ invitedUserIds.has(friendship.user.id) ? '已发送' : invitingUserId === friendship.user.id ? '发送中' : '邀请' }}
            </button>
          </article>
        </template>
      </div>

      <footer class="invite-link-panel">
        <strong>或者，向好友发送服务器邀请链接</strong>
        <div class="invite-link-row">
          <input :value="inviteLink" readonly aria-label="服务器邀请链接" />
          <button class="copy-button" type="button" :disabled="!inviteLink" @click="copyInviteLink">
            {{ copyLabel }}
          </button>
        </div>
        <p>
          你的邀请链接将在 7 天后过期。
          <button type="button" @click="refreshInvite">重新生成邀请链接</button>
        </p>
      </footer>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { Search, X } from 'lucide-vue-next';
import friendService from '../services/friendService';
import { useChannelStore } from '../stores/channelStore';
import { useDirectMessageStore } from '../stores/directMessageStore';
import { useServerStore } from '../stores/serverStore';
import type { Friendship, ServerInvite } from '../types';
import { buildDirectInviteMessage } from '../utils/inviteMessage';
import { usernameTag } from '../utils/userDisplay';

const serverStore = useServerStore();
const channelStore = useChannelStore();
const directMessageStore = useDirectMessageStore();
const { currentServer, currentServerId } = storeToRefs(serverStore);
const { currentChannel } = storeToRefs(channelStore);

const defaultAvatarUrl = '/logo.png';
const isOpen = ref(false);
const isLoading = ref(false);
const searchQuery = ref('');
const friends = ref<Friendship[]>([]);
const currentInvite = ref<ServerInvite | null>(null);
const invitingUserId = ref<number | null>(null);
const invitedUserIds = ref(new Set<number>());
const copyLabel = ref('复制');

const channelEntryLabel = computed(() => {
  if (!currentChannel.value) {
    return '服务器首页';
  }

  return `${currentChannel.value.type === 'VOICE' ? '语音频道' : '#'} ${currentChannel.value.name}`;
});

const filteredFriends = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) {
    return friends.value;
  }

  return friends.value.filter((friendship) => {
    const user = friendship.user;
    return (
      user.username.toLowerCase().includes(query) ||
      user.email.toLowerCase().includes(query)
    );
  });
});

const inviteLink = computed(() => {
  if (!currentInvite.value?.code) {
    return '';
  }

  return `${window.location.origin}/invite/${currentInvite.value.code}`;
});

const loadInvite = async () => {
  if (!currentServerId.value) {
    return;
  }

  currentInvite.value = await serverStore.createServerInvite(currentServerId.value);
};

const open = async () => {
  if (!currentServerId.value) {
    return;
  }

  isOpen.value = true;
  isLoading.value = true;
  searchQuery.value = '';
  copyLabel.value = '复制';
  invitedUserIds.value = new Set();

  try {
    const [friendList] = await Promise.all([
      friendService.getFriends(),
      loadInvite(),
    ]);
    friends.value = friendList;
  } finally {
    isLoading.value = false;
  }
};

const close = () => {
  isOpen.value = false;
  searchQuery.value = '';
};

const inviteFriend = async (userId: number) => {
  if (!currentServerId.value) {
    return;
  }

  invitingUserId.value = userId;
  try {
    if (!currentInvite.value) {
      await loadInvite();
    }
    if (!currentInvite.value) {
      return;
    }

    const conversation = await directMessageStore.openConversationWithUser({ id: userId });
    if (!conversation) {
      return;
    }

    const sent = await directMessageStore.sendMessage(
      conversation.id,
      buildDirectInviteMessage(currentInvite.value)
    );
    if (sent) {
      invitedUserIds.value = new Set([...invitedUserIds.value, userId]);
    }
  } finally {
    invitingUserId.value = null;
  }
};

const refreshInvite = async () => {
  copyLabel.value = '复制';
  await loadInvite();
};

const copyInviteLink = async () => {
  if (!inviteLink.value) {
    return;
  }

  try {
    await navigator.clipboard.writeText(inviteLink.value);
  } catch {
    const textarea = document.createElement('textarea');
    textarea.value = inviteLink.value;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    textarea.remove();
  }

  copyLabel.value = '已复制';
  window.setTimeout(() => {
    copyLabel.value = '复制';
  }, 1600);
};

onMounted(() => {
  window.addEventListener('nexacord:open-invite', open);
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:open-invite', open);
});
</script>

<style scoped>
.invite-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--discord-overlay);
  animation: fade-in 120ms ease-out;
}

.invite-modal {
  width: min(560px, 100%);
  max-height: min(760px, calc(100vh - 48px));
  display: grid;
  grid-template-rows: auto auto minmax(160px, 1fr) auto;
  border: 1px solid var(--discord-border);
  border-radius: 14px;
  background: var(--discord-elevated);
  box-shadow: var(--discord-shadow);
  overflow: hidden;
  animation: modal-in 140ms ease-out;
}

.invite-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
  padding: 26px 28px 12px;
}

.invite-header h2 {
  margin: 0;
  color: var(--discord-text);
  font-size: 24px;
  line-height: 1.16;
}

.invite-header p {
  margin: 8px 0 0;
  color: var(--discord-text-muted);
  font-size: 15px;
  font-weight: 700;
}

.icon-button {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--discord-text-faint);
}

.icon-button:hover {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.invite-search {
  position: relative;
  margin: 16px 28px 14px;
}

.invite-search svg {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--discord-text-faint);
}

.invite-search input {
  width: 100%;
  height: 54px;
  border: 1px solid var(--discord-strong-border);
  border-radius: 8px;
  padding: 0 16px 0 46px;
  background: var(--discord-input);
  color: var(--discord-text);
  font-size: 18px;
}

.friend-scroll {
  min-height: 0;
  overflow-y: auto;
  padding: 4px 20px 16px 28px;
}

.invite-status {
  padding: 36px 8px;
  color: var(--discord-text-faint);
  text-align: center;
}

.friend-row {
  min-height: 64px;
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  padding: 8px 8px 8px 0;
  border-radius: 8px;
}

.friend-row:hover {
  background: var(--discord-hover);
}

.friend-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
  overflow: hidden;
}

.friend-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.friend-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.friend-copy strong,
.friend-copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.friend-copy strong {
  color: var(--discord-text);
  font-size: 18px;
}

.friend-copy span {
  color: var(--discord-text-faint);
  font-size: 14px;
}

.invite-button {
  min-width: 70px;
  padding: 11px 16px;
  border-radius: 10px;
  background: var(--discord-muted-surface);
  color: var(--discord-text);
  font-weight: 800;
}

.invite-button:hover:not(:disabled) {
  background: var(--discord-hover-strong);
}

.invite-button:disabled {
  opacity: 0.62;
  cursor: default;
}

.invite-link-panel {
  display: grid;
  gap: 12px;
  padding: 22px 28px 26px;
  border-top: 1px solid var(--discord-border);
  background: var(--discord-surface);
}

.invite-link-panel strong {
  color: var(--discord-text);
  font-size: 16px;
}

.invite-link-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  overflow: hidden;
  border: 1px solid var(--discord-strong-border);
  border-radius: 8px;
  background: var(--discord-input);
}

.invite-link-row input {
  min-width: 0;
  border: 0;
  padding: 13px 14px;
  background: transparent;
  color: var(--discord-text);
  font-size: 18px;
}

.copy-button {
  min-width: 82px;
  margin: 6px;
  border-radius: 6px;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
}

.copy-button:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

.copy-button:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.invite-link-panel p {
  margin: 0;
  color: var(--discord-text-faint);
  font-size: 13px;
}

.invite-link-panel p button {
  padding: 0;
  background: transparent;
  color: var(--discord-brand);
  font-weight: 800;
}

.invite-link-panel p button:hover {
  color: var(--discord-link-hover);
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes modal-in {
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
