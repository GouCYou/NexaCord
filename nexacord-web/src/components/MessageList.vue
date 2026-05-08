<template>
  <section class="message-layout">
    <header class="message-header">
      <div class="message-header-meta">
        <span class="message-header-prefix">
          <Volume2 v-if="currentChannel?.type === 'VOICE'" :size="24" aria-hidden="true" />
          <Hash v-else :size="24" aria-hidden="true" />
        </span>
        <div class="message-header-copy">
          <h2>{{ currentChannel?.name || '频道' }}</h2>
          <p>{{ headerSubtitle }}</p>
        </div>
      </div>

      <div class="message-header-status">
        <span>{{ currentChannel?.type === 'VOICE' ? '语音频道' : '文字频道' }}</span>
        <button class="header-tool" type="button" title="显示或隐藏成员列表" @click="toggleMemberSidebar">
          <Users :size="18" aria-hidden="true" />
        </button>
      </div>
    </header>

    <VoiceChannelPanel
      v-if="currentChannel?.type === 'VOICE' && currentChannelId"
      :channel-id="currentChannelId"
      :channel-name="currentChannel.name"
    />

    <div
      v-else
      ref="messagesContainer"
      class="messages-scroll"
      :class="{ 'is-empty-channel': currentChannel && currentChannelMessages.length === 0 && !isLoading }"
    >
      <div v-if="isLoading && currentChannelMessages.length === 0" class="status-block">
        <div class="spinner"></div>
        <p>正在加载消息……</p>
      </div>

      <div v-else-if="!currentChannel" class="status-block">
        <strong>请选择一个频道</strong>
        <p>从左侧频道列表中选中一个频道后，这里会加载对应的聊天内容。</p>
      </div>

      <div v-else-if="currentChannelMessages.length === 0" class="channel-welcome">
        <div class="welcome-icon">
          <Hash :size="52" aria-hidden="true" />
        </div>
        <h1>欢迎来到 #{{ currentChannel.name }}!</h1>
        <p>
          这是 #{{ currentChannel.name }} 频道的起点。{{ headerSubtitle }}
        </p>
      </div>

      <div v-else class="messages-list">
        <article
          v-for="message in currentChannelMessages"
          :key="message.id"
          class="message-row"
          :class="{ own: message.author.id === currentUser?.id }"
        >
          <button class="avatar" type="button" @click.stop="openUserPopover(message.author, $event)">
            <AvatarImage :src="message.author.avatarUrl || defaultAvatarUrl" :alt="displayUserName(message.author)" />
          </button>

          <div class="message-body">
            <header class="message-meta">
              <button class="author-button" type="button" @click.stop="openUserPopover(message.author, $event)">
                {{ displayUserName(message.author) }}
              </button>
              <span>{{ formatTime(message.createdAt) }}</span>
              <em v-if="message.edited">(已编辑)</em>
            </header>

            <form v-if="editingMessageId === message.id" class="message-edit" @submit.prevent="saveMessageEdit(message.id)">
              <textarea
                ref="editTextarea"
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

            <div v-else-if="message.content" class="message-content">{{ message.content }}</div>

            <div v-if="(message.attachments?.length ?? 0) > 0" class="attachments">
              <ImageAttachment
                v-for="attachment in imageAttachments(message.attachments || [])"
                :key="attachment.id"
                :source-url="attachment.url"
                :alt="attachment.fileName"
                @preview="openImagePreview"
              />
              <a
                v-for="attachment in legacyFileAttachments(message.attachments || [])"
                :key="`file-${attachment.id}`"
                class="attachment"
                :href="attachment.url"
                target="_blank"
                rel="noopener noreferrer"
              >
                <span class="attachment-icon">
                  <FileIcon :size="18" aria-hidden="true" />
                </span>
                <span class="attachment-copy">
                  <strong>{{ attachment.fileName }}</strong>
                  <small>{{ formatFileSize(attachment.fileSize) }}</small>
                </span>
              </a>
            </div>
          </div>

          <div v-if="message.author.id === currentUser?.id" class="message-actions">
            <button type="button" title="编辑消息" @click="startMessageEdit(message)">
              <Pencil :size="15" aria-hidden="true" />
            </button>
            <button type="button" title="删除消息" @click="deleteMessage(message.id)">
              <Trash2 :size="15" aria-hidden="true" />
            </button>
          </div>
        </article>
      </div>
    </div>

    <footer v-if="currentChannel?.type !== 'VOICE'" class="composer">
      <div v-if="pendingFiles.length > 0" class="pending-files">
        <div
          v-for="(file, index) in pendingFiles"
          :key="`${file.name}-${file.size}-${index}`"
          class="pending-file"
        >
          <img v-if="isImageFile(file)" class="pending-image" :src="filePreviewUrl(file)" :alt="file.name" />
          <span class="pending-file-copy">
            <strong>{{ file.name }}</strong>
            <small>{{ formatFileSize(file.size) }}</small>
          </span>
          <button
            type="button"
            class="pending-file-remove"
            aria-label="移除附件"
            @click="removePendingFile(index)"
          >
            <X :size="16" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div v-if="showEmojiPicker" class="emoji-picker" @click.stop>
        <label class="emoji-search">
          <input v-model="emojiSearch" type="text" placeholder="搜索表情" />
        </label>
        <div class="emoji-scroll">
          <section v-for="group in filteredEmojiGroups" :key="group.name" class="emoji-group">
            <strong>{{ group.name }}</strong>
            <div class="emoji-grid">
              <button
                v-for="emoji in group.emojis"
                :key="emoji"
                type="button"
                :title="emoji"
                @click="insertEmoji(emoji)"
              >
                {{ emoji }}
              </button>
            </div>
          </section>
        </div>
      </div>

      <div class="composer-input" @click.stop>
        <input ref="fileInput" class="visually-hidden" type="file" accept="image/*" multiple @change="handleFileSelection" />

        <button
          class="attach-button"
          type="button"
          title="添加附件"
          :disabled="isUploading || !currentChannelId"
          @click="openFilePicker"
        >
          <Paperclip :size="19" aria-hidden="true" />
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
          class="emoji-button"
          type="button"
          title="选择表情"
          :disabled="!currentChannelId"
          @click="toggleEmojiPicker"
        >
          <Smile :size="20" aria-hidden="true" />
        </button>

        <button
          class="send-button"
          type="button"
          :disabled="isSending || isUploading || !canSend"
          @click="sendMessage"
        >
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
import { useRoute } from 'vue-router';
import { storeToRefs } from 'pinia';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { FileIcon, Hash, Paperclip, Pencil, Send, Smile, Trash2, Users, Volume2, X } from 'lucide-vue-next';
import ImageAttachment from './ImageAttachment.vue';
import ImagePreviewModal from './ImagePreviewModal.vue';
import VoiceChannelPanel from './VoiceChannelPanel.vue';
import fileService from '../services/fileService';
import { useChannelStore } from '../stores/channelStore';
import { useMessageStore, type MessageAttachmentInput } from '../stores/messageStore';
import { useUserStore } from '../stores/userStore';
import { useServerStore } from '../stores/serverStore';
import type { Attachment, Message, User } from '../types';
import { displayUserLabel } from '../utils/userDisplay';

dayjs.locale('zh-cn');

const route = useRoute();
const channelStore = useChannelStore();
const messageStore = useMessageStore();
const userStore = useUserStore();
const serverStore = useServerStore();
const defaultAvatarUrl = '/logo.png';

const messagesContainer = ref<HTMLElement | null>(null);
const composerTextarea = ref<HTMLTextAreaElement | null>(null);
const editTextarea = ref<HTMLTextAreaElement | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const newMessageContent = ref('');
const editingMessageId = ref<number | null>(null);
const editingContent = ref('');
const previewImageUrl = ref<string | null>(null);
const pendingFiles = ref<File[]>([]);
const isUploading = ref(false);
const localError = ref<string | null>(null);
const showEmojiPicker = ref(false);
const emojiSearch = ref('');
const previewUrls = new Map<File, string>();
const emojiGroups = [
  {
    name: '常用',
    emojis: ['😀', '😄', '😂', '🤣', '😊', '😍', '😘', '😎', '😭', '😡', '👍', '👎', '👏', '🙏', '💪', '🔥', '✨', '🎉', '❤️', '💙', '💜', '💯', '✅', '❌'],
  },
  {
    name: '表情',
    emojis: ['🙂', '🙃', '😉', '😌', '🥰', '😋', '🤔', '🤨', '😐', '😑', '😶', '😴', '🤤', '😵', '🤯', '🥳', '😇', '🤠', '🤓', '🫡', '🫠', '🥹', '😤', '😱'],
  },
  {
    name: '手势',
    emojis: ['👋', '🤚', '✋', '👌', '🤌', '🤏', '✌️', '🤞', '🤟', '🤘', '👈', '👉', '👆', '👇', '☝️', '✍️', '🤝', '🫶', '🙌', '👐'],
  },
  {
    name: '物品',
    emojis: ['🎮', '🎧', '🎤', '📷', '💻', '⌨️', '🖱️', '📱', '📌', '📎', '🧭', '⏰', '🧪', '🛠️', '💡', '🔒', '🔑', '🎁', '🏆', '🚀'],
  },
  {
    name: '自然',
    emojis: ['☀️', '🌙', '⭐', '🌈', '⚡', '❄️', '🌊', '🌸', '🌵', '🍀', '🍎', '🍔', '🍕', '🍜', '🍰', '☕', '🍵', '🥤', '🍺', '🍬'],
  },
];

const { currentChannel, currentChannelId } = storeToRefs(channelStore);
const { currentChannelMessages, isLoading, isSending, error } = storeToRefs(messageStore);
const { currentUser } = storeToRefs(userStore);
const { currentServer } = storeToRefs(serverStore);

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
    return '上传中……';
  }

  if (isSending.value) {
    return '发送中……';
  }

  return '发送';
});

const composerError = computed(() => localError.value || error.value);

const displayUserName = (user: { username: string; displayName?: string | null }) =>
  displayUserLabel(user);

const openUserPopover = (user: User, event: MouseEvent) => {
  window.dispatchEvent(new CustomEvent('nexacord:open-user-popover', {
    detail: {
      user,
      serverName: currentServer.value?.name,
      serverIconUrl: currentServer.value?.iconUrl,
      x: event.clientX,
      y: event.clientY,
    },
  }));
};

const toggleMemberSidebar = () => {
  window.dispatchEvent(new CustomEvent('nexacord:toggle-member-sidebar'));
};

const filteredEmojiGroups = computed(() => {
  const query = emojiSearch.value.trim();
  if (!query) {
    return emojiGroups;
  }

  return emojiGroups
    .map((group) => ({
      ...group,
      emojis: group.emojis.filter((emoji) => emoji.includes(query)),
    }))
    .filter((group) => group.emojis.length > 0);
});

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

const isImageAttachment = (attachment: Attachment) =>
  attachment.fileType?.startsWith('image/') || /\.(png|jpe?g|gif|webp|avif|svg)$/i.test(attachment.url);

const imageAttachments = (attachments: Attachment[]) => attachments.filter(isImageAttachment);

const legacyFileAttachments = (attachments: Attachment[]) => attachments.filter((attachment) => !isImageAttachment(attachment));

const isImageFile = (file: File) => file.type.startsWith('image/');

const openImagePreview = (url: string) => {
  previewImageUrl.value = url;
};

const closeImagePreview = () => {
  previewImageUrl.value = null;
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
  if (!url) {
    return;
  }

  URL.revokeObjectURL(url);
  previewUrls.delete(file);
};

const revokeAllFilePreviews = () => {
  previewUrls.forEach((url) => URL.revokeObjectURL(url));
  previewUrls.clear();
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

const toggleEmojiPicker = () => {
  showEmojiPicker.value = !showEmojiPicker.value;
};

const closeEmojiPicker = () => {
  showEmojiPicker.value = false;
};

const insertEmoji = (emoji: string) => {
  const textarea = composerTextarea.value;
  const start = textarea?.selectionStart ?? newMessageContent.value.length;
  const end = textarea?.selectionEnd ?? newMessageContent.value.length;

  newMessageContent.value =
    `${newMessageContent.value.substring(0, start)}${emoji}${newMessageContent.value.substring(end)}`;
  showEmojiPicker.value = false;

  nextTick(() => {
    if (textarea) {
      textarea.focus();
      textarea.selectionStart = textarea.selectionEnd = start + emoji.length;
    }
    autoResizeComposer();
  });
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

  const nextFiles = selectedFiles.filter((file) => {
    const key = `${file.name}-${file.size}-${file.lastModified}`;
    return !existingKeys.has(key);
  });

  pendingFiles.value = [...pendingFiles.value, ...nextFiles];
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
      const uploadableFile = await fileService.uploadFile(file);
      uploadedAttachments.push({
        fileName: file.name,
        fileType: file.type || 'image/*',
        fileSize: file.size,
        url: uploadableFile,
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

const startMessageEdit = (message: Message) => {
  editingMessageId.value = message.id;
  editingContent.value = message.content;
  nextTick(() => {
    editTextarea.value?.focus();
  });
};

const cancelMessageEdit = () => {
  editingMessageId.value = null;
  editingContent.value = '';
};

const saveMessageEdit = async (messageId: number) => {
  const content = editingContent.value.trim();
  if (!content) {
    return;
  }

  const success = await messageStore.updateMessage(messageId, content);
  if (success) {
    cancelMessageEdit();
  }
};

const deleteMessage = async (messageId: number) => {
  await messageStore.deleteMessage(messageId);
};

const resetComposer = () => {
  newMessageContent.value = '';
  revokeAllFilePreviews();
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
    showEmojiPicker.value = false;
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
  window.addEventListener('click', closeEmojiPicker);
});

onBeforeUnmount(() => {
  revokeAllFilePreviews();
  window.removeEventListener('click', closeEmojiPicker);
});
</script>

<style scoped>
.message-layout {
  display: grid;
  grid-template-rows: auto 1fr auto;
  height: 100%;
  min-height: 0;
  background: var(--discord-bg);
}

.message-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--discord-border);
  background: color-mix(in srgb, var(--discord-bg) 95%, transparent);
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
  background: var(--discord-muted-surface);
}

.header-tool {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--discord-text-faint);
}

.header-tool:hover {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.messages-scroll {
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0 20px;
}

.messages-scroll.is-empty-channel {
  display: grid;
  align-items: end;
  padding: 0 26px 44px;
}

.messages-list {
  display: grid;
}

.message-row {
  position: relative;
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 14px;
  padding: 8px 18px;
  transition: background-color 140ms ease;
}

.message-row:hover {
  background: var(--discord-subtle);
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

.avatar:hover {
  filter: brightness(1.06);
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

.author-button {
  min-width: 0;
  padding: 0;
  background: transparent;
  color: var(--discord-text);
  font-size: 15px;
  font-weight: 900;
}

.author-button:hover {
  text-decoration: underline;
}

.message-meta span,
.message-meta em {
  color: var(--discord-text-faint);
  font-size: 12px;
  font-style: italic;
}

.message-content {
  margin-top: 4px;
  color: var(--discord-text);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
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
  line-height: 1.45;
}

.message-edit-actions {
  display: flex;
  align-items: center;
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
  max-width: min(420px, 100%);
  min-width: 220px;
  padding: 12px 14px;
  border-radius: 14px;
  background: var(--discord-muted-surface);
  color: var(--discord-text);
}

.attachment.image {
  display: grid;
  align-items: start;
  padding: 8px;
}

.attachment:hover {
  background: var(--discord-pressed);
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

.attachment-image {
  width: 100%;
  max-height: 320px;
  border-radius: 10px;
  object-fit: cover;
  background: var(--discord-input);
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

.channel-welcome {
  max-width: 720px;
  display: grid;
  gap: 12px;
  text-align: left;
}

.welcome-icon {
  width: 78px;
  height: 78px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-muted-surface);
  color: var(--discord-text);
}

.channel-welcome h1 {
  margin: 0;
  color: var(--discord-text);
  font-size: clamp(30px, 4vw, 44px);
  line-height: 1.08;
}

.channel-welcome p {
  max-width: 620px;
  margin: 0;
  color: var(--discord-text-muted);
  font-size: 17px;
  line-height: 1.55;
}

.spinner {
  width: 34px;
  height: 34px;
  margin: 0 auto;
  border: 3px solid var(--discord-strong-border);
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
  position: relative;
  display: grid;
  gap: 10px;
  padding: 0 16px 14px;
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
  font-size: 12px;
  font-weight: 800;
  line-height: 1;
}

.pending-file-remove:hover {
  background: var(--discord-hover-strong);
  color: var(--discord-text);
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
  background: var(--discord-hover);
  color: var(--discord-text);
  font-size: 20px;
  line-height: 1;
}

.emoji-button {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--discord-text-faint);
}

.emoji-button:hover:not(:disabled) {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.emoji-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.attach-button:hover:not(:disabled) {
  background: var(--discord-hover-strong);
}

.attach-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.composer-textarea {
  flex: 1;
  min-height: 28px;
  max-height: 180px;
  border: 0;
  padding: 6px 0;
  background: transparent;
  color: var(--discord-text);
  line-height: 1.45;
  resize: none;
}

.composer-textarea:focus {
  outline: none;
}

.send-button {
  display: inline-flex;
  align-items: center;
  gap: 7px;
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

.emoji-picker {
  position: absolute;
  right: 22px;
  bottom: calc(100% + 10px);
  width: min(380px, calc(100vw - 44px));
  max-height: 420px;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  border: 1px solid var(--discord-border);
  border-radius: 14px;
  background: var(--discord-elevated);
  box-shadow: var(--discord-shadow);
  overflow: hidden;
  z-index: 35;
  animation: emoji-in 120ms ease-out;
}

.emoji-search {
  padding: 12px;
  border-bottom: 1px solid var(--discord-border);
}

.emoji-search input {
  width: 100%;
  height: 38px;
  border: 0;
  border-radius: 8px;
  padding: 0 12px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.emoji-scroll {
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
}

.emoji-group + .emoji-group {
  margin-top: 14px;
}

.emoji-group strong {
  display: block;
  margin-bottom: 8px;
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 900;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
}

.emoji-grid button {
  aspect-ratio: 1;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: transparent;
  font-size: 22px;
}

.emoji-grid button:hover {
  background: var(--discord-hover);
}

@keyframes emoji-in {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
