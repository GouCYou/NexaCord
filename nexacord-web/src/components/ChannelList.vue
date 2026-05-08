<template>
  <aside class="channel-list">
    <header class="channel-header">
      <button
        class="server-title-button"
        type="button"
        :disabled="!currentServerId"
        @click.stop="toggleServerMenu"
      >
        <div class="server-meta">
          <h2>{{ currentServer?.name || '选择一个服务器' }}</h2>
        </div>
        <ChevronDown class="server-menu-chevron" :class="{ open: showServerMenu }" :size="18" aria-hidden="true" />
      </button>

      <button
        class="header-icon"
        type="button"
        title="创建频道"
        :disabled="!currentServerId"
        @click="showCreateChannelModal = true"
      >
        <Plus :size="20" aria-hidden="true" />
      </button>

      <div v-if="showServerMenu" class="server-menu" @click.stop>
        <button type="button" @click="openInviteModal">
          <UserPlus :size="18" aria-hidden="true" />
          <span>邀请成员</span>
        </button>
        <button type="button" @click="openServerSettings">
          <Settings :size="18" aria-hidden="true" />
          <span>服务器设置</span>
        </button>
        <button type="button" @click="openCreateFromMenu">
          <Plus :size="18" aria-hidden="true" />
          <span>创建频道</span>
        </button>
      </div>
    </header>

    <div class="channel-scroll">
      <template v-if="currentServerId">
        <div v-if="isLoading" class="empty-state">
          <strong>正在加载频道……</strong>
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
              <ChevronRight v-if="collapsedCategories.TEXT" :size="14" aria-hidden="true" />
              <ChevronDown v-else :size="14" aria-hidden="true" />
              <span>文字频道</span>
            </button>

            <div v-show="!collapsedCategories.TEXT" class="channel-items">
              <div
                v-for="channel in textChannels"
                :key="channel.id"
                class="channel-row"
                :class="{ active: currentChannelId === channel.id }"
              >
                <button
                  class="channel-item"
                  :title="channel.topic || channel.name"
                  type="button"
                  @click="selectChannel(channel.id)"
                >
                  <Hash class="channel-prefix" :size="18" aria-hidden="true" />
                  <span class="channel-label">{{ channel.name }}</span>
                </button>

                <div class="channel-actions">
                  <button type="button" title="邀请成员" @click.stop="openChannelInvite(channel.id)">
                    <UserPlus :size="16" aria-hidden="true" />
                  </button>
                  <button type="button" title="编辑频道" @click.stop="openChannelSettings(channel)">
                    <Settings :size="16" aria-hidden="true" />
                  </button>
                </div>
              </div>

              <p v-if="textChannels.length === 0" class="channel-empty">还没有文字频道。</p>
            </div>
          </section>

          <section class="channel-group">
            <button class="group-toggle" type="button" @click="toggleCategory('VOICE')">
              <ChevronRight v-if="collapsedCategories.VOICE" :size="14" aria-hidden="true" />
              <ChevronDown v-else :size="14" aria-hidden="true" />
              <span>语音频道</span>
            </button>

            <div v-show="!collapsedCategories.VOICE" class="channel-items">
              <template v-for="channel in voiceChannels" :key="channel.id">
                <div
                  class="channel-row"
                  :class="{ active: currentChannelId === channel.id, connected: activeVoiceChannelId === channel.id }"
                >
                  <button
                    class="channel-item"
                    :title="channel.topic || channel.name"
                    type="button"
                    @click="selectChannel(channel.id)"
                  >
                    <Volume2 class="channel-prefix" :size="18" aria-hidden="true" />
                    <span class="channel-label">{{ channel.name }}</span>
                    <span v-if="voiceCount(channel.id) > 0" class="voice-duration">{{ voiceCount(channel.id) }}</span>
                  </button>

                  <div class="channel-actions">
                    <button type="button" title="邀请成员" @click.stop="openChannelInvite(channel.id)">
                      <UserPlus :size="16" aria-hidden="true" />
                    </button>
                    <button type="button" title="编辑频道" @click.stop="openChannelSettings(channel)">
                      <Settings :size="16" aria-hidden="true" />
                    </button>
                  </div>
                </div>

                <div v-if="voiceParticipants(channel.id).length > 0" class="voice-members">
                  <div
                    v-for="participant in voiceParticipants(channel.id)"
                    :key="participant.id"
                    class="voice-member"
                    :class="{ speaking: activeVoiceChannelId === channel.id && isUserSpeaking(participant.id) }"
                    :style="voiceLevelStyle(participant.id)"
                  >
                    <button class="voice-member-avatar" type="button" @click.stop="openVoiceUserPopover(participant, $event)">
                      <img :src="participant.avatarUrl || defaultAvatarUrl" :alt="displayNameOf(participant)" />
                    </button>
                    <button class="voice-member-name" type="button" @click.stop="openVoiceUserPopover(participant, $event)">
                      {{ displayNameOf(participant) }}
                    </button>
                  </div>
                </div>
              </template>

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

    <UserControlPanel show-logout @logout="logout" />

    <ChannelSettingsModal
      v-if="showChannelSettingsModal && selectedChannel"
      :channel="selectedChannel"
      @close="closeChannelSettings"
      @saved="handleChannelSettingsSaved"
      @deleted="handleChannelDeleted"
    />

    <div v-if="showCreateChannelModal" class="modal-overlay" @click="closeModal">
      <div class="modal-card" @click.stop>
        <header class="modal-header">
          <div>
            <h2>创建频道</h2>
            <p>为 {{ currentServer?.name || '当前服务器' }} 添加一个新的讨论空间。</p>
          </div>
          <button class="icon-button" type="button" aria-label="关闭弹窗" @click="closeModal">
            <X :size="20" aria-hidden="true" />
          </button>
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
            {{ isLoading ? '创建中……' : '创建频道' }}
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
import { ChevronDown, ChevronRight, Hash, Plus, Settings, UserPlus, Volume2, X } from 'lucide-vue-next';
import ChannelSettingsModal from './ChannelSettingsModal.vue';
import UserControlPanel from './UserControlPanel.vue';
import { useChannelStore } from '../stores/channelStore';
import { useServerStore } from '../stores/serverStore';
import { useUserStore } from '../stores/userStore';
import { useVoiceStore, type VoiceUser } from '../stores/voiceStore';
import type { Channel } from '../types';

const router = useRouter();
const serverStore = useServerStore();
const channelStore = useChannelStore();
const userStore = useUserStore();
const voiceStore = useVoiceStore();

const { currentServer, currentServerId } = storeToRefs(serverStore);
const { channels, currentChannelId, isLoading, error } = storeToRefs(channelStore);
const {
  activeChannelId: activeVoiceChannelId,
} = storeToRefs(voiceStore);
const { displayNameOf, getParticipantCountForChannel, getParticipantsForChannel, getVoiceLevel, isUserSpeaking } = voiceStore;

const showCreateChannelModal = ref(false);
const showServerMenu = ref(false);
const showChannelSettingsModal = ref(false);
const selectedChannel = ref<Channel | null>(null);
const defaultAvatarUrl = '/logo.png';
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

const voiceLevelStyle = (userId: number) => ({
  '--voice-level': Math.max(0.18, getVoiceLevel(userId)).toFixed(2),
});

const voiceCount = (channelId: number) => getParticipantCountForChannel(channelId);
const voiceParticipants = (channelId: number) => getParticipantsForChannel(channelId);

const openCreateChannelModal = () => {
  if (!currentServerId.value) {
    return;
  }

  showCreateChannelModal.value = true;
};

const openCreateFromMenu = () => {
  showServerMenu.value = false;
  openCreateChannelModal();
};

const toggleServerMenu = () => {
  if (!currentServerId.value) {
    return;
  }

  showServerMenu.value = !showServerMenu.value;
};

const closeServerMenu = () => {
  showServerMenu.value = false;
};

const openInviteModal = () => {
  showServerMenu.value = false;
  window.dispatchEvent(new CustomEvent('nexacord:open-invite'));
};

const openChannelInvite = (channelId: number) => {
  selectChannel(channelId);
  window.dispatchEvent(new CustomEvent('nexacord:open-invite'));
};

const openChannelSettings = (channel: Channel) => {
  selectedChannel.value = channel;
  showChannelSettingsModal.value = true;
};

const openVoiceUserPopover = (user: VoiceUser, event: MouseEvent) => {
  window.dispatchEvent(new CustomEvent('nexacord:open-user-popover', {
    detail: {
      user,
      serverName: currentServer.value?.name,
      serverIconUrl: currentServer.value?.iconUrl,
      x: event.clientX,
      y: event.clientY,
    },
  }));
};

const closeChannelSettings = () => {
  selectedChannel.value = null;
  showChannelSettingsModal.value = false;
};

const handleChannelSettingsSaved = () => {
  closeChannelSettings();
};

const handleChannelDeleted = () => {
  closeChannelSettings();
  if (currentServerId.value && currentChannelId.value) {
    router.push(`/servers/${currentServerId.value}/channels/${currentChannelId.value}`);
    return;
  }

  if (currentServerId.value) {
    router.push(`/servers/${currentServerId.value}`);
    return;
  }

  router.push('/');
};

const openServerSettings = () => {
  showServerMenu.value = false;
  window.dispatchEvent(new CustomEvent('nexacord:open-server-settings', { detail: { tab: 'overview' } }));
};

const toggleCategory = (category: 'TEXT' | 'VOICE') => {
  collapsedCategories.value[category] = !collapsedCategories.value[category];
};

const selectChannel = (channelId: number) => {
  if (!currentServerId.value) {
    return;
  }

  channelStore.setCurrentChannel(channelId);
  router.push(`/servers/${currentServerId.value}/channels/${channelId}`);
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
    router.push(`/servers/${currentServerId.value}/channels/${channelStore.currentChannelId}`);
  }
};

const logout = () => {
  voiceStore.leaveChannel();
  userStore.logout();
  router.push('/login');
};

onMounted(() => {
  voiceStore.initializeRealtime();
  window.addEventListener('nexacord:create-channel', openCreateChannelModal);
  window.addEventListener('click', closeServerMenu);
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:create-channel', openCreateChannelModal);
  window.removeEventListener('click', closeServerMenu);
});
</script>

<style scoped>
.channel-list {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-rows: auto 1fr auto;
  background: var(--discord-surface);
}

.channel-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--discord-border);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.24);
}

.server-title-button {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0;
  background: transparent;
  color: var(--discord-text);
  text-align: left;
}

.server-title-button:disabled {
  cursor: default;
}

.server-meta h2 {
  margin: 0;
  font-size: 16px;
  font-weight: 800;
}

.server-menu-chevron {
  flex-shrink: 0;
  color: var(--discord-text-faint);
  transition: transform 140ms ease;
}

.server-menu-chevron.open {
  transform: rotate(180deg);
}

.server-menu {
  position: absolute;
  left: 12px;
  right: 12px;
  top: calc(100% + 8px);
  z-index: 25;
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid var(--discord-border);
  border-radius: 12px;
  background: var(--discord-elevated);
  box-shadow: var(--discord-shadow);
  animation: server-menu-in 120ms ease-out;
}

.server-menu button {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-radius: 8px;
  padding: 0 10px;
  background: transparent;
  color: var(--discord-text);
  font-weight: 800;
  text-align: left;
}

.server-menu button:hover {
  background: var(--discord-hover);
  color: white;
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
  background: var(--discord-muted-surface);
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

.channel-row {
  position: relative;
  display: flex;
  align-items: center;
  border-radius: 8px;
}

.channel-row:hover {
  background: var(--discord-channel-hover);
}

.channel-row.active {
  background: var(--discord-hover);
}

.channel-item {
  min-width: 0;
  flex: 1;
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 70px 8px 10px;
  border-radius: 8px;
  background: transparent;
  color: var(--discord-text-muted);
  text-align: left;
}

.channel-item:hover {
  color: var(--discord-text);
}

.channel-row.active .channel-item,
.channel-row:hover .channel-item {
  color: var(--discord-text);
}

.channel-row.connected .channel-item,
.channel-row.connected.active .channel-item,
.channel-row.connected:hover .channel-item {
  color: var(--discord-green);
}

.channel-actions {
  position: absolute;
  right: 6px;
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  pointer-events: none;
  transition: opacity 120ms ease;
}

.channel-row:hover .channel-actions,
.channel-row.active .channel-actions {
  opacity: 1;
  pointer-events: auto;
}

.channel-actions button {
  width: 28px;
  height: 28px;
  border-radius: 7px;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--discord-text-faint);
}

.channel-actions button:hover {
  background: var(--discord-hover-strong);
  color: var(--discord-text);
}

.channel-prefix {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  display: grid;
  place-items: center;
  color: inherit;
  font-size: 12px;
  font-weight: 700;
}

.channel-label {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voice-duration {
  flex-shrink: 0;
  min-width: 22px;
  color: var(--discord-green);
  font-size: 12px;
  font-weight: 900;
  text-align: right;
}

.voice-members {
  display: grid;
  gap: 2px;
  padding: 2px 8px 4px 38px;
}

.voice-member {
  --voice-level: 0.18;
  min-height: 28px;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: var(--discord-text-muted);
  font-size: 13px;
  border-radius: 7px;
  padding: 2px 4px;
  transition:
    background-color 120ms ease,
    color 120ms ease;
}

.voice-member.speaking {
  background: rgba(59, 165, 93, calc(0.08 + var(--voice-level) * 0.08));
  color: var(--discord-green);
}

.voice-member-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: var(--discord-brand);
  color: white;
  font-size: 11px;
  font-weight: 900;
  line-height: 1;
  transition:
    box-shadow 120ms ease,
    transform 120ms ease;
}

.voice-member.speaking .voice-member-avatar {
  box-shadow:
    0 0 0 2px rgba(59, 165, 93, 0.34),
    0 0 calc(8px + var(--voice-level) * 16px) rgba(59, 165, 93, 0.58);
  transform: scale(calc(1 + var(--voice-level) * 0.08));
}

.voice-member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.voice-member-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  font-size: 13px;
  font-weight: 800;
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
  background: var(--discord-subtle);
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
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 8px;
  padding: 8px;
  background: var(--discord-surface-soft);
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

.panel-action {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--discord-muted-surface);
  color: var(--discord-text-muted);
}

.panel-action:hover {
  background: var(--discord-pressed);
  color: var(--discord-text);
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 20px;
  background: var(--discord-overlay);
  backdrop-filter: blur(8px);
}

.modal-card {
  width: min(420px, 100%);
  border: 1px solid var(--discord-border);
  border-radius: 18px;
  background: var(--discord-surface);
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
  border: 1px solid var(--discord-border);
  border-radius: 10px;
  background: var(--discord-input);
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
  background: var(--discord-muted-surface);
  color: var(--discord-text);
}

.secondary-button,
.primary-button {
  min-width: 88px;
  padding: 10px 16px;
}

.secondary-button {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.secondary-button:hover {
  background: var(--discord-hover-strong);
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

@keyframes server-menu-in {
  from {
    opacity: 0;
    transform: translateY(-4px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
