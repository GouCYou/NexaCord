<template>
  <div v-if="currentUser" class="control-stack" @click.stop>
    <section v-if="isJoined" class="voice-connection">
      <div class="voice-connection-status">
        <RadioTower :size="22" aria-hidden="true" />
        <span>
          <strong>语音已连接</strong>
          <small>{{ activeChannelName }}{{ activeServerName ? ` / ${activeServerName}` : '' }}</small>
        </span>
      </div>

      <div class="voice-connection-actions">
        <button class="voice-action" type="button" :title="muteTitle" @click="toggleMute">
          <MicOff v-if="isMuted" :size="18" aria-hidden="true" />
          <Mic v-else :size="18" aria-hidden="true" />
        </button>
        <button class="voice-action" type="button" :title="deafenTitle" @click="toggleDeafen">
          <VolumeX v-if="isDeafened" :size="18" aria-hidden="true" />
          <Headphones v-else :size="18" aria-hidden="true" />
        </button>
        <button class="voice-action" type="button" title="音量控制" @click="toggleVoiceMixer">
          <SlidersHorizontal :size="18" aria-hidden="true" />
        </button>
        <button class="voice-action danger" type="button" title="断开语音" @click="disconnectVoice">
          <PhoneOff :size="18" aria-hidden="true" />
        </button>
      </div>

      <div v-if="showVoiceMixer" class="voice-volume-popover" @click.stop>
        <label>
          <span>麦克风 {{ inputVolume }}%</span>
          <input class="volume-range" type="range" min="0" max="200" :value="inputVolume" @input="setInputVolumeFromEvent" />
        </label>
        <label>
          <span>扬声器 {{ outputVolume }}%</span>
          <input class="volume-range" type="range" min="0" max="200" :value="outputVolume" @input="setOutputVolumeFromEvent" />
        </label>
      </div>
    </section>

    <footer class="user-control">
      <button class="user-card" type="button" title="切换状态" @click="toggleStatusMenu">
        <div class="user-avatar">
          <img :src="currentUser.avatarUrl || defaultAvatarUrl" :alt="displayName" />
          <i :class="['status-dot', currentUser.status || 'online']"></i>
        </div>

        <div class="user-meta">
          <strong>{{ displayName }}</strong>
          <span>{{ statusLabel }}</span>
        </div>
      </button>

      <div v-if="showStatusMenu" class="status-menu">
        <button
          v-for="option in statusOptions"
          :key="option.value"
          class="status-option"
          type="button"
          :class="{ active: currentUser.status === option.value }"
          @click="setStatus(option.value)"
        >
          <i :class="['status-dot', option.value]"></i>
          <span>
            <strong>{{ option.label }}</strong>
            <small v-if="option.description">{{ option.description }}</small>
          </span>
          <ChevronRight v-if="currentUser.status === option.value" :size="18" aria-hidden="true" />
        </button>
      </div>

      <div v-if="showAccountMenu" class="account-menu">
        <button type="button" @click="openProfile">
          <Pencil :size="18" aria-hidden="true" />
          <span>编辑个人资料</span>
        </button>
        <button type="button" @click="openPasswordReset">
          <KeyRound :size="18" aria-hidden="true" />
          <span>重置密码</span>
        </button>
        <button v-if="showLogout" type="button" @click="switchAccount">
          <LogOut :size="18" aria-hidden="true" />
          <span>切换账号</span>
        </button>
      </div>

      <button class="panel-action" type="button" title="账号设置" @click="toggleAccountMenu">
        <Settings :size="18" aria-hidden="true" />
      </button>
      <button class="panel-action" type="button" :title="themeTitle" @click="themeStore.toggleTheme">
        <Moon v-if="isLight" :size="18" aria-hidden="true" />
        <Sun v-else :size="18" aria-hidden="true" />
      </button>
      <button v-if="showLogout" class="panel-action" type="button" title="退出登录" @click="$emit('logout')">
        <LogOut :size="18" aria-hidden="true" />
      </button>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { storeToRefs } from 'pinia';
import {
  ChevronRight,
  Headphones,
  KeyRound,
  LogOut,
  Mic,
  MicOff,
  Moon,
  Pencil,
  PhoneOff,
  RadioTower,
  Settings,
  SlidersHorizontal,
  Sun,
  VolumeX,
} from 'lucide-vue-next';
import { useThemeStore } from '../stores/themeStore';
import { useUserStore } from '../stores/userStore';
import { useVoiceStore } from '../stores/voiceStore';
import type { User } from '../types';
import { compactUserLabel } from '../utils/userDisplay';

defineProps<{
  showLogout?: boolean;
}>();

const emit = defineEmits<{
  logout: [];
}>();

const themeStore = useThemeStore();
const userStore = useUserStore();
const voiceStore = useVoiceStore();
const { currentUser } = storeToRefs(userStore);
const { isLight } = storeToRefs(themeStore);
const { activeChannelName, activeServerName, isJoined, isMuted, isDeafened, inputVolume, outputVolume } = storeToRefs(voiceStore);
const { leaveChannel, toggleMute, toggleDeafen, setInputVolume, setOutputVolume } = voiceStore;
const showStatusMenu = ref(false);
const showAccountMenu = ref(false);
const showVoiceMixer = ref(false);
const defaultAvatarUrl = '/logo.png';

const statusOptions: Array<{
  value: User['status'];
  label: string;
  description?: string;
}> = [
  { value: 'online', label: '在线' },
  { value: 'away', label: '闲置' },
  { value: 'dnd', label: '请勿打扰', description: '您将不会收到桌面通知' },
  { value: 'offline', label: '隐身', description: '您将显示为离线' },
];

const statusLabel = computed(() => {
  const status = currentUser.value?.status || 'online';
  return statusOptions.find((option) => option.value === status)?.label || '在线';
});

const themeTitle = computed(() => (isLight.value ? '切换为深色模式' : '切换为浅色模式'));
const muteTitle = computed(() => (isMuted.value ? '打开麦克风' : '关闭麦克风'));
const deafenTitle = computed(() => (isDeafened.value ? '恢复收听' : '拒听远端声音'));
const displayName = computed(() => compactUserLabel(currentUser.value));

const toggleStatusMenu = () => {
  showStatusMenu.value = !showStatusMenu.value;
  showAccountMenu.value = false;
};

const toggleAccountMenu = () => {
  showAccountMenu.value = !showAccountMenu.value;
  showStatusMenu.value = false;
  showVoiceMixer.value = false;
};

const toggleVoiceMixer = () => {
  showVoiceMixer.value = !showVoiceMixer.value;
  showStatusMenu.value = false;
  showAccountMenu.value = false;
};

const setStatus = async (status: User['status']) => {
  await userStore.updateProfile({ status });
  showStatusMenu.value = false;
};

const openProfile = () => {
  showAccountMenu.value = false;
  window.dispatchEvent(new CustomEvent('nexacord:open-profile'));
};

const openPasswordReset = () => {
  showAccountMenu.value = false;
  window.dispatchEvent(new CustomEvent('nexacord:open-password-reset'));
};

const switchAccount = () => {
  showAccountMenu.value = false;
  emit('logout');
};

const closeMenus = () => {
  showStatusMenu.value = false;
  showAccountMenu.value = false;
  showVoiceMixer.value = false;
};

const setInputVolumeFromEvent = (event: Event) => {
  setInputVolume(Number((event.target as HTMLInputElement).value));
};

const setOutputVolumeFromEvent = (event: Event) => {
  setOutputVolume(Number((event.target as HTMLInputElement).value));
};

const disconnectVoice = () => {
  leaveChannel();
};

onMounted(() => {
  window.addEventListener('click', closeMenus);
});

onBeforeUnmount(() => {
  window.removeEventListener('click', closeMenus);
});
</script>

<style scoped>
.control-stack {
  position: relative;
  display: grid;
  border-top: 1px solid var(--discord-border);
  background: var(--discord-surface-soft);
}

.voice-connection {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 10px 8px;
  border-bottom: 1px solid var(--discord-border);
  background: color-mix(in srgb, var(--discord-green) 10%, var(--discord-surface-soft));
}

.voice-connection-status {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: var(--discord-green);
}

.voice-connection-status > svg {
  width: 32px;
  height: 32px;
  padding: 6px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--discord-green) 14%, transparent);
}

.voice-connection-status span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.voice-connection-status strong,
.voice-connection-status small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.voice-connection-status strong {
  font-size: 14px;
}

.voice-connection-status small {
  color: var(--discord-text-muted);
  font-size: 12px;
}

.voice-connection-actions {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.voice-volume-popover {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: calc(100% + 10px);
  z-index: 35;
  display: grid;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--discord-strong-border);
  border-radius: 14px;
  background: color-mix(in srgb, var(--discord-elevated) 94%, transparent);
  box-shadow: var(--discord-shadow);
  backdrop-filter: blur(18px);
}

.voice-volume-popover::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -6px;
  width: 12px;
  height: 12px;
  background: inherit;
  border-right: 1px solid var(--discord-strong-border);
  border-bottom: 1px solid var(--discord-strong-border);
  transform: translateX(-50%) rotate(45deg);
}

.voice-volume-popover label {
  position: relative;
  display: grid;
  gap: 8px;
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.volume-range {
  width: 100%;
  accent-color: var(--discord-text-muted);
}

.voice-action {
  min-height: 34px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--discord-muted-surface);
  color: var(--discord-text);
}

.voice-action:hover {
  background: var(--discord-hover-strong);
}

.voice-action.danger:hover {
  background: var(--discord-red);
  color: white;
}

.user-control {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 6px;
  padding: 8px;
  background: transparent;
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

.user-card:hover,
.panel-action:hover {
  background: var(--discord-hover);
}

.user-avatar {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.user-avatar span {
  line-height: 1;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--discord-green);
}

.user-avatar .status-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  border: 2px solid var(--discord-surface-soft);
  box-sizing: content-box;
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

.panel-action {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--discord-muted-surface);
  color: var(--discord-text-muted);
}

.status-menu,
.account-menu {
  position: absolute;
  left: 8px;
  right: 8px;
  bottom: calc(100% + 8px);
  z-index: 30;
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid var(--discord-border);
  border-radius: 12px;
  background: var(--discord-elevated);
  box-shadow: var(--discord-shadow);
  animation: menu-in 120ms ease-out;
}

.account-menu {
  gap: 2px;
}

.account-menu button {
  min-height: 42px;
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: transparent;
  color: var(--discord-text);
  font-weight: 900;
  text-align: left;
}

.account-menu button:hover {
  background: var(--discord-hover);
}

.status-option {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 52px;
  padding: 8px 10px;
  border-radius: 8px;
  background: transparent;
  color: var(--discord-text);
  text-align: left;
}

.status-option:hover,
.status-option.active {
  background: var(--discord-hover);
}

.status-option span {
  display: grid;
  gap: 2px;
}

.status-option small {
  color: var(--discord-text-faint);
  font-size: 12px;
}

@keyframes menu-in {
  from {
    opacity: 0;
    transform: translateY(6px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
