<template>
  <aside class="channel-list">
    <header class="channel-header">
      <div class="server-meta">
        <h2>{{ currentServer?.name || '选择一个服务器' }}</h2>
        <span class="server-meta-subtitle">
          {{ currentServer ? `当前共有 ${channels.length} 个频道` : '先从左侧选择或创建一个服务器。' }}
        </span>
      </div>

      <button
        class="header-icon"
        type="button"
        title="创建频道"
        :disabled="!currentServerId"
        @click="showCreateChannelModal = true"
      >
        +
      </button>
    </header>

    <div class="channel-scroll">
      <template v-if="currentServerId">
        <div v-if="isLoading" class="empty-state">
          <strong>正在加载频道...</strong>
          <p>正在获取这个服务器里的频道列表。</p>
        </div>

        <div v-else-if="channels.length === 0" class="empty-state">
          <strong>这个服务器还没有频道</strong>
          <p>先创建一个文字频道开始讨论，或者添加一个语音频道用于实时交流。</p>
          <button class="empty-state-button" type="button" @click="showCreateChannelModal = true">
            创建频道
          </button>
        </div>

        <template v-else>
          <section class="channel-group">
            <button class="group-toggle" type="button" @click="toggleCategory('TEXT')">
              <span>{{ collapsedCategories.TEXT ? '>' : 'v' }}</span>
              <span>文字频道</span>
            </button>

            <div v-show="!collapsedCategories.TEXT" class="channel-items">
              <button
                v-for="channel in textChannels"
                :key="channel.id"
                class="channel-item"
                :class="{ active: currentChannelId === channel.id }"
                :title="channel.topic || channel.name"
                type="button"
                @click="selectChannel(channel.id)"
              >
                <span class="channel-prefix">#</span>
                <span class="channel-label">{{ channel.name }}</span>
              </button>

              <p v-if="textChannels.length === 0" class="channel-empty">还没有文字频道。</p>
            </div>
          </section>

          <section class="channel-group">
            <button class="group-toggle" type="button" @click="toggleCategory('VOICE')">
              <span>{{ collapsedCategories.VOICE ? '>' : 'v' }}</span>
              <span>语音频道</span>
            </button>

            <div v-show="!collapsedCategories.VOICE" class="channel-items">
              <button
                v-for="channel in voiceChannels"
                :key="channel.id"
                class="channel-item"
                :class="{ active: currentChannelId === channel.id }"
                :title="channel.topic || channel.name"
                type="button"
                @click="selectChannel(channel.id)"
              >
                <span class="channel-prefix">音</span>
                <span class="channel-label">{{ channel.name }}</span>
              </button>

              <p v-if="voiceChannels.length === 0" class="channel-empty">还没有语音频道。</p>
            </div>
          </section>
        </template>
      </template>

      <div v-else class="empty-state">
        <strong>还没有选中服务器</strong>
        <p>从左侧选择一个服务器，或者先创建一个新的服务器开始使用。</p>
      </div>
    </div>

    <footer v-if="currentUser" class="user-panel">
      <div class="user-avatar">
        <img v-if="currentUser.avatarUrl" :src="currentUser.avatarUrl" :alt="currentUser.username" />
        <span v-else>{{ currentUser.username.charAt(0).toUpperCase() }}</span>
      </div>

      <div class="user-meta">
        <strong>{{ currentUser.username }}</strong>
        <span>{{ statusLabel }}</span>
      </div>

      <button class="logout-button" type="button" @click="logout">退出登录</button>
    </footer>

    <div v-if="showCreateChannelModal" class="modal-overlay" @click="closeModal">
      <div class="modal-card" @click.stop>
        <header class="modal-header">
          <div>
            <h2>创建频道</h2>
            <p>为 {{ currentServer?.name || '当前服务器' }} 添加一个新的讨论空间。</p>
          </div>
          <button class="icon-button" type="button" @click="closeModal">x</button>
        </header>

        <div class="modal-body">
          <label class="field">
            <span>频道名称</span>
            <input v-model="newChannel.name" type="text" placeholder="例如：综合讨论" />
          </label>

          <label class="field">
            <span>频道类型</span>
            <select v-model="newChannel.type">
              <option value="TEXT">文字频道</option>
              <option value="VOICE">语音频道</option>
            </select>
          </label>

          <label class="field">
            <span>频道主题</span>
            <input v-model="newChannel.topic" type="text" placeholder="补充说明这个频道主要做什么" />
          </label>

          <p v-if="error" class="form-error">{{ error }}</p>
        </div>

        <footer class="modal-footer">
          <button class="secondary-button" type="button" @click="closeModal">取消</button>
          <button
            class="primary-button"
            type="button"
            :disabled="isLoading || !newChannel.name.trim() || !currentServerId"
            @click="createChannel"
          >
            {{ isLoading ? '创建中...' : '创建频道' }}
          </button>
        </footer>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useRouter } from 'vue-router';
import { useChannelStore } from '../stores/channelStore';
import { useServerStore } from '../stores/serverStore';
import { useUserStore } from '../stores/userStore';

const router = useRouter();
const serverStore = useServerStore();
const channelStore = useChannelStore();
const userStore = useUserStore();

const { currentServer, currentServerId } = storeToRefs(serverStore);
const { channels, currentChannelId, isLoading, error } = storeToRefs(channelStore);
const { currentUser } = storeToRefs(userStore);

const showCreateChannelModal = ref(false);
const newChannel = ref({
  name: '',
  type: 'TEXT' as 'TEXT' | 'VOICE',
  topic: '',
});

const collapsedCategories = ref({
  TEXT: false,
  VOICE: false,
});

const textChannels = computed(() => channels.value.filter((channel) => channel.type === 'TEXT'));
const voiceChannels = computed(() => channels.value.filter((channel) => channel.type === 'VOICE'));

const statusLabel = computed(() => {
  const status = currentUser.value?.status || 'online';
  const labelMap = {
    online: '在线',
    offline: '离线',
    away: '离开',
    dnd: '请勿打扰',
  } as const;

  return labelMap[status] || '在线';
});

const openCreateChannelModal = () => {
  if (!currentServerId.value) {
    return;
  }

  showCreateChannelModal.value = true;
};

const toggleCategory = (category: 'TEXT' | 'VOICE') => {
  collapsedCategories.value[category] = !collapsedCategories.value[category];
};

const selectChannel = (channelId: number) => {
  if (!currentServerId.value) {
    return;
  }

  channelStore.setCurrentChannel(channelId);
  router.push(`/server/${currentServerId.value}/channel/${channelId}`);
};

const closeModal = () => {
  showCreateChannelModal.value = false;
  newChannel.value = {
    name: '',
    type: 'TEXT',
    topic: '',
  };
};

const createChannel = async () => {
  if (!currentServerId.value || !newChannel.value.name.trim()) {
    return;
  }

  const success = await channelStore.createChannel(currentServerId.value, {
    name: newChannel.value.name.trim(),
    type: newChannel.value.type,
    topic: newChannel.value.topic.trim(),
    nsfw: false,
    parentId: null,
  });

  if (success && channelStore.currentChannelId) {
    closeModal();
    router.push(`/server/${currentServerId.value}/channel/${channelStore.currentChannelId}`);
  }
};

const logout = () => {
  userStore.logout();
  router.push('/login');
};

onMounted(() => {
  window.addEventListener('nexacord:create-channel', openCreateChannelModal);
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:create-channel', openCreateChannelModal);
});
</script>

<style scoped>
.channel-list {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr auto;
  background: #2b2d31;
}

.channel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--discord-border);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.24);
}

.server-meta h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
}

.server-meta-subtitle {
  display: block;
  margin-top: 4px;
  color: var(--discord-text-faint);
  font-size: 12px;
}

.header-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: transparent;
  color: var(--discord-text-faint);
  font-size: 20px;
}

.header-icon:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.06);
  color: var(--discord-text);
}

.header-icon:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.channel-scroll {
  min-height: 0;
  padding: 10px 10px 14px;
  overflow-y: auto;
}

.channel-group + .channel-group {
  margin-top: 12px;
}

.group-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  border-radius: 6px;
  background: transparent;
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  text-align: left;
}

.group-toggle:hover {
  color: var(--discord-text);
}

.channel-items {
  display: grid;
  gap: 2px;
  padding-top: 4px;
}

.channel-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: transparent;
  color: var(--discord-text-muted);
  text-align: left;
}

.channel-item:hover {
  background: var(--discord-channel-hover);
  color: var(--discord-text);
}

.channel-item.active {
  background: rgba(255, 255, 255, 0.08);
  color: var(--discord-text);
}

.channel-prefix {
  flex-shrink: 0;
  width: 18px;
  text-align: center;
  color: inherit;
  font-size: 12px;
  font-weight: 700;
}

.channel-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.channel-empty,
.empty-state p {
  margin: 0;
  color: var(--discord-text-faint);
  font-size: 13px;
}

.empty-state {
  display: grid;
  gap: 8px;
  place-items: start;
  padding: 14px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
}

.empty-state strong {
  font-size: 15px;
}

.empty-state-button {
  margin-top: 4px;
  padding: 10px 14px;
  border-radius: 10px;
  background: var(--discord-brand);
  color: white;
  font-weight: 700;
}

.empty-state-button:hover {
  background: var(--discord-brand-hover);
}

.user-panel {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #232428;
  border-top: 1px solid var(--discord-border);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-weight: 800;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-meta {
  flex: 1;
  min-width: 0;
  display: grid;
}

.user-meta strong,
.user-meta span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.user-meta strong {
  font-size: 14px;
}

.user-meta span {
  color: var(--discord-text-faint);
  font-size: 12px;
}

.logout-button {
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 700;
}

.logout-button:hover {
  background: rgba(255, 255, 255, 0.1);
  color: var(--discord-text);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(0, 0, 0, 0.72);
  backdrop-filter: blur(8px);
}

.modal-card {
  width: min(420px, 100%);
  border: 1px solid var(--discord-border);
  border-radius: 18px;
  background: #2b2d31;
  box-shadow: var(--discord-shadow);
}

.modal-header,
.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 20px 20px 0;
}

.modal-header h2 {
  margin: 0;
  font-size: 22px;
}

.modal-header p {
  margin: 6px 0 0;
  color: var(--discord-text-muted);
  font-size: 14px;
}

.modal-body {
  display: grid;
  gap: 16px;
  padding: 20px;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.field input,
.field select {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid rgba(0, 0, 0, 0.32);
  border-radius: 10px;
  background: #1e1f22;
  color: var(--discord-text);
}

.form-error {
  margin: 0;
  color: #ff8b8d;
  font-size: 13px;
}

.modal-footer {
  padding: 0 20px 20px;
}

.icon-button,
.secondary-button,
.primary-button {
  border-radius: 10px;
  font-weight: 700;
}

.icon-button {
  width: 36px;
  height: 36px;
  background: transparent;
  color: var(--discord-text-faint);
  font-size: 18px;
  line-height: 1;
}

.icon-button:hover {
  background: rgba(255, 255, 255, 0.06);
  color: var(--discord-text);
}

.secondary-button,
.primary-button {
  min-width: 88px;
  padding: 10px 16px;
}

.secondary-button {
  background: rgba(255, 255, 255, 0.08);
  color: var(--discord-text);
}

.secondary-button:hover {
  background: rgba(255, 255, 255, 0.12);
}

.primary-button {
  background: var(--discord-brand);
  color: white;
}

.primary-button:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
