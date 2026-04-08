<template>
  <nav class="server-list" aria-label="服务器列表">
    <button
      class="server-button add-server"
      type="button"
      title="创建服务器"
      @click="showCreateServerModal = true"
    >
      <span class="server-glyph">+</span>
    </button>

    <div class="server-divider"></div>

    <button
      v-for="server in validServers"
      :key="server.id"
      class="server-button"
      :class="{ active: currentServerId === server.id }"
      :title="server.name"
      type="button"
      @click="selectServer(server.id)"
    >
      <img v-if="server.iconUrl" class="server-image" :src="server.iconUrl" :alt="server.name" />
      <span v-else class="server-glyph">{{ server.name.charAt(0).toUpperCase() }}</span>
    </button>

    <p v-if="validServers.length === 0" class="server-hint">创建你的第一个服务器</p>

    <div v-if="showCreateServerModal" class="modal-overlay" @click="closeModal">
      <div class="modal-card" @click.stop>
        <header class="modal-header">
          <div>
            <h2>创建你的服务器</h2>
            <p>先建立一个空间，再添加频道、成员和实时聊天。</p>
          </div>
          <button class="icon-button" type="button" @click="closeModal">x</button>
        </header>

        <div class="modal-body">
          <label class="field">
            <span>服务器名称</span>
            <input v-model="newServer.name" type="text" placeholder="例如：NexaCord 社区" />
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
            {{ isLoading ? '创建中...' : '创建服务器' }}
          </button>
        </footer>
      </div>
    </div>
  </nav>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useServerStore } from '../stores/serverStore';

const serverStore = useServerStore();
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
  overflow-y: auto;
}

.server-button {
  position: relative;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #232428;
  color: var(--discord-text);
  transition:
    border-radius 160ms ease,
    background-color 160ms ease,
    transform 160ms ease;
}

.server-button::before {
  content: '';
  position: absolute;
  left: -14px;
  width: 0;
  height: 20px;
  border-radius: 999px;
  background: white;
  transition:
    width 140ms ease,
    height 140ms ease;
}

.server-button:hover {
  border-radius: 16px;
  background: var(--discord-brand);
  transform: translateY(-1px);
}

.server-button:hover::before {
  width: 4px;
  height: 20px;
}

.server-button.active {
  border-radius: 16px;
  background: var(--discord-brand);
}

.server-button.active::before {
  width: 4px;
  height: 40px;
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
  background: rgba(255, 255, 255, 0.08);
}

.server-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: inherit;
}

.server-glyph {
  font-size: 18px;
  font-weight: 800;
}

.server-hint {
  width: 52px;
  margin: 8px 0 0;
  color: var(--discord-text-faint);
  font-size: 11px;
  line-height: 1.35;
  text-align: center;
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
  width: min(460px, 100%);
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
  border: 1px solid rgba(0, 0, 0, 0.32);
  border-radius: 10px;
  background: #1e1f22;
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
