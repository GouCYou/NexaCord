<template>
  <section class="voice-panel">
    <div class="voice-hero">
      <div class="voice-copy">
        <span class="voice-kicker">
          <Volume2 :size="16" aria-hidden="true" />
          语音频道
        </span>
        <h2>{{ channelName }}</h2>
        <p>{{ localStatusText }}</p>
      </div>

      <div class="voice-actions">
        <button
          v-if="!isCurrentVoiceChannel"
          class="call-button join"
          type="button"
          :disabled="isConnecting"
          @click="joinVoice"
        >
          <PhoneCall :size="18" aria-hidden="true" />
          <span>{{ isConnecting ? '连接中……' : '加入语音' }}</span>
        </button>
        <button v-else class="call-button leave" type="button" @click="leaveChannel">
          <PhoneOff :size="18" aria-hidden="true" />
          <span>断开连接</span>
        </button>
        <button class="round-button" type="button" :disabled="!isCurrentVoiceChannel" :title="muteTitle" @click="toggleMute">
          <MicOff v-if="isMuted" :size="20" aria-hidden="true" />
          <Mic v-else :size="20" aria-hidden="true" />
        </button>
        <button class="round-button" type="button" :disabled="!isCurrentVoiceChannel" :title="deafenTitle" @click="toggleDeafen">
          <VolumeX v-if="isDeafened" :size="20" aria-hidden="true" />
          <Headphones v-else :size="20" aria-hidden="true" />
        </button>
      </div>

      <p v-if="voiceError" class="voice-error">{{ voiceError }}</p>
    </div>

    <div class="voice-toolbar">
      <span>
        <Users :size="16" aria-hidden="true" />
        {{ displayedParticipants.length }} 人在频道中
      </span>
      <span>{{ isCurrentVoiceChannel ? selfVoiceStatus : '你可以先加入这个语音频道' }}</span>
    </div>

    <div class="voice-grid">
      <article
        v-for="participant in displayedParticipants"
        :key="participant.id"
        class="voice-tile"
        :class="{ self: participant.id === currentUser?.id, speaking: isUserSpeaking(participant.id) }"
        :style="voiceLevelStyle(participant.id)"
        @click="openUserPopover(participant, $event)"
      >
        <div class="voice-avatar">
          <img :src="participant.avatarUrl || defaultAvatarUrl" :alt="displayNameOf(participant)" />
        </div>
        <strong>{{ displayNameOf(participant) }}</strong>
        <span>{{ participantVoiceStatus(participant) }}</span>
      </article>

      <div v-if="displayedParticipants.length === 0" class="voice-empty">
        <Volume2 :size="42" aria-hidden="true" />
        <strong>还没有人加入语音</strong>
        <p>加入后，这里会显示同频道成员和通话状态。</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { storeToRefs } from 'pinia';
import { Headphones, Mic, MicOff, PhoneCall, PhoneOff, Users, Volume2, VolumeX } from 'lucide-vue-next';
import { useServerStore } from '../stores/serverStore';
import { useUserStore } from '../stores/userStore';
import { useVoiceStore, type VoiceUser } from '../stores/voiceStore';

const props = defineProps<{
  channelId: number;
  channelName: string;
}>();

const userStore = useUserStore();
const serverStore = useServerStore();
const voiceStore = useVoiceStore();

const { currentUser } = storeToRefs(userStore);
const { currentServer, currentServerId } = storeToRefs(serverStore);
const {
  activeChannelId,
  isConnecting,
  isMuted,
  isDeafened,
  voiceError,
  visibleParticipants,
  selfVoiceStatus,
  statusText,
} = storeToRefs(voiceStore);
const {
  displayNameOf,
  getVoiceLevel,
  isUserSpeaking,
  joinChannel,
  leaveChannel,
  toggleMute,
  toggleDeafen,
} = voiceStore;
const defaultAvatarUrl = '/logo.png';

const isCurrentVoiceChannel = computed(() => activeChannelId.value === props.channelId);

const displayedParticipants = computed(() =>
  isCurrentVoiceChannel.value ? visibleParticipants.value : []
);

const localStatusText = computed(() => {
  if (isCurrentVoiceChannel.value) {
    return statusText.value;
  }

  return '加入后即可和同频道成员实时语音通话，切换频道也会保持连接。';
});

const muteTitle = computed(() => (isMuted.value ? '打开麦克风' : '关闭麦克风'));
const deafenTitle = computed(() => (isDeafened.value ? '恢复收听' : '拒听远端声音'));

const voiceLevelStyle = (userId: number) => ({
  '--voice-level': Math.max(0.18, getVoiceLevel(userId)).toFixed(2),
});

const participantVoiceStatus = (participant: VoiceUser) => {
  if (isUserSpeaking(participant.id)) {
    return '正在说话';
  }

  return participant.id === currentUser.value?.id ? selfVoiceStatus.value : '已连接';
};

const joinVoice = () => {
  joinChannel({
    channelId: props.channelId,
    channelName: props.channelName,
    serverId: currentServerId.value,
    serverName: currentServer.value?.name,
  });
};

const openUserPopover = (user: VoiceUser, event: MouseEvent) => {
  window.dispatchEvent(new CustomEvent('nexacord:open-user-popover', {
    detail: {
      user,
      serverName: currentServer.value?.name,
      x: event.clientX,
      y: event.clientY,
    },
  }));
};
</script>

<style scoped>
.voice-panel {
  min-height: 0;
  height: 100%;
  overflow-y: auto;
  display: grid;
  grid-template-rows: auto auto 1fr;
  gap: 16px;
  padding: 22px;
  background: var(--discord-elevated);
}

.voice-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 92px;
  padding: 18px 20px;
  border: 1px solid var(--discord-border);
  border-radius: 12px;
  background: linear-gradient(135deg, var(--discord-bg), var(--discord-surface-soft));
}

.voice-copy {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.voice-kicker {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #3ba55d;
  font-size: 12px;
  font-weight: 900;
}

.voice-hero h2,
.voice-hero p {
  margin: 0;
}

.voice-hero h2 {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 26px;
}

.voice-hero p {
  color: var(--discord-text-muted);
  font-size: 14px;
}

.voice-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.call-button {
  min-height: 42px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  border-radius: 9px;
  color: white;
  font-weight: 900;
}

.call-button.join {
  background: var(--discord-green);
}

.call-button.leave {
  background: var(--discord-red);
}

.round-button {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-surface-soft);
  color: var(--discord-text);
}

.round-button:hover:not(:disabled) {
  background: var(--discord-hover-strong);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

.voice-error {
  color: #ff8b8d;
  font-size: 13px;
}

.voice-toolbar {
  min-height: 38px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 4px;
  color: var(--discord-text-faint);
  font-size: 13px;
}

.voice-toolbar span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.voice-grid {
  min-height: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  align-content: start;
  gap: 14px;
}

.voice-tile {
  --voice-level: 0.18;
  min-height: 168px;
  position: relative;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  background: var(--discord-surface);
  cursor: pointer;
  transition:
    border-color 120ms ease,
    background-color 120ms ease,
    box-shadow 120ms ease,
    transform 120ms ease;
}

.voice-tile.self {
  border-color: rgba(59, 165, 93, 0.45);
  background: rgba(59, 165, 93, 0.1);
}

.voice-tile.speaking {
  border-color: rgba(59, 165, 93, 0.92);
  background:
    linear-gradient(180deg, rgba(59, 165, 93, calc(0.08 + var(--voice-level) * 0.08)), transparent),
    var(--discord-surface);
  box-shadow:
    0 0 0 1px rgba(59, 165, 93, 0.24),
    0 0 calc(18px + var(--voice-level) * 24px) rgba(59, 165, 93, calc(0.18 + var(--voice-level) * 0.22));
  transform: translateY(-1px);
}

.voice-tile.speaking::after {
  content: '正在说话';
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 7px;
  border-radius: 999px;
  background: rgba(59, 165, 93, 0.16);
  color: var(--discord-green);
  font-size: 11px;
  font-weight: 900;
}

.voice-avatar {
  width: 72px;
  height: 72px;
  position: relative;
  border-radius: 50%;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
  transition:
    box-shadow 120ms ease,
    transform 120ms ease;
}

.voice-tile.speaking .voice-avatar {
  box-shadow:
    0 0 0 4px rgba(59, 165, 93, 0.28),
    0 0 calc(14px + var(--voice-level) * 22px) rgba(59, 165, 93, 0.62);
  transform: scale(calc(1 + var(--voice-level) * 0.04));
}

.voice-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.voice-avatar span {
  line-height: 1;
}

.voice-tile strong {
  font-size: 17px;
}

.voice-tile span {
  color: var(--discord-text-faint);
  font-size: 13px;
}

.voice-empty {
  grid-column: 1 / -1;
  min-height: 0;
  margin-top: clamp(52px, 10vh, 104px);
  display: grid;
  justify-items: center;
  align-content: start;
  gap: 10px;
  color: var(--discord-text-muted);
  text-align: center;
}

.voice-empty svg {
  display: block;
}

.voice-empty strong {
  color: var(--discord-text);
  font-size: 20px;
}

.voice-empty p {
  margin: 0;
}

@media (max-width: 720px) {
  .voice-hero {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
