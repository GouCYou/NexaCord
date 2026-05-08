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

    <div v-if="incomingCall" class="call-toast">
      <div>
        <strong>{{ displayUserLabel(incomingCall.caller) }}</strong>
        <span>邀请你语音通话</span>
      </div>
      <button class="call-accept" type="button" title="接听" @click="voiceStore.acceptIncomingCall">
        接听
      </button>
      <button class="call-decline" type="button" title="拒绝" @click="voiceStore.declineIncomingCall">
        拒绝
      </button>
    </div>

    <div v-else-if="outgoingCall" class="call-toast">
      <div>
        <strong>{{ displayUserLabel(outgoingCall.callee) }}</strong>
        <span>等待对方接听……</span>
      </div>
      <button class="call-decline" type="button" title="取消呼叫" @click="voiceStore.cancelOutgoingCall">
        取消
      </button>
    </div>
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
import { useDirectMessageStore } from '../stores/directMessageStore';
import { useServerStore } from '../stores/serverStore';
import { useUserStore } from '../stores/userStore';
import { useVoiceStore } from '../stores/voiceStore';
import websocketService from '../services/websocketService';
import { displayUserLabel } from '../utils/userDisplay';

const route = useRoute();
const router = useRouter();
const serverStore = useServerStore();
const channelStore = useChannelStore();
const userStore = useUserStore();
const directMessageStore = useDirectMessageStore();
const voiceStore = useVoiceStore();

const { servers, currentServerId, currentServer, isLoading } = storeToRefs(serverStore);
const { channels } = storeToRefs(channelStore);
const { currentUser, isAuthenticated } = storeToRefs(userStore);
const { incomingCall, outgoingCall } = storeToRefs(voiceStore);
const showMemberSidebar = ref(true);
let routeSyncVersion = 0;

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

const isHomeRoute = computed(() => route.name === 'Friends' || route.name === 'DirectConversation');
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

const getRouteServerId = () => parseRouteId(route.params.serverId);
const getRouteChannelId = () => parseRouteId(route.params.channelId);

const syncRouteState = async () => {
  const syncVersion = ++routeSyncVersion;

  if (!isAuthenticated.value) {
    return;
  }

  if (isHomeRoute.value) {
    serverStore.setCurrentServer(null);
    channelStore.clearChannels();
    return;
  }

  const routeServerId = getRouteServerId();
  if (!routeServerId) {
    serverStore.setCurrentServer(null);
    channelStore.clearChannels();
    if (route.path !== '/') {
      router.replace('/');
    }
    return;
  }

  if (!servers.value.some((server) => server.id === routeServerId)) {
    await serverStore.fetchServers(routeServerId);
    if (syncVersion !== routeSyncVersion) {
      return;
    }
  }

  if (!servers.value.some((server) => server.id === routeServerId)) {
    serverStore.setCurrentServer(null);
    channelStore.clearChannels();
    if (route.path !== '/') {
      router.replace('/');
    }
    return;
  }

  const isSwitchingServer = currentServerId.value !== routeServerId;
  serverStore.setCurrentServer(routeServerId);
  if (isSwitchingServer) {
    channelStore.clearChannels();
  }

  const routeChannelId = getRouteChannelId();
  const loadedChannels = await channelStore.fetchChannels(routeServerId, routeChannelId ?? undefined);
  if (syncVersion !== routeSyncVersion) {
    return;
  }

  const matchedRouteChannel = routeChannelId
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
    return;
  }

  channelStore.setCurrentChannel(firstVisibleChannel.id);
  const nextPath = `/servers/${routeServerId}/channels/${firstVisibleChannel.id}`;
  if (route.path !== nextPath) {
    router.replace(nextPath);
  }
};

const bootstrapWorkspace = async () => {
  if (!isAuthenticated.value) {
    return;
  }

  await userStore.refreshCurrentUser();
  directMessageStore.initializeRealtime();
  voiceStore.initializeRealtime();
  await serverStore.fetchServers(getRouteServerId() ?? undefined);
  await syncRouteState();
};

const handleServerRealtimeUpdate = (_event: unknown, payload: unknown) => {
  const update = payload as { userId?: number };
  if (update?.userId && update.userId !== currentUser.value?.id) {
    return;
  }

  void serverStore.fetchServers(getRouteServerId() ?? undefined);
};

onMounted(() => {
  window.addEventListener('nexacord:toggle-member-sidebar', toggleMemberSidebar);
  websocketService.on('server:update', handleServerRealtimeUpdate);
  void bootstrapWorkspace();
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:toggle-member-sidebar', toggleMemberSidebar);
  websocketService.off('server:update', handleServerRealtimeUpdate);
});

watch(
  () => [route.name, route.params.serverId, route.params.channelId],
  () => {
    void syncRouteState();
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

.call-toast {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 70;
  min-width: 320px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--discord-border);
  border-radius: 12px;
  background: var(--discord-elevated);
  box-shadow: var(--discord-shadow);
}

.call-toast div {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.call-toast strong,
.call-toast span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.call-toast span {
  color: var(--discord-text-faint);
  font-size: 13px;
}

.call-toast button {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 8px;
  color: white;
  font-weight: 900;
}

.call-accept {
  background: var(--discord-green);
}

.call-decline {
  background: var(--discord-red);
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
