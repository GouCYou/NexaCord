<template>
  <section class="message-layout">
    <header class="message-header">
      <div class="message-header-meta">
        <span class="message-header-prefix">#</span>
        <div class="message-header-copy">
          <h2>{{ currentChannel?.name || '频道' }}</h2>
          <p>{{ headerSubtitle }}</p>
        </div>
      </div>

      <div class="message-header-status">
        <span>{{ currentChannel?.type === 'VOICE' ? '语音频道' : '文字频道' }}</span>
        <span>{{ currentChannelMessages.length }} 条消息</span>
      </div>
    </header>

    <div ref="messagesContainer" class="messages-scroll">
      <div v-if="isLoading" class="status-block">
        <div class="spinner"></div>
        <p>正在加载消息...</p>
      </div>

      <div v-else-if="!currentChannel" class="status-block">
        <strong>请选择一个频道</strong>
        <p>从左侧频道列表中选中一个频道后，这里会加载对应的聊天内容。</p>
      </div>

      <div v-else-if="currentChannelMessages.length === 0" class="status-block welcome-block">
        <strong># {{ currentChannel.name }}</strong>
        <p>{{ headerSubtitle }}</p>
        <span class="welcome-chip">发送第一条消息</span>
      </div>

      <div v-else class="messages-list">
        <article
          v-for="message in currentChannelMessages"
          :key="message.id"
          class="message-row"
          :class="{ own: message.author.id === currentUser?.id }"
        >
          <div class="avatar">
            <img v-if="message.author.avatarUrl" :src="message.author.avatarUrl" :alt="message.author.username" />
            <span v-else>{{ message.author.username.charAt(0).toUpperCase() }}</span>
          </div>

          <div class="message-body">
            <header class="message-meta">
              <strong>{{ message.author.username }}</strong>
              <span>{{ formatTime(message.createdAt) }}</span>
              <em v-if="message.edited">已编辑</em>
            </header>

            <div v-if="message.content" class="message-content">{{ message.content }}</div>

            <div v-if="(message.attachments?.length ?? 0) > 0" class="attachments">
              <a
                v-for="attachment in message.attachments || []"
                :key="attachment.id"
                class="attachment"
                :href="attachment.url"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span class="attachment-icon">文件</span>
                <span class="attachment-copy">
                  <strong>{{ attachment.fileName }}</strong>
                  <small>{{ formatFileSize(attachment.fileSize) }}</small>
                </span>
              </a>
            </div>
          </div>
        </article>
      </div>
    </div>

    <footer class="composer">
      <div v-if="pendingFiles.length > 0" class="pending-files">
        <div
          v-for="(file, index) in pendingFiles"
          :key="`${file.name}-${file.size}-${index}`"
          class="pending-file"
        >
          <span class="pending-file-copy">
            <strong>{{ file.name }}</strong>
            <small>{{ formatFileSize(file.size) }}</small>
          </span>
          <button type="button" class="pending-file-remove" @click="removePendingFile(index)">x</button>
        </div>
      </div>

      <div class="composer-input">
        <input ref="fileInput" class="visually-hidden" type="file" multiple @change="handleFileSelection" />

        <button
          class="attach-button"
          type="button"
          title="添加附件"
          :disabled="isUploading || !currentChannelId"
          @click="openFilePicker"
        >
          +
        </button>

        <textarea
          ref="composerTextarea"
          v-model="newMessageContent"
          class="composer-textarea"
          rows="1"
          :placeholder="`发送到 #${currentChannel?.name || '当前频道'}`"
          @input="autoResizeComposer"
          @keydown.enter.exact.prevent="sendMessage"
          @keydown.enter.shift="handleShiftEnter"
        ></textarea>

        <button
          class="send-button"
          type="button"
          :disabled="isSending || isUploading || !canSend"
          @click="sendMessage"
        >
          {{ sendButtonLabel }}
        </button>
      </div>

      <p v-if="composerError" class="composer-error">{{ composerError }}</p>
      <p class="composer-hint">按 Enter 发送，Shift + Enter 换行。</p>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { storeToRefs } from 'pinia';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import fileService from '../services/fileService';
import { useChannelStore } from '../stores/channelStore';
import { useMessageStore, type MessageAttachmentInput } from '../stores/messageStore';
import { useUserStore } from '../stores/userStore';

dayjs.locale('zh-cn');

const route = useRoute();
const channelStore = useChannelStore();
const messageStore = useMessageStore();
const userStore = useUserStore();

const messagesContainer = ref<HTMLElement | null>(null);
const composerTextarea = ref<HTMLTextAreaElement | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const newMessageContent = ref('');
const pendingFiles = ref<File[]>([]);
const isUploading = ref(false);
const localError = ref<string | null>(null);

const { currentChannel, currentChannelId } = storeToRefs(channelStore);
const { currentChannelMessages, isLoading, isSending, error } = storeToRefs(messageStore);
const { currentUser } = storeToRefs(userStore);

const headerSubtitle = computed(() => {
  if (!currentChannel.value) {
    return '这里会显示当前频道的聊天内容。';
  }

  if (currentChannel.value.topic?.trim()) {
    return currentChannel.value.topic;
  }

  return currentChannel.value.type === 'VOICE'
    ? '语音交流空间已准备就绪。'
    : '从这里开始新的讨论。';
});

const canSend = computed(
  () => newMessageContent.value.trim().length > 0 || pendingFiles.value.length > 0
);

const sendButtonLabel = computed(() => {
  if (isUploading.value) {
    return '上传中...';
  }

  if (isSending.value) {
    return '发送中...';
  }

  return '发送';
});

const composerError = computed(() => localError.value || error.value);

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

const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1073741824) return `${(bytes / 1048576).toFixed(1)} MB`;
  return `${(bytes / 1073741824).toFixed(1)} GB`;
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
    composerTextarea.value.style.height = `${Math.min(composerTextarea.value.scrollHeight, 180)}px`;
  });
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

  const existingKeys = new Set(
    pendingFiles.value.map((file) => `${file.name}-${file.size}-${file.lastModified}`)
  );

  const nextFiles = selectedFiles.filter((file) => {
    const key = `${file.name}-${file.size}-${file.lastModified}`;
    return !existingKeys.has(key);
  });

  pendingFiles.value = [...pendingFiles.value, ...nextFiles];
  resetFileInput();
};

const removePendingFile = (index: number) => {
  pendingFiles.value = pendingFiles.value.filter((_, currentIndex) => currentIndex !== index);
};

const handleShiftEnter = (event: KeyboardEvent) => {
  event.preventDefault();
  const textarea = event.target as HTMLTextAreaElement;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;

  newMessageContent.value =
    `${newMessageContent.value.substring(0, start)}\n${newMessageContent.value.substring(end)}`;

  nextTick(() => {
    textarea.selectionStart = textarea.selectionEnd = start + 1;
    autoResizeComposer();
  });
};

const cleanupUploadedFiles = async (attachments: MessageAttachmentInput[]) => {
  await Promise.allSettled(attachments.map((attachment) => fileService.deleteFile(attachment.url)));
};

const uploadPendingFiles = async (): Promise<MessageAttachmentInput[]> => {
  if (pendingFiles.value.length === 0) {
    return [];
  }

  isUploading.value = true;
  const uploadedAttachments: MessageAttachmentInput[] = [];

  try {
    for (const file of pendingFiles.value) {
      uploadedAttachments.push({
        fileName: file.name,
        fileType: file.type || 'application/octet-stream',
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

const resetComposer = () => {
  newMessageContent.value = '';
  pendingFiles.value = [];
  resetFileInput();
  autoResizeComposer();
};

const sendMessage = async () => {
  const content = newMessageContent.value.trim();
  if (!currentChannelId.value || (!content && pendingFiles.value.length === 0)) {
    return;
  }

  localError.value = null;
  let uploadedAttachments: MessageAttachmentInput[] = [];

  try {
    uploadedAttachments = await uploadPendingFiles();

    const success = await messageStore.sendMessage(
      currentChannelId.value,
      content,
      uploadedAttachments
    );

    if (!success) {
      await cleanupUploadedFiles(uploadedAttachments);
      return;
    }

    resetComposer();
    scrollToBottom();
  } catch (uploadError: any) {
    const attachmentsToCleanup = uploadError?.uploadedAttachments || uploadedAttachments;
    await cleanupUploadedFiles(attachmentsToCleanup);
    localError.value =
      uploadError?.response?.data?.error ||
      uploadError?.response?.data?.message ||
      uploadError?.message ||
      '上传所选附件失败。';
  }
};

watch(
  () => route.params.channelId,
  (newChannelId) => {
    if (!newChannelId) {
      return;
    }

    const channelId = Number.parseInt(newChannelId as string, 10);
    if (Number.isNaN(channelId)) {
      return;
    }

    const shouldFetchChannel = channelId !== currentChannelId.value;
    channelStore.setCurrentChannel(channelId);
    messageStore.setCurrentChannelMessages(channelId);

    if (shouldFetchChannel) {
      channelStore.fetchChannelById(channelId);
    }

    messageStore.fetchMessages(channelId);
  },
  { immediate: true }
);

watch(
  currentChannelMessages,
  () => {
    if (!isLoading.value) {
      scrollToBottom();
    }
  },
  { deep: true }
);

watch(newMessageContent, () => {
  autoResizeComposer();
});

onMounted(() => {
  autoResizeComposer();
  scrollToBottom();
});
</script>

<style scoped>
.message-layout {
  display: grid;
  grid-template-rows: auto 1fr auto;
  height: 100%;
  min-height: 0;
  background: #313338;
}

.message-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--discord-border);
  background: rgba(49, 51, 56, 0.95);
  backdrop-filter: blur(14px);
}

.message-header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.message-header-prefix {
  color: var(--discord-text-faint);
  font-size: 24px;
  font-weight: 800;
}

.message-header-copy {
  min-width: 0;
}

.message-header-copy h2 {
  margin: 0;
  font-size: 18px;
}

.message-header-copy p {
  margin: 4px 0 0;
  color: var(--discord-text-faint);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-header-status {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 700;
}

.message-header-status span {
  padding: 7px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
}

.messages-scroll {
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0 20px;
}

.messages-list {
  display: grid;
}

.message-row {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 14px;
  padding: 8px 18px;
  transition: background-color 140ms ease;
}

.message-row:hover {
  background: rgba(4, 4, 5, 0.07);
}

.message-row.own {
  background: linear-gradient(90deg, rgba(88, 101, 242, 0.06), transparent 60%);
}

.avatar {
  width: 40px;
  height: 40px;
  margin-top: 2px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-weight: 800;
  overflow: hidden;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.message-body {
  min-width: 0;
}

.message-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.message-meta strong {
  font-size: 15px;
}

.message-meta span,
.message-meta em {
  color: var(--discord-text-faint);
  font-size: 12px;
  font-style: normal;
}

.message-content {
  margin-top: 4px;
  color: #dbdee1;
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

.attachment {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
  padding: 12px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--discord-text);
}

.attachment:hover {
  background: rgba(255, 255, 255, 0.1);
}

.attachment-icon {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: rgba(88, 101, 242, 0.18);
  color: #b9c0ff;
  font-size: 12px;
  font-weight: 800;
}

.attachment-copy {
  display: grid;
  gap: 2px;
}

.attachment-copy strong {
  font-size: 14px;
  font-weight: 700;
}

.attachment-copy small {
  color: var(--discord-text-faint);
}

.status-block {
  min-height: 100%;
  display: grid;
  place-content: center;
  gap: 10px;
  padding: 40px;
  color: var(--discord-text-muted);
  text-align: center;
}

.status-block strong {
  font-size: 20px;
  color: var(--discord-text);
}

.welcome-block {
  gap: 12px;
}

.welcome-chip {
  justify-self: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(88, 101, 242, 0.16);
  color: #c2c8ff;
  font-size: 12px;
  font-weight: 700;
}

.spinner {
  width: 34px;
  height: 34px;
  margin: 0 auto;
  border: 3px solid rgba(255, 255, 255, 0.12);
  border-top-color: var(--discord-brand);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.composer {
  display: grid;
  gap: 10px;
  padding: 0 16px 24px;
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
  background: rgba(255, 255, 255, 0.06);
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
  background: rgba(255, 255, 255, 0.08);
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.pending-file-remove:hover {
  background: rgba(255, 255, 255, 0.14);
  color: var(--discord-text);
}

.composer-input {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  padding: 12px 16px;
  border-radius: 16px;
  background: #383a40;
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
  background: rgba(255, 255, 255, 0.08);
  color: var(--discord-text);
  font-size: 20px;
  line-height: 1;
}

.attach-button:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.14);
}

.attach-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.composer-textarea {
  flex: 1;
  min-height: 24px;
  max-height: 180px;
  border: 0;
  background: transparent;
  color: var(--discord-text);
  line-height: 1.45;
  resize: none;
}

.composer-textarea:focus {
  outline: none;
}

.send-button {
  padding: 10px 16px;
  border-radius: 10px;
  background: var(--discord-brand);
  color: white;
  font-weight: 800;
}

.send-button:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

.send-button:disabled {
  opacity: 0.64;
  cursor: not-allowed;
}

.composer-error {
  margin: 0;
  color: #ff8b8d;
  font-size: 13px;
}

.composer-hint {
  margin: 0;
  color: var(--discord-text-faint);
  font-size: 12px;
}
</style>
