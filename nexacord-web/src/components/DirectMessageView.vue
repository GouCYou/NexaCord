<template>
  <section class="direct-layout">
    <header class="direct-header">
      <button
        v-if="currentConversation"
        class="direct-user"
        type="button"
        @click="openUserPopover(currentConversation.otherUser, $event)"
      >
        <span class="direct-avatar">
          <img :src="currentConversation.otherUser.avatarUrl || defaultAvatarUrl" :alt="displayUserName(currentConversation.otherUser)" />
          <i :class="['status-dot', currentConversation.otherUser.status || 'offline']"></i>
        </span>
        <span>
          <strong>{{ displayUserName(currentConversation.otherUser) }}</strong>
          <small>{{ statusLabel(currentConversation.otherUser.status) }}</small>
        </span>
      </button>

      <div v-else class="direct-user placeholder">
        <span class="direct-avatar"></span>
        <span>
          <strong>私信</strong>
          <small>选择一位好友开始对话</small>
        </span>
      </div>

      <button
        class="header-action"
        type="button"
        title="发起语音呼叫"
        :disabled="!currentConversation"
        @click="startCall"
      >
        <PhoneCall :size="18" aria-hidden="true" />
      </button>
    </header>

    <main ref="messagesContainer" class="direct-messages">
      <div v-if="isLoading" class="state-block">正在加载私信……</div>
      <div v-else-if="error" class="state-block error">{{ error }}</div>
      <div v-else-if="!currentConversation" class="state-block">这条私信会话不存在，或者你没有访问权限。</div>

      <template v-else>
        <div v-if="currentMessages.length === 0" class="direct-welcome">
          <div class="welcome-avatar">
            <img :src="currentConversation.otherUser.avatarUrl || defaultAvatarUrl" :alt="displayUserName(currentConversation.otherUser)" />
          </div>
          <h1>{{ displayUserName(currentConversation.otherUser) }}</h1>
          <p>这是你们私信的开始。</p>
        </div>

        <article
          v-for="message in currentMessages"
          :key="message.id"
          class="message-row"
          :class="{ own: message.author.id === currentUser?.id }"
        >
          <button class="message-avatar" type="button" @click="openUserPopover(message.author, $event)">
            <img :src="message.author.avatarUrl || defaultAvatarUrl" :alt="displayUserName(message.author)" />
          </button>
          <div class="message-body">
            <header>
              <button type="button" @click="openUserPopover(message.author, $event)">
                {{ displayUserName(message.author) }}
              </button>
              <span>{{ formatTime(message.createdAt) }}</span>
            </header>
            <p>{{ message.content }}</p>
          </div>
        </article>
      </template>
    </main>

    <footer v-if="currentConversation" class="direct-composer">
      <textarea
        ref="composerTextarea"
        v-model="draft"
        rows="1"
        :placeholder="`发送私信给 ${displayUserName(currentConversation.otherUser)}`"
        @input="autoResizeComposer"
        @keydown.enter.exact.prevent="sendCurrentMessage"
        @keydown.enter.shift="handleShiftEnter"
      ></textarea>
      <button type="button" :disabled="isSending || !draft.trim()" @click="sendCurrentMessage">
        <Send :size="17" aria-hidden="true" />
        <span>{{ isSending ? '发送中' : '发送' }}</span>
      </button>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { storeToRefs } from 'pinia';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { PhoneCall, Send } from 'lucide-vue-next';
import { useDirectMessageStore } from '../stores/directMessageStore';
import { useUserStore } from '../stores/userStore';
import { useVoiceStore } from '../stores/voiceStore';
import type { User } from '../types';
import { displayUserLabel } from '../utils/userDisplay';

dayjs.locale('zh-cn');

const route = useRoute();
const directMessageStore = useDirectMessageStore();
const userStore = useUserStore();
const voiceStore = useVoiceStore();
const { currentUser } = storeToRefs(userStore);
const { currentConversation, currentMessages, isLoading, isSending, error } = storeToRefs(directMessageStore);

const defaultAvatarUrl = '/logo.png';
const messagesContainer = ref<HTMLElement | null>(null);
const composerTextarea = ref<HTMLTextAreaElement | null>(null);
const draft = ref('');

const conversationId = computed(() => {
  const parsedId = Number.parseInt(route.params.conversationId as string, 10);
  return Number.isNaN(parsedId) ? null : parsedId;
});

const displayUserName = (user: Pick<User, 'username' | 'displayName'>) => displayUserLabel(user);

const statusLabel = (status: User['status']) => {
  const labels = {
    online: '在线',
    offline: '离线',
    away: '闲置',
    dnd: '请勿打扰',
  } as const;

  return labels[status] || '离线';
};

const formatTime = (time: string) => {
  const parsedTime = dayjs(time);
  if (parsedTime.isSame(dayjs(), 'day')) {
    return parsedTime.format('HH:mm');
  }

  if (parsedTime.isSame(dayjs(), 'year')) {
    return parsedTime.format('M月D日 HH:mm');
  }

  return parsedTime.format('YYYY年M月D日 HH:mm');
};

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
};

const autoResizeComposer = () => {
  nextTick(() => {
    if (!composerTextarea.value) {
      return;
    }

    composerTextarea.value.style.height = '0px';
    composerTextarea.value.style.height = `${Math.min(composerTextarea.value.scrollHeight, 160)}px`;
  });
};

const handleShiftEnter = (event: KeyboardEvent) => {
  event.preventDefault();
  const textarea = event.target as HTMLTextAreaElement;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;

  draft.value = `${draft.value.substring(0, start)}\n${draft.value.substring(end)}`;
  nextTick(() => {
    textarea.selectionStart = textarea.selectionEnd = start + 1;
    autoResizeComposer();
  });
};

const sendCurrentMessage = async () => {
  if (!conversationId.value || !draft.value.trim()) {
    return;
  }

  const success = await directMessageStore.sendMessage(conversationId.value, draft.value);
  if (success) {
    draft.value = '';
    autoResizeComposer();
    scrollToBottom();
  }
};

const openUserPopover = (user: User, event: MouseEvent) => {
  window.dispatchEvent(new CustomEvent('nexacord:open-user-popover', {
    detail: {
      user,
      x: event.clientX,
      y: event.clientY,
    },
  }));
};

const startCall = () => {
  if (!currentConversation.value) {
    return;
  }

  voiceStore.startDirectCall(currentConversation.value.otherUser);
};

watch(
  conversationId,
  async (nextConversationId) => {
    if (!nextConversationId) {
      directMessageStore.clearCurrentConversation();
      return;
    }

    await directMessageStore.fetchConversation(nextConversationId);
    await directMessageStore.fetchMessages(nextConversationId);
    scrollToBottom();
  },
  { immediate: true }
);

watch(currentMessages, scrollToBottom, { deep: true });
watch(draft, autoResizeComposer);

onMounted(() => {
  directMessageStore.initializeRealtime();
  autoResizeComposer();
  scrollToBottom();
});
</script>

<style scoped>
.direct-layout {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-rows: auto 1fr auto;
  background: var(--discord-bg);
}

.direct-header {
  min-width: 0;
  min-height: 62px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 18px;
  border-bottom: 1px solid var(--discord-border);
  background: color-mix(in srgb, var(--discord-bg) 95%, transparent);
  backdrop-filter: blur(14px);
}

.direct-user {
  min-width: 0;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 4px 8px 4px 0;
  border-radius: 8px;
  background: transparent;
  color: var(--discord-text);
  text-align: left;
}

.direct-user:not(.placeholder):hover {
  background: var(--discord-hover);
}

.direct-user span:last-child {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.direct-user strong,
.direct-user small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.direct-user small {
  color: var(--discord-text-faint);
  font-size: 12px;
}

.direct-avatar,
.message-avatar,
.welcome-avatar {
  position: relative;
  border-radius: 50%;
  display: grid;
  place-items: center;
  overflow: visible;
  background: var(--discord-brand);
}

.direct-avatar {
  width: 40px;
  height: 40px;
}

.direct-avatar img,
.message-avatar img,
.welcome-avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.status-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 13px;
  height: 13px;
  border: 3px solid var(--discord-bg);
  border-radius: 50%;
  background: var(--discord-green);
}

.status-dot.offline {
  background: #80848e;
}

.status-dot.away {
  background: #f0b232;
}

.status-dot.dnd {
  background: var(--discord-red);
}

.header-action {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: var(--discord-muted-surface);
  color: var(--discord-text-muted);
}

.header-action:hover:not(:disabled) {
  background: var(--discord-hover-strong);
  color: var(--discord-text);
}

.header-action:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.direct-messages {
  min-height: 0;
  overflow-y: auto;
  padding: 14px 0 24px;
}

.state-block {
  min-height: 260px;
  display: grid;
  place-items: center;
  color: var(--discord-text-faint);
}

.state-block.error {
  color: #ff8b8d;
}

.direct-welcome {
  display: grid;
  justify-items: start;
  gap: 8px;
  padding: 52px 22px 24px;
}

.welcome-avatar {
  width: 78px;
  height: 78px;
}

.direct-welcome h1,
.direct-welcome p,
.message-body p {
  margin: 0;
}

.direct-welcome h1 {
  font-size: 30px;
}

.direct-welcome p {
  color: var(--discord-text-muted);
}

.message-row {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 14px;
  padding: 8px 18px;
}

.message-row:hover {
  background: var(--discord-subtle);
}

.message-row.own {
  background: linear-gradient(90deg, rgba(88, 101, 242, 0.06), transparent 60%);
}

.message-avatar {
  width: 40px;
  height: 40px;
  margin-top: 2px;
  overflow: hidden;
}

.message-body {
  min-width: 0;
}

.message-body header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.message-body header button {
  padding: 0;
  background: transparent;
  color: var(--discord-text);
  font-size: 15px;
  font-weight: 900;
}

.message-body header button:hover {
  text-decoration: underline;
}

.message-body header span {
  color: var(--discord-text-faint);
  font-size: 12px;
}

.message-body p {
  margin-top: 4px;
  color: var(--discord-text);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.direct-composer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: end;
  gap: 10px;
  padding: 14px 18px 18px;
}

.direct-composer textarea {
  min-height: 46px;
  max-height: 160px;
  resize: none;
  border: 1px solid var(--discord-border);
  border-radius: 10px;
  padding: 13px 14px;
  background: var(--discord-input);
  color: var(--discord-text);
  line-height: 1.45;
}

.direct-composer button {
  min-height: 46px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 18px;
  border-radius: 10px;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
}

.direct-composer button:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

.direct-composer button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}
</style>
