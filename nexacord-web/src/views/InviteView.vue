<template>
  <main class="invite-page">
    <section class="invite-card">
      <div class="server-avatar">
        <img v-if="invite?.serverIconUrl" :src="invite.serverIconUrl" :alt="invite.serverName" />
        <ServerIcon v-else :size="34" aria-hidden="true" />
      </div>

      <p class="eyebrow">Nexacord 邀请</p>
      <h1>{{ invite?.serverName || '正在加载邀请' }}</h1>
      <p class="copy">
        {{ statusCopy }}
      </p>

      <button class="join-button" type="button" :disabled="isLoading || !invite || isAlreadyMember" @click="joinServer">
        {{ joinButtonLabel }}
      </button>

      <router-link class="back-link" to="/">返回 Nexacord</router-link>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Server as ServerIcon } from 'lucide-vue-next';
import serverService from '../services/serverService';
import { useChannelStore } from '../stores/channelStore';
import { useServerStore } from '../stores/serverStore';
import type { ServerInvite } from '../types';

const route = useRoute();
const router = useRouter();
const serverStore = useServerStore();
const channelStore = useChannelStore();

const invite = ref<ServerInvite | null>(null);
const isLoading = ref(false);
const error = ref('');

const inviteCode = computed(() => String(route.params.code || ''));
const isAlreadyMember = computed(() => Boolean(invite.value?.member));
const targetChannelLabel = computed(() => {
  if (!invite.value?.channelId || !invite.value.channelName) {
    return '';
  }

  return `${invite.value.channelType === 'VOICE' ? '语音频道' : '#'} ${invite.value.channelName}`;
});
const joinButtonLabel = computed(() => {
  if (isLoading.value) {
    return '处理中……';
  }

  if (isAlreadyMember.value) {
    return invite.value?.channelId ? '已是该频道成员' : '已是服务器成员';
  }

  return '接受邀请';
});
const statusCopy = computed(() => {
  if (error.value) {
    return error.value;
  }

  if (!invite.value) {
    return '正在确认这条邀请链接是否仍然有效。';
  }

  if (isAlreadyMember.value) {
    return invite.value.channelId
      ? `你已经是 ${invite.value.serverName} 中 ${targetChannelLabel.value} 的成员。`
      : `你已经是 ${invite.value.serverName} 的服务器成员。`;
  }

  if (targetChannelLabel.value) {
    return `你将加入 ${invite.value.serverName}，并进入 ${targetChannelLabel.value}。`;
  }

  return `你将加入 ${invite.value.serverName}，和服务器成员一起聊天、语音和分享文件。`;
});

const loadInvite = async () => {
  isLoading.value = true;
  error.value = '';

  try {
    invite.value = await serverService.getInvite(inviteCode.value);
  } catch (err: any) {
    error.value =
      err.response?.data?.error ||
      err.response?.data?.message ||
      '邀请链接不存在或已经失效。';
  } finally {
    isLoading.value = false;
  }
};

const joinServer = async () => {
  if (!invite.value || isAlreadyMember.value) {
    return;
  }

  isLoading.value = true;
  error.value = '';

  try {
    const server = await serverService.joinInvite(invite.value.code);
    await serverStore.fetchServers(server.id);
    serverStore.setCurrentServer(server.id);
    if (invite.value.channelId) {
      await channelStore.fetchChannels(server.id, invite.value.channelId);
      channelStore.setCurrentChannel(invite.value.channelId);
      router.push(`/servers/${server.id}/channels/${invite.value.channelId}`);
      return;
    }

    router.push(`/servers/${server.id}`);
  } catch (err: any) {
    error.value =
      err.response?.data?.error ||
      err.response?.data?.message ||
      '加入服务器失败，请稍后再试。';
  } finally {
    isLoading.value = false;
  }
};

onMounted(loadInvite);
</script>

<style scoped>
.invite-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  background:
    radial-gradient(circle at top, var(--discord-body-glow), transparent 34%),
    var(--discord-bg);
}

.invite-card {
  width: min(420px, 100%);
  display: grid;
  justify-items: center;
  gap: 14px;
  padding: 36px;
  border: 1px solid var(--discord-border);
  border-radius: 18px;
  background: var(--discord-elevated);
  box-shadow: var(--discord-shadow);
  text-align: center;
}

.server-avatar {
  width: 88px;
  height: 88px;
  border-radius: 28px;
  display: grid;
  place-items: center;
  background: #f2f3f5;
  color: #1e1f22;
  font-size: 30px;
  font-weight: 900;
  overflow: hidden;
}

.server-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.eyebrow {
  margin: 8px 0 0;
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: 28px;
}

.copy {
  margin: 0;
  color: var(--discord-text-muted);
  line-height: 1.5;
}

.join-button {
  width: 100%;
  min-height: 46px;
  margin-top: 10px;
  border-radius: 10px;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
}

.join-button:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

.join-button:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.back-link {
  color: var(--discord-text-muted);
  font-size: 14px;
  font-weight: 800;
}
</style>
