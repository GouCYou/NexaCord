<template>
  <nav class="server-list" aria-label="服务器列表">
    <div class="server-entry" :class="{ active: !currentServerId }">
      <button
        class="server-button home-server"
        :class="{ active: !currentServerId }"
        type="button"
        aria-label="好友"
        @click="openHome"
      >
        <img class="server-image" src="/logo.png" alt="Nexacord" />
      </button>
      <span class="server-tooltip">好友</span>
    </div>

    <div class="server-divider"></div>

    <button
      class="server-button add-server"
      type="button"
      title="创建服务器"
      @click="showCreateServerModal = true"
    >
      <Plus :size="24" aria-hidden="true" />
    </button>

    <div
      v-for="server in validServers"
      :key="server.id"
      class="server-entry"
      :class="{ active: currentServerId === server.id }"
    >
      <button
        class="server-button"
        :class="{ active: currentServerId === server.id }"
        :aria-label="server.name"
        type="button"
        @click="selectServer(server.id)"
      >
        <img v-if="server.iconUrl" class="server-image" :src="server.iconUrl" :alt="server.name" />
        <ServerIcon v-else :size="22" aria-hidden="true" />
      </button>
      <span class="server-tooltip">{{ server.name }}</span>
    </div>

    <div v-if="showCreateServerModal" class="modal-overlay" @click="closeModal">
      <div class="modal-card" @click.stop>
        <header class="modal-header">
          <div>
            <h2>创建你的服务器</h2>
            <p>先建立一个空间，再添加频道、成员和实时聊天。</p>
          </div>
          <button class="icon-button" type="button" aria-label="关闭弹窗" @click="closeModal">
            <X :size="20" aria-hidden="true" />
          </button>
        </header>

        <div class="modal-body">
          <label class="field">
            <span>服务器名称</span>
            <input v-model="newServer.name" type="text" placeholder="例如：Nexacord 朋友服务器" />
          </label>

          <label class="field">
            <span>简介</span>
            <textarea
              v-model="newServer.description"
              rows="3"
              placeholder="简单介绍一下这个服务器的用途"
            ></textarea>
          </label>

          <p v-if="error" class="form-error">{{ error }}</p>
        </div>

        <footer class="modal-footer">
          <button class="secondary-button" type="button" @click="closeModal">取消</button>
          <button
            class="primary-button"
            type="button"
            :disabled="isLoading || !newServer.name.trim()"
            @click="createServer"
          >
            {{ isLoading ? '创建中……' : '创建服务器' }}
          </button>
        </footer>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useRouter } from 'vue-router';
import { Plus, Server as ServerIcon, X } from 'lucide-vue-next';
import { useChannelStore } from '../stores/channelStore';
import { useServerStore } from '../stores/serverStore';

const router = useRouter();
const serverStore = useServerStore();
const channelStore = useChannelStore();
const { servers, currentServerId, isLoading, error } = storeToRefs(serverStore);

const showCreateServerModal = ref(false);
const newServer = ref({
  name: '',
  description: '',
  iconUrl: null as string | null,
  bannerUrl: null as string | null,
});

const validServers = computed(() =>
  servers.value.filter((server) => typeof server.id === 'number' && !Number.isNaN(server.id))
);

const openCreateServerModal = () => {
  showCreateServerModal.value = true;
};

const openHome = () => {
  serverStore.setCurrentServer(null);
  channelStore.clearChannels();
  router.push('/');
};

const selectServer = (serverId: number) => {
  if (!serverId || Number.isNaN(serverId)) {
    return;
  }

  serverStore.setCurrentServer(serverId);
};

const closeModal = () => {
  showCreateServerModal.value = false;
  newServer.value = {
    name: '',
    description: '',
    iconUrl: null,
    bannerUrl: null,
  };
};

const createServer = async () => {
  if (!newServer.value.name.trim()) {
    return;
  }

  const success = await serverStore.createServer({
    name: newServer.value.name.trim(),
    description: newServer.value.description.trim(),
    iconUrl: newServer.value.iconUrl,
    bannerUrl: newServer.value.bannerUrl,
  });

  if (success) {
    closeModal();
  }
};

onMounted(() => {
  window.addEventListener('nexacord:create-server', openCreateServerModal);
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:create-server', openCreateServerModal);
});
</script>

<style scoped>
.server-list {
  width: 100%;
  height: 100%;
  padding: 12px 0 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  overflow: visible;
}

.server-entry {
  position: relative;
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
}

.server-entry::before {
  content: '';
  position: absolute;
  left: -16px;
  top: 50%;
  width: 0;
  height: 8px;
  border-radius: 0 999px 999px 0;
  background: var(--discord-text);
  transform: translateY(-50%);
  transition:
    width 140ms ease,
    height 140ms ease;
}

.server-entry:hover::before {
  width: 4px;
  height: 20px;
}

.server-entry.active::before {
  width: 4px;
  height: 40px;
}

.server-button {
  position: relative;
  width: 48px;
  height: 48px;
  padding: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-surface-soft);
  color: var(--discord-text);
  transition:
    border-radius 160ms ease,
    background-color 160ms ease,
    transform 160ms ease;
  overflow: hidden;
}

.server-button:hover {
  border-radius: 16px;
  background: var(--discord-brand);
  transform: translateY(-1px);
}

.server-button.active {
  border-radius: 16px;
  background: var(--discord-brand);
}

.add-server {
  color: #3bd274;
}

.add-server:hover {
  color: white;
  background: var(--discord-green);
}

.server-divider {
  width: 32px;
  height: 2px;
  border-radius: 999px;
  background: var(--discord-hover);
}

.server-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}

.server-tooltip {
  position: absolute;
  left: calc(100% + 12px);
  top: 50%;
  z-index: 50;
  max-width: 220px;
  padding: 9px 12px;
  border-radius: 8px;
  background: var(--discord-elevated);
  color: var(--discord-text);
  box-shadow: var(--discord-shadow);
  font-size: 14px;
  font-weight: 900;
  line-height: 1;
  opacity: 0;
  pointer-events: none;
  transform: translateY(-50%) translateX(-6px) scale(0.98);
  transition:
    opacity 120ms ease,
    transform 120ms ease;
  white-space: nowrap;
}

.server-tooltip::before {
  content: '';
  position: absolute;
  left: -6px;
  top: 50%;
  width: 12px;
  height: 12px;
  background: inherit;
  transform: translateY(-50%) rotate(45deg);
}

.server-entry:hover .server-tooltip,
.server-entry:focus-within .server-tooltip {
  opacity: 1;
  transform: translateY(-50%) translateX(0) scale(1);
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
  width: min(460px, 100%);
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
  gap: 16px;
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
  padding: 20px;
  display: grid;
  gap: 16px;
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
.field textarea {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--discord-border);
  border-radius: 10px;
  background: var(--discord-input);
  color: var(--discord-text);
  resize: vertical;
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
</style>
