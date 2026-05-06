<template>
  <div class="layout">
    <aside class="servers-pane">
      <ServerList />
    </aside>

    <aside class="channels-pane">
      <FriendsSidebar v-if="isHomeRoute" />
      <ChannelList v-else />
    </aside>

    <main class="content-pane">
      <div v-if="showLandingState" class="empty-state">
        <span class="empty-state-kicker">Nexacord</span>
        <h1>创建你的第一个服务器</h1>
        <p>
          先建立服务器，再按主题整理频道，在文字和语音空间之间切换，整体布局和交互尽量保持接近现代社区工具。
        </p>
        <div class="empty-state-actions">
          <button class="primary-action" type="button" @click="dispatchCreateServer">
            创建服务器
          </button>
        </div>
      </div>

      <div v-else-if="showServerSetupState" class="empty-state server-empty-state">
        <span class="empty-state-kicker">{{ currentServer?.name }}</span>
        <h1>这个服务器已经准备好创建第一个频道</h1>
        <p>
          可以先创建一个文字频道用于日常讨论，或者建立语音频道做实时交流。创建完成后，左侧栏和消息区会自动更新。
        </p>
        <div class="empty-state-actions">
          <button class="primary-action" type="button" @click="dispatchCreateChannel">
            创建频道
          </button>
        </div>
      </div>

      <div v-else class="channel-workspace" :class="{ 'with-members': shouldShowMemberSidebar }">
        <router-view />
        <MemberSidebar v-if="shouldShowMemberSidebar" :server-id="currentServerId" />
      </div>
    </main>

    <ProfileModal />
    <PasswordResetModal />
    <ServerInviteModal />
    <ServerSettingsModal />
    <UserProfilePopover />
    <VoiceAudioSink />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import ServerList from '../components/ServerList.vue';
import ChannelList from '../components/ChannelList.vue';
import FriendsSidebar from '../components/FriendsSidebar.vue';
import ProfileModal from '../components/ProfileModal.vue';
import PasswordResetModal from '../components/PasswordResetModal.vue';
import ServerInviteModal from '../components/ServerInviteModal.vue';
import ServerSettingsModal from '../components/ServerSettingsModal.vue';
import UserProfilePopover from '../components/UserProfilePopover.vue';
import VoiceAudioSink from '../components/VoiceAudioSink.vue';
import MemberSidebar from '../components/MemberSidebar.vue';
import { useChannelStore } from '../stores/channelStore';
import { useServerStore } from '../stores/serverStore';
import { useUserStore } from '../stores/userStore';

const route = useRoute();
const router = useRouter();
const serverStore = useServerStore();
const channelStore = useChannelStore();
const userStore = useUserStore();

const { servers, currentServerId, currentServer, isLoading } = storeToRefs(serverStore);
const { channels } = storeToRefs(channelStore);
const { isAuthenticated } = storeToRefs(userStore);
const showMemberSidebar = ref(true);

const parseRouteId = (value: unknown): number | null => {
  if (typeof value !== 'string') {
    return null;
  }

  const parsedValue = Number.parseInt(value, 10);
  return Number.isNaN(parsedValue) ? null : parsedValue;
};

const showLandingState = computed(
  () => !isHomeRoute.value && !currentServer.value && !isLoading.value && servers.value.length === 0
);

const showServerSetupState = computed(
  () =>
    !isHomeRoute.value &&
    Boolean(currentServer.value) &&
    !isLoading.value &&
    channels.value.length === 0
);

const isHomeRoute = computed(() => route.name === 'Friends');
const shouldShowMemberSidebar = computed(
  () => !isHomeRoute.value && Boolean(currentServerId.value) && showMemberSidebar.value
);

const dispatchCreateServer = () => {
  window.dispatchEvent(new CustomEvent('nexacord:create-server'));
};

const dispatchCreateChannel = () => {
  window.dispatchEvent(new CustomEvent('nexacord:create-channel'));
};

const toggleMemberSidebar = () => {
  showMemberSidebar.value = !showMemberSidebar.value;
};

onMounted(async () => {
  window.addEventListener('nexacord:toggle-member-sidebar', toggleMemberSidebar);

  if (!isAuthenticated.value) {
    return;
  }

  const routeServerId = parseRouteId(route.params.serverId);
  await serverStore.fetchServers(routeServerId ?? undefined);
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:toggle-member-sidebar', toggleMemberSidebar);
});

watch(
  () => route.params.serverId,
  (serverIdParam) => {
    const routeServerId = parseRouteId(serverIdParam);
    if (routeServerId && routeServerId !== currentServerId.value) {
      serverStore.setCurrentServer(routeServerId);
    }
  },
  { immediate: true }
);

watch(
  currentServerId,
  async (serverId) => {
    if (!serverId) {
      channelStore.clearChannels();
      if (route.path !== '/') {
        router.replace('/');
      }
      return;
    }

    const routeServerId = parseRouteId(route.params.serverId);
    const routeChannelId = parseRouteId(route.params.channelId);

    const loadedChannels = await channelStore.fetchChannels(
      serverId,
      routeServerId === serverId ? routeChannelId ?? undefined : undefined
    );

    const matchedRouteChannel =
      routeServerId === serverId && routeChannelId
        ? loadedChannels.find((channel) => channel.id === routeChannelId)
        : null;

    if (matchedRouteChannel) {
      channelStore.setCurrentChannel(matchedRouteChannel.id);
      return;
    }

    const firstVisibleChannel =
      loadedChannels.find((channel) => channel.type === 'TEXT') || loadedChannels[0];

    if (!firstVisibleChannel) {
      channelStore.setCurrentChannel(null);
      if (route.path !== '/') {
        router.replace('/');
      }
      return;
    }

    channelStore.setCurrentChannel(firstVisibleChannel.id);
    router.replace(`/servers/${serverId}/channels/${firstVisibleChannel.id}`);
  }
);
</script>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 72px 280px 1fr;
  width: 100%;
  height: 100vh;
  background: var(--discord-bg);
}

.servers-pane {
  background: var(--discord-rail);
  border-right: 1px solid var(--discord-border);
}

.channels-pane {
  background: var(--discord-surface);
  border-right: 1px solid var(--discord-border);
}

.content-pane {
  min-width: 0;
  min-height: 0;
  background:
    radial-gradient(circle at top left, rgba(88, 101, 242, 0.12), transparent 24%),
    var(--discord-bg);
}

.channel-workspace {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
}

.channel-workspace.with-members {
  grid-template-columns: minmax(0, 1fr) 280px;
}

.empty-state {
  height: 100%;
  display: grid;
  place-content: center;
  gap: 14px;
  padding: 48px;
  text-align: center;
}

.empty-state-kicker {
  color: var(--discord-brand);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.empty-state h1 {
  margin: 0;
  font-size: clamp(30px, 5vw, 48px);
  line-height: 1.05;
}

.empty-state p {
  max-width: 640px;
  margin: 0 auto;
  color: var(--discord-text-muted);
  font-size: 16px;
}

.empty-state-actions {
  display: flex;
  justify-content: center;
  margin-top: 8px;
}

.primary-action {
  min-width: 180px;
  padding: 14px 22px;
  border-radius: 14px;
  background: var(--discord-brand);
  color: white;
  font-weight: 800;
}

.primary-action:hover {
  background: var(--discord-brand-hover);
}

.server-empty-state .empty-state-kicker {
  color: #98a1ff;
}

@media (max-width: 960px) {
  .layout {
    grid-template-columns: 72px 220px 1fr;
  }
}

@media (max-width: 760px) {
  .layout {
    grid-template-columns: 64px 200px 1fr;
  }
}
</style>
