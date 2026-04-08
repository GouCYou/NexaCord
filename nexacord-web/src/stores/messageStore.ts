import { defineStore } from 'pinia';
import { ref } from 'vue';
import type { Attachment, Message } from '../types';
import messageService from '../services/messageService';
import websocketService from '../services/websocketService';

export type MessageAttachmentInput = Pick<
  Attachment,
  'fileName' | 'fileType' | 'fileSize' | 'url'
>;

const sortMessages = (items: Message[]) =>
  [...items].sort(
    (left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
  );

const normalizeMessage = (message: Message): Message => ({
  ...message,
  edited: message.edited ?? false,
  deleted: message.deleted ?? false,
  attachments: Array.isArray(message.attachments) ? message.attachments : [],
});

export const useMessageStore = defineStore('message', () => {
  const messages = ref<Record<number, Message[]>>({});
  const currentChannelMessages = ref<Message[]>([]);
  const activeChannelId = ref<number | null>(null);
  const realtimeInitialized = ref(false);
  const isLoading = ref(false);
  const error = ref<string | null>(null);
  const isSending = ref(false);

  const syncCurrentChannelMessages = () => {
    if (activeChannelId.value == null) {
      currentChannelMessages.value = [];
      return;
    }

    currentChannelMessages.value = [...(messages.value[activeChannelId.value] || [])];
  };

  const setChannelMessages = (channelId: number, channelMessages: Message[]) => {
    messages.value[channelId] = sortMessages(channelMessages.map(normalizeMessage));
    if (activeChannelId.value === channelId) {
      syncCurrentChannelMessages();
    }
  };

  const upsertMessage = (message: Message) => {
    const normalizedMessage = normalizeMessage(message);
    const channelId = normalizedMessage.channel.id;
    const nextMessages = [...(messages.value[channelId] || [])];
    const existingIndex = nextMessages.findIndex(
      (currentMessage) => currentMessage.id === normalizedMessage.id
    );

    if (existingIndex >= 0) {
      nextMessages[existingIndex] = normalizedMessage;
    } else {
      nextMessages.push(normalizedMessage);
    }

    messages.value[channelId] = sortMessages(nextMessages);
    if (activeChannelId.value === channelId) {
      syncCurrentChannelMessages();
    }
  };

  const removeMessage = (messageId: number) => {
    Object.keys(messages.value).forEach((key) => {
      const channelId = Number(key);
      const channelMessages = messages.value[channelId] || [];
      const nextMessages = channelMessages.filter((message) => message.id !== messageId);

      if (nextMessages.length !== channelMessages.length) {
        messages.value[channelId] = nextMessages;
      }
    });

    syncCurrentChannelMessages();
  };

  const initializeRealtime = () => {
    if (realtimeInitialized.value) {
      return;
    }

    websocketService.on('message:new', (_event, payload) => {
      if (payload && typeof payload === 'object') {
        addMessage(payload as Message);
      }
    });

    websocketService.on('message:update', (_event, payload) => {
      if (payload && typeof payload === 'object') {
        upsertMessage(payload as Message);
      }
    });

    websocketService.on('message:delete', (_event, payload) => {
      if (typeof payload === 'number') {
        removeMessage(payload);
        return;
      }

      if (typeof payload === 'string' && !Number.isNaN(Number(payload))) {
        removeMessage(Number(payload));
      }
    });

    realtimeInitialized.value = true;
  };

  const fetchMessages = async (channelId: number, sort: 'asc' | 'desc' = 'asc') => {
    activeChannelId.value = channelId;
    syncCurrentChannelMessages();
    isLoading.value = true;
    error.value = null;

    try {
      const fetchedMessages = await messageService.getChannelMessages(channelId, sort);
      setChannelMessages(channelId, fetchedMessages);
      syncCurrentChannelMessages();
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '暂时无法加载该频道的消息。';
      currentChannelMessages.value = [];
    } finally {
      isLoading.value = false;
    }
  };

  const sendMessage = async (
    channelId: number,
    content: string,
    attachments: MessageAttachmentInput[] = []
  ) => {
    if (!content.trim() && attachments.length === 0) {
      return false;
    }

    isSending.value = true;
    error.value = null;

    try {
      const newMessage = await messageService.createMessage(channelId, content, attachments);
      upsertMessage(newMessage);
      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '发送消息失败。';
      return false;
    } finally {
      isSending.value = false;
    }
  };

  const updateMessage = async (messageId: number, content: string) => {
    try {
      const updatedMessage = await messageService.updateMessage(messageId, content);
      upsertMessage(updatedMessage);
      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '更新消息失败。';
      return false;
    }
  };

  const deleteMessage = async (messageId: number) => {
    try {
      await messageService.deleteMessage(messageId);
      removeMessage(messageId);
      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '删除消息失败。';
      return false;
    }
  };

  const addMessage = (message: Message) => {
    if (message.deleted) {
      removeMessage(message.id);
      return;
    }

    upsertMessage(message);
  };

  const setCurrentChannelMessages = (channelId: number) => {
    activeChannelId.value = channelId;
    syncCurrentChannelMessages();
  };

  initializeRealtime();

  return {
    messages,
    currentChannelMessages,
    isLoading,
    isSending,
    error,
    fetchMessages,
    sendMessage,
    updateMessage,
    deleteMessage,
    addMessage,
    setCurrentChannelMessages,
    initializeRealtime,
  };
});
