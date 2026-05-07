import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import directMessageService from '../services/directMessageService';
import websocketService from '../services/websocketService';
import { useUserStore } from './userStore';
import type { DirectConversation, DirectMessage, DirectRealtimeEvent, User } from '../types';

const sortConversations = (items: DirectConversation[]) =>
  [...items].sort(
    (left, right) =>
      new Date(right.updatedAt || right.lastMessage?.createdAt || 0).getTime() -
      new Date(left.updatedAt || left.lastMessage?.createdAt || 0).getTime()
  );

const sortMessages = (items: DirectMessage[]) =>
  [...items].sort(
    (left, right) => new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime()
  );

export const useDirectMessageStore = defineStore('directMessage', () => {
  const conversations = ref<DirectConversation[]>([]);
  const messages = ref<Record<number, DirectMessage[]>>({});
  const currentConversationId = ref<number | null>(null);
  const isLoading = ref(false);
  const isSending = ref(false);
  const error = ref<string | null>(null);

  let unsubscribeRealtime: (() => void) | null = null;
  let subscribedUserId: number | null = null;
  let realtimeInitialized = false;

  const userStore = useUserStore();

  const currentConversation = computed(() => {
    if (currentConversationId.value == null) {
      return null;
    }

    return conversations.value.find((conversation) => conversation.id === currentConversationId.value) || null;
  });

  const currentMessages = computed(() => {
    if (currentConversationId.value == null) {
      return [];
    }

    return messages.value[currentConversationId.value] || [];
  });

  const upsertConversation = (conversation: DirectConversation) => {
    const nextConversations = [...conversations.value];
    const index = nextConversations.findIndex((item) => item.id === conversation.id);

    if (index >= 0) {
      nextConversations[index] = conversation;
    } else {
      nextConversations.push(conversation);
    }

    conversations.value = sortConversations(nextConversations);
  };

  const upsertMessage = (message: DirectMessage) => {
    const conversationMessages = [...(messages.value[message.conversationId] || [])];
    const index = conversationMessages.findIndex((item) => item.id === message.id);

    if (index >= 0) {
      conversationMessages[index] = message;
    } else {
      conversationMessages.push(message);
    }

    messages.value[message.conversationId] = sortMessages(conversationMessages);
  };

  const handleRealtimeEvent = (payload: unknown) => {
    const event = payload as DirectRealtimeEvent;
    if (!event?.conversation) {
      return;
    }

    upsertConversation(event.conversation);
    if (event.message) {
      upsertMessage(event.message);
    }
  };

  const handleUserStatusUpdate = (_event: unknown, payload: unknown) => {
    const author = (payload as { author?: Pick<User, 'id' | 'status'> })?.author;
    if (!author?.id || !author.status) {
      return;
    }

    conversations.value = conversations.value.map((conversation) =>
      conversation.otherUser.id === author.id
        ? {
            ...conversation,
            otherUser: {
              ...conversation.otherUser,
              status: author.status as User['status'],
            },
          }
        : conversation
    );
  };

  const subscribeRealtime = () => {
    const userId = userStore.currentUser?.id;
    if (!userId || !websocketService.isConnected() || subscribedUserId === userId) {
      return;
    }

    unsubscribeRealtime?.();
    unsubscribeRealtime = websocketService.subscribe(`/topic/direct/user/${userId}`, handleRealtimeEvent);
    subscribedUserId = unsubscribeRealtime ? userId : null;
  };

  const initializeRealtime = () => {
    if (realtimeInitialized) {
      subscribeRealtime();
      return;
    }

    websocketService.on('connect', subscribeRealtime);
    websocketService.on('disconnect', () => {
      unsubscribeRealtime?.();
      unsubscribeRealtime = null;
      subscribedUserId = null;
    });
    websocketService.on('user:status:update', handleUserStatusUpdate);
    realtimeInitialized = true;
    subscribeRealtime();
  };

  const fetchConversations = async () => {
    isLoading.value = true;
    error.value = null;

    try {
      conversations.value = sortConversations(await directMessageService.getConversations());
      return conversations.value;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '暂时无法加载私信列表。';
      return [];
    } finally {
      isLoading.value = false;
    }
  };

  const fetchConversation = async (conversationId: number) => {
    error.value = null;

    try {
      const conversation = await directMessageService.getConversation(conversationId);
      upsertConversation(conversation);
      currentConversationId.value = conversationId;
      return conversation;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '无法打开这条私信会话。';
      return null;
    }
  };

  const openConversationWithUser = async (user: Pick<User, 'id'>) => {
    isLoading.value = true;
    error.value = null;

    try {
      const conversation = await directMessageService.createConversation(user.id);
      upsertConversation(conversation);
      currentConversationId.value = conversation.id;
      return conversation;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '暂时无法开启私信。';
      return null;
    } finally {
      isLoading.value = false;
    }
  };

  const fetchMessages = async (conversationId: number) => {
    currentConversationId.value = conversationId;
    isLoading.value = true;
    error.value = null;

    try {
      const fetchedMessages = await directMessageService.getMessages(conversationId, 'asc');
      messages.value[conversationId] = sortMessages(fetchedMessages);
      return messages.value[conversationId];
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '暂时无法加载私信消息。';
      messages.value[conversationId] = [];
      return [];
    } finally {
      isLoading.value = false;
    }
  };

  const sendMessage = async (conversationId: number, content: string) => {
    if (!content.trim()) {
      return false;
    }

    isSending.value = true;
    error.value = null;

    try {
      const message = await directMessageService.sendMessage(conversationId, content.trim());
      upsertMessage(message);
      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '私信发送失败。';
      return false;
    } finally {
      isSending.value = false;
    }
  };

  const clearCurrentConversation = () => {
    currentConversationId.value = null;
  };

  return {
    conversations,
    currentConversationId,
    currentConversation,
    currentMessages,
    isLoading,
    isSending,
    error,
    initializeRealtime,
    fetchConversations,
    fetchConversation,
    openConversationWithUser,
    fetchMessages,
    sendMessage,
    clearCurrentConversation,
  };
});
