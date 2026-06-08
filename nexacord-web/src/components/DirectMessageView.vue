<template>
  <section class="direct-layout">
    <header class="direct-header">
      <button class="mobile-nav-button" type="button" aria-label="打开导航" @click="openMobileNav">
        <Menu :size="22" aria-hidden="true" />
      </button>

      <button
        v-if="currentConversation"
        class="direct-user"
        type="button"
        @click="openUserPopover(currentConversation.otherUser, $event)"
      >
        <span class="direct-avatar">
          <AvatarImage :src="currentConversation.otherUser.avatarUrl || defaultAvatarUrl" :alt="displayUserName(currentConversation.otherUser)" />
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
        :disabled="!currentConversation || isDirectCallPending"
        @click="startCall"
      >
        <PhoneCall :size="18" aria-hidden="true" />
      </button>
    </header>

    <main ref="messagesContainer" class="direct-messages">
      <div v-if="isLoading && currentMessages.length === 0" class="state-block">正在加载私信……</div>
      <div v-else-if="error" class="state-block error">{{ error }}</div>
      <div v-else-if="!currentConversation" class="state-block">这条私信会话不存在，或者你没有访问权限。</div>

      <template v-else>
        <div v-if="currentMessages.length === 0" class="direct-welcome">
          <div class="welcome-avatar">
            <AvatarImage :src="currentConversation.otherUser.avatarUrl || defaultAvatarUrl" :alt="displayUserName(currentConversation.otherUser)" />
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
            <AvatarImage :src="message.author.avatarUrl || defaultAvatarUrl" :alt="displayUserName(message.author)" />
          </button>
          <div class="message-body">
            <header>
              <button type="button" @click="openUserPopover(message.author, $event)">
                {{ displayUserName(message.author) }}
              </button>
              <span>{{ formatTime(message.createdAt) }}</span>
              <em v-if="message.edited">(已编辑)</em>
            </header>

            <form v-if="editingMessageId === message.id" class="message-edit" @submit.prevent="saveMessageEdit(message.id)">
              <textarea
                v-model="editingContent"
                rows="1"
                @keydown.enter.exact.prevent="saveMessageEdit(message.id)"
                @keydown.esc.prevent="cancelMessageEdit"
              ></textarea>
              <div class="message-edit-actions">
                <button type="button" @click="cancelMessageEdit">取消</button>
                <button type="submit" :disabled="!editingContent.trim()">保存</button>
              </div>
            </form>

            <div v-else-if="callPayload(message.content)" class="direct-call-card">
              <div class="call-record-icon">
                <PhoneCall :size="22" aria-hidden="true" />
              </div>
              <div class="call-record-copy">
                <span>{{ callRecordTitle(message) }}</span>
                <strong>{{ callRecordSummary(message) }}</strong>
                <small>{{ formatTime(message.createdAt) }}</small>
              </div>
            </div>

            <div v-else-if="invitePayload(message.content)" class="server-invite-card">
              <div class="invite-icon">
                <img
                  v-if="invitePayload(message.content)?.serverIconUrl"
                  :src="invitePayload(message.content)?.serverIconUrl || ''"
                  :alt="invitePayload(message.content)?.serverName"
                />
                <ServerIcon v-else :size="24" aria-hidden="true" />
              </div>
              <div class="invite-copy">
                <span>{{ inviteTypeLabel(message) }}</span>
                <strong>{{ inviteTargetName(message) }}</strong>
                <small>{{ inviteStateLabel(message) }}</small>
              </div>
              <div class="invite-actions">
                <button
                  class="invite-accept"
                  type="button"
                  :disabled="isInviteResolved(message)"
                  @click="acceptInviteMessage(message)"
                >
                  <Check :size="15" aria-hidden="true" />
                  <span>接受</span>
                </button>
                <button
                  class="invite-decline"
                  type="button"
                  :disabled="isInviteResolved(message)"
                  @click="declineInviteMessage(message)"
                >
                  <XCircle :size="15" aria-hidden="true" />
                  <span>拒绝</span>
                </button>
              </div>
            </div>

            <p v-else-if="message.content">{{ message.content }}</p>

            <div v-if="(message.attachments?.length ?? 0) > 0" class="attachments">
              <ImageAttachment
                v-for="attachment in imageAttachments(message.attachments || [])"
                :key="attachment.id"
                :source-url="attachment.url"
                :alt="attachment.fileName"
                @preview="openImagePreview"
              />
            </div>
          </div>
          <div v-if="message.author.id === currentUser?.id && !invitePayload(message.content) && !callPayload(message.content)" class="message-actions">
            <button type="button" title="编辑消息" @click="startMessageEdit(message)">
              <Pencil :size="15" aria-hidden="true" />
            </button>
            <button type="button" title="删除消息" @click="deleteMessage(message.id)">
              <Trash2 :size="15" aria-hidden="true" />
            </button>
          </div>
        </article>
      </template>
    </main>

    <footer v-if="currentConversation" class="direct-composer">
      <div v-if="pendingFiles.length > 0" class="pending-files">
        <div
          v-for="(file, index) in pendingFiles"
          :key="`${file.name}-${file.size}-${index}`"
          class="pending-file"
        >
          <img class="pending-image" :src="filePreviewUrl(file)" :alt="file.name" />
          <span class="pending-file-copy">
            <strong>{{ file.name }}</strong>
            <small>{{ formatFileSize(file.size) }}</small>
          </span>
          <button type="button" class="pending-file-remove" aria-label="移除附件" @click="removePendingFile(index)">
            <X :size="16" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div class="composer-input">
        <input ref="fileInput" class="visually-hidden" type="file" accept="image/*" multiple @change="handleFileSelection" />
        <button class="attach-button" type="button" title="添加图片" :disabled="isUploading" @click="openFilePicker">
          <Paperclip :size="19" aria-hidden="true" />
        </button>
        <textarea
          ref="composerTextarea"
          v-model="draft"
          rows="1"
          :placeholder="`发送私信给 ${displayUserName(currentConversation.otherUser)}`"
          @input="autoResizeComposer"
          @keydown.enter.exact.prevent="sendCurrentMessage"
          @keydown.enter.shift="handleShiftEnter"
        ></textarea>
        <button type="button" :disabled="isSending || isUploading || !canSend" @click="sendCurrentMessage">
          <Send :size="17" aria-hidden="true" />
          <span>{{ sendButtonLabel }}</span>
        </button>
      </div>
      <p v-if="composerError" class="composer-error">{{ composerError }}</p>
    </footer>

    <ImagePreviewModal :image-url="previewImageUrl" @close="closeImagePreview" />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { storeToRefs } from 'pinia';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { Check, Menu, Paperclip, Pencil, PhoneCall, Send, Server as ServerIcon, Trash2, X, XCircle } from 'lucide-vue-next';
import ImageAttachment from './ImageAttachment.vue';
import ImagePreviewModal from './ImagePreviewModal.vue';
import fileService from '../services/fileService';
import serverService from '../services/serverService';
import type { DirectMessageCreateAttachment } from '../services/directMessageService';
import { useChannelStore } from '../stores/channelStore';
import { useDirectMessageStore } from '../stores/directMessageStore';
import { useServerStore } from '../stores/serverStore';
import { useUserStore } from '../stores/userStore';
import { useVoiceStore } from '../stores/voiceStore';
import type { Attachment, DirectMessage, User } from '../types';
import { formatCallDuration, parseDirectCallMessage } from '../utils/directCallMessage';
import { parseDirectInviteMessage } from '../utils/inviteMessage';
import { displayUserLabel } from '../utils/userDisplay';

dayjs.locale('zh-cn');

const route = useRoute();
const router = useRouter();
const directMessageStore = useDirectMessageStore();
const channelStore = useChannelStore();
const serverStore = useServerStore();
const userStore = useUserStore();
const voiceStore = useVoiceStore();
const { currentUser } = storeToRefs(userStore);
const { servers } = storeToRefs(serverStore);
const { currentConversation, currentMessages, isLoading, isSending, error } = storeToRefs(directMessageStore);

const defaultAvatarUrl = '/logo.png';
const messagesContainer = ref<HTMLElement | null>(null);
const composerTextarea = ref<HTMLTextAreaElement | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const draft = ref('');
const pendingFiles = ref<File[]>([]);
const isUploading = ref(false);
const localError = ref<string | null>(null);
const editingMessageId = ref<number | null>(null);
const editingContent = ref('');
const previewImageUrl = ref<string | null>(null);
const inviteStates = ref<Record<string, 'accepted' | 'declined'>>({});
const previewUrls = new Map<File, string>();

const { outgoingCall } = storeToRefs(voiceStore);

const conversationId = computed(() => {
  const parsedId = Number.parseInt(route.params.conversationId as string, 10);
  return Number.isNaN(parsedId) ? null : parsedId;
});

const displayUserName = (user: Pick<User, 'username' | 'displayName'>) => displayUserLabel(user);

const isDirectCallPending = computed(() => Boolean(outgoingCall.value));
const canSend = computed(() => draft.value.trim().length > 0 || pendingFiles.value.length > 0);
const composerError = computed(() => localError.value || error.value);
const sendButtonLabel = computed(() => {
  if (isUploading.value) {
    return '上传中……';
  }

  return isSending.value ? '发送中' : '发送';
});

const statusLabel = (status: User['status']) => {
  const labels = {
    online: '在线',
    offline: '离线',
    away: '闲置',
    dnd: '请勿打扰',
  } as const;

  return labels[status] || '离线';
};

const inviteStorageKey = computed(() => `nexacord:invite-actions:${currentUser.value?.id || 'guest'}`);

const loadInviteStates = () => {
  try {
    inviteStates.value = JSON.parse(localStorage.getItem(inviteStorageKey.value) || '{}') as Record<
      string,
      'accepted' | 'declined'
    >;
  } catch {
    inviteStates.value = {};
  }
};

const persistInviteStates = () => {
  localStorage.setItem(inviteStorageKey.value, JSON.stringify(inviteStates.value));
};

const invitePayload = (content: string) => parseDirectInviteMessage(content);
const callPayload = (content: string) => parseDirectCallMessage(content);
const callRecordTitle = (message: DirectMessage) => {
  const call = callPayload(message.content);
  if (call?.status === 'declined') {
    return '已拒绝的语音通话';
  }
  if (call?.status === 'cancelled') {
    return '已取消的语音通话';
  }
  return '语音通话';
};
const callRecordSummary = (message: DirectMessage) => {
  const call = callPayload(message.content);
  if (!call) {
    return '';
  }

  if (call.status === 'declined') {
    return message.author.id === currentUser.value?.id
      ? '你拒绝了语音通话'
      : `${displayUserName(message.author)} 拒绝了语音通话`;
  }

  if (call.status === 'cancelled') {
    return message.author.id === currentUser.value?.id
      ? '你取消了语音通话'
      : `${displayUserName(message.author)} 取消了语音通话`;
  }

  return `通话时长 ${formatCallDuration(call.durationSeconds)}`;
};
const inviteStateKey = (message: DirectMessage) => `${message.id}:${invitePayload(message.content)?.code || ''}`;
const inviteState = (message: DirectMessage) => inviteStates.value[inviteStateKey(message)];
const isInviteAlreadyMember = (message: DirectMessage) => {
  const invite = invitePayload(message.content);
  return Boolean(invite && servers.value.some((server) => server.id === invite.serverId));
};
const isInviteResolved = (message: DirectMessage) => Boolean(inviteState(message)) || isInviteAlreadyMember(message);
const inviteTypeLabel = (message: DirectMessage) => {
  const invite = invitePayload(message.content);
  return invite?.channelId ? '频道邀请' : '服务器邀请';
};
const inviteTargetName = (message: DirectMessage) => {
  const invite = invitePayload(message.content);
  if (!invite) {
    return '';
  }

  if (invite.channelId && invite.channelName) {
    return `${invite.channelType === 'VOICE' ? '语音频道' : '#'} ${invite.channelName}`;
  }

  return invite.serverName;
};
const inviteStateLabel = (message: DirectMessage) => {
  if (isInviteAlreadyMember(message)) {
    const invite = invitePayload(message.content);
    return invite?.channelId ? '你已经是该频道成员。' : '你已经是该服务器成员。';
  }

  const state = inviteState(message);
  if (state === 'accepted') {
    return '你已接受这个邀请。';
  }
  if (state === 'declined') {
    return '你已拒绝这个邀请。';
  }

  const invite = invitePayload(message.content);
  return invite?.channelId
    ? `加入 ${invite.serverName} 并进入这个频道。`
    : '在私信中选择是否加入这个服务器。';
};

const setInviteState = (message: DirectMessage, state: 'accepted' | 'declined') => {
  inviteStates.value = {
    ...inviteStates.value,
    [inviteStateKey(message)]: state,
  };
  persistInviteStates();
};

const acceptInviteMessage = async (message: DirectMessage) => {
  const invite = invitePayload(message.content);
  if (!invite || isInviteResolved(message)) {
    return;
  }

  try {
    const server = await serverService.joinInvite(invite.code);
    await serverStore.fetchServers(invite.serverId);
    setInviteState(message, 'accepted');
    if (invite.channelId) {
      await channelStore.fetchChannels(server.id, invite.channelId);
      channelStore.setCurrentChannel(invite.channelId);
      router.push(`/servers/${server.id}/channels/${invite.channelId}`);
      return;
    }

    router.push(`/servers/${server.id}`);
  } catch {
    localError.value = '接受服务器邀请失败，这个邀请可能已经失效。';
  }
};

const declineInviteMessage = (message: DirectMessage) => {
  if (!invitePayload(message.content) || isInviteResolved(message)) {
    return;
  }

  setInviteState(message, 'declined');
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
  const content = draft.value.trim();
  if (!conversationId.value || (!content && pendingFiles.value.length === 0)) {
    return;
  }

  localError.value = null;
  let uploadedAttachments: DirectMessageCreateAttachment[] = [];

  try {
    uploadedAttachments = await uploadPendingFiles();
    const success = await directMessageStore.sendMessage(conversationId.value, content, uploadedAttachments);
    if (success) {
      resetComposer();
      scrollToBottom();
      return;
    }

    await cleanupUploadedFiles(uploadedAttachments);
  } catch (uploadError: any) {
    const attachmentsToCleanup = uploadError?.uploadedAttachments || uploadedAttachments;
    await cleanupUploadedFiles(attachmentsToCleanup);
    localError.value =
      uploadError?.response?.data?.error ||
      uploadError?.response?.data?.message ||
      uploadError?.message ||
      '上传所选图片失败。';
  }
};

const isImageAttachment = (attachment: Attachment) =>
  attachment.fileType?.startsWith('image/') || /\.(png|jpe?g|gif|webp|avif|svg)$/i.test(attachment.url);

const imageAttachments = (attachments: Attachment[]) => attachments.filter(isImageAttachment);

const isImageFile = (file: File) => file.type.startsWith('image/');

const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1073741824) return `${(bytes / 1048576).toFixed(1)} MB`;
  return `${(bytes / 1073741824).toFixed(1)} GB`;
};

const filePreviewUrl = (file: File) => {
  const existingUrl = previewUrls.get(file);
  if (existingUrl) {
    return existingUrl;
  }

  const url = URL.createObjectURL(file);
  previewUrls.set(file, url);
  return url;
};

const revokeFilePreview = (file: File) => {
  const url = previewUrls.get(file);
  if (url) {
    URL.revokeObjectURL(url);
    previewUrls.delete(file);
  }
};

const revokeAllFilePreviews = () => {
  previewUrls.forEach((url) => URL.revokeObjectURL(url));
  previewUrls.clear();
};

const resetFileInput = () => {
  if (fileInput.value) {
    fileInput.value.value = '';
  }
};

const openFilePicker = () => {
  fileInput.value?.click();
};

const handleFileSelection = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const selectedFiles = Array.from(input.files || []);
  if (selectedFiles.length === 0) {
    return;
  }

  const nonImageFile = selectedFiles.find((file) => !isImageFile(file));
  if (nonImageFile) {
    localError.value = '当前只能发送图片。';
    resetFileInput();
    return;
  }

  const existingKeys = new Set(
    pendingFiles.value.map((file) => `${file.name}-${file.size}-${file.lastModified}`)
  );
  pendingFiles.value = [
    ...pendingFiles.value,
    ...selectedFiles.filter((file) => !existingKeys.has(`${file.name}-${file.size}-${file.lastModified}`)),
  ];
  localError.value = null;
  resetFileInput();
};

const removePendingFile = (index: number) => {
  const removedFile = pendingFiles.value[index];
  if (removedFile) {
    revokeFilePreview(removedFile);
  }
  pendingFiles.value = pendingFiles.value.filter((_, currentIndex) => currentIndex !== index);
};

const uploadPendingFiles = async (): Promise<DirectMessageCreateAttachment[]> => {
  if (pendingFiles.value.length === 0) {
    return [];
  }

  isUploading.value = true;
  const uploadedAttachments: DirectMessageCreateAttachment[] = [];

  try {
    for (const file of pendingFiles.value) {
      uploadedAttachments.push({
        fileName: file.name,
        fileType: file.type || 'image/*',
        fileSize: file.size,
        url: await fileService.uploadFile(file),
      });
    }
    return uploadedAttachments;
  } catch (uploadError: any) {
    uploadError.uploadedAttachments = uploadedAttachments;
    throw uploadError;
  } finally {
    isUploading.value = false;
  }
};

const cleanupUploadedFiles = async (attachments: DirectMessageCreateAttachment[]) => {
  await Promise.allSettled(attachments.map((attachment) => fileService.deleteFile(attachment.url)));
};

const resetComposer = () => {
  draft.value = '';
  pendingFiles.value = [];
  revokeAllFilePreviews();
  resetFileInput();
  autoResizeComposer();
};

const openImagePreview = (url: string) => {
  previewImageUrl.value = url;
};

const closeImagePreview = () => {
  previewImageUrl.value = null;
};

const startMessageEdit = (message: DirectMessage) => {
  editingMessageId.value = message.id;
  editingContent.value = message.content;
};

const cancelMessageEdit = () => {
  editingMessageId.value = null;
  editingContent.value = '';
};

const saveMessageEdit = async (messageId: number) => {
  if (!conversationId.value || !editingContent.value.trim()) {
    return;
  }

  const success = await directMessageStore.updateMessage(
    conversationId.value,
    messageId,
    editingContent.value.trim()
  );
  if (success) {
    cancelMessageEdit();
  }
};

const deleteMessage = async (messageId: number) => {
  if (!conversationId.value) {
    return;
  }

  await directMessageStore.deleteMessage(conversationId.value, messageId);
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

const openMobileNav = () => {
  window.dispatchEvent(new CustomEvent('nexacord:open-mobile-nav'));
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
  voiceStore.initializeRealtime();
  directMessageStore.initializeRealtime();
  loadInviteStates();
  autoResizeComposer();
  scrollToBottom();
});

onBeforeUnmount(() => {
  revokeAllFilePreviews();
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

.mobile-nav-button {
  display: none;
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
  position: relative;
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

.message-body header em {
  color: var(--discord-text-faint);
  font-size: 12px;
  font-style: italic;
}

.message-body p {
  margin-top: 4px;
  color: var(--discord-text);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}

.attachments {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.direct-call-card {
  width: min(420px, 100%);
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  padding: 12px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  background: color-mix(in srgb, var(--discord-surface) 90%, var(--discord-green));
}

.call-record-icon {
  width: 46px;
  height: 46px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--discord-green) 20%, var(--discord-surface-soft));
  color: var(--discord-green);
}

.call-record-copy {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.call-record-copy span,
.call-record-copy small {
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 800;
}

.call-record-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--discord-text);
  font-size: 15px;
}

.server-invite-card {
  width: min(420px, 100%);
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr);
  gap: 12px;
  margin-top: 8px;
  padding: 14px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  background: var(--discord-surface);
  box-shadow: 0 8px 22px rgba(0, 0, 0, 0.08);
}

.invite-icon {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: #f2f3f5;
  color: #1e1f22;
}

.invite-icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.invite-copy {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.invite-copy span {
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.invite-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 17px;
}

.invite-copy small {
  color: var(--discord-text-muted);
  font-size: 12px;
}

.invite-actions {
  grid-column: 2;
  display: flex;
  gap: 8px;
  margin-top: 6px;
}

.invite-actions button {
  min-height: 32px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 900;
}

.invite-accept {
  background: var(--discord-green);
  color: white;
}

.invite-decline {
  background: var(--discord-muted-surface);
  color: var(--discord-text);
}

.invite-actions button:disabled {
  opacity: 0.54;
}

.message-actions {
  position: absolute;
  top: -8px;
  right: 18px;
  display: flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  background: var(--discord-elevated);
  box-shadow: var(--discord-shadow);
  opacity: 0;
  pointer-events: none;
  transition: opacity 120ms ease;
}

.message-row:hover .message-actions {
  opacity: 1;
  pointer-events: auto;
}

.message-actions button {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--discord-text-muted);
}

.message-actions button:hover {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.message-edit {
  display: grid;
  gap: 6px;
  margin-top: 6px;
}

.message-edit textarea {
  min-height: 42px;
  max-height: 160px;
  resize: vertical;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  padding: 10px 12px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.message-edit-actions {
  display: flex;
  gap: 8px;
}

.message-edit-actions button {
  min-height: 30px;
  padding: 0 10px;
  border-radius: 7px;
  background: var(--discord-muted-surface);
  color: var(--discord-text);
  font-size: 12px;
  font-weight: 800;
}

.message-edit-actions button[type='submit'] {
  background: var(--discord-brand);
  color: white;
}

.direct-composer {
  display: grid;
  gap: 10px;
  padding: 14px 18px 18px;
}

.pending-files {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pending-file {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--discord-muted-surface);
}

.pending-image {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: cover;
  background: var(--discord-input);
}

.pending-file-copy {
  display: grid;
  gap: 2px;
}

.pending-file-copy strong {
  font-size: 13px;
}

.pending-file-copy small {
  color: var(--discord-text-faint);
}

.pending-file-remove {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--discord-hover);
  color: var(--discord-text-muted);
}

.composer-input {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 64px;
  padding: 10px 12px;
  border-radius: 14px;
  background: var(--discord-surface-soft);
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.attach-button {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  background: var(--discord-hover);
  color: var(--discord-text);
}

.direct-composer textarea {
  flex: 1;
  min-height: 28px;
  max-height: 180px;
  resize: none;
  border: 0;
  padding: 6px 0;
  background: transparent;
  color: var(--discord-text);
  line-height: 1.45;
}

.direct-composer textarea:focus {
  outline: none;
}

.composer-input > button:last-child {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 10px 16px;
  border-radius: 10px;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
}

.composer-input > button:last-child:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

.direct-composer button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.composer-error {
  margin: 0;
  color: #ff8b8d;
  font-size: 13px;
}

@media (max-width: 760px) {
  .direct-layout {
    height: 100dvh;
  }

  .direct-header {
    min-height: calc(54px + env(safe-area-inset-top));
    gap: 8px;
    padding: calc(8px + env(safe-area-inset-top)) 8px 8px;
  }

  .mobile-nav-button {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    flex: 0 0 38px;
    display: grid;
    place-items: center;
    background: var(--discord-muted-surface);
    color: var(--discord-text);
  }

  .direct-user {
    grid-template-columns: 36px minmax(0, 1fr);
    gap: 9px;
    padding-right: 4px;
  }

  .direct-avatar {
    width: 36px;
    height: 36px;
  }

  .header-action {
    width: 38px;
    height: 38px;
  }

  .direct-messages {
    padding: 8px 0 12px;
  }

  .direct-welcome {
    padding: 34px 14px 18px;
  }

  .direct-welcome h1 {
    font-size: 26px;
  }

  .message-row {
    grid-template-columns: 38px minmax(0, 1fr);
    gap: 9px;
    padding: 7px 10px;
  }

  .message-row.own {
    padding-right: 78px;
  }

  .message-avatar {
    width: 34px;
    height: 34px;
  }

  .message-actions {
    top: 4px;
    right: 8px;
    opacity: 1;
    pointer-events: auto;
  }

  .direct-call-card,
  .server-invite-card {
    width: 100%;
  }

  .server-invite-card {
    grid-template-columns: 44px minmax(0, 1fr);
    padding: 12px;
  }

  .invite-icon {
    width: 44px;
    height: 44px;
    border-radius: 14px;
  }

  .invite-actions {
    grid-column: 1 / -1;
  }

  .direct-composer {
    padding: 0 8px calc(8px + env(safe-area-inset-bottom));
  }

  .pending-file {
    max-width: 100%;
  }

  .pending-file-copy {
    min-width: 0;
  }

  .pending-file-copy strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .composer-input {
    min-height: 52px;
    gap: 6px;
    padding: 8px;
    border-radius: 18px;
  }

  .attach-button {
    width: 34px;
    height: 34px;
  }

  .composer-input > button:last-child {
    width: 38px;
    height: 38px;
    justify-content: center;
    padding: 0;
    border-radius: 50%;
  }

  .composer-input > button:last-child span {
    display: none;
  }
}
</style>
