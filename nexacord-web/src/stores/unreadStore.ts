import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { Channel, DirectConversation, DirectMessage, Message } from '../types';

const readKey = (userId: number, scope: 'channels' | 'direct') => `nexacord:read:${scope}:${userId}`;
const unreadKey = (userId: number) => `nexacord:unread:${userId}`;

const readRecord = (key: string): Record<number, number> => {
  try {
    const value = JSON.parse(localStorage.getItem(key) || '{}') as Record<string, number>;
    return Object.fromEntries(
      Object.entries(value)
        .map(([id, marker]) => [Number(id), Number(marker)])
        .filter(([id, marker]) => Number.isFinite(id) && Number.isFinite(marker))
    );
  } catch {
    return {};
  }
};

const writeRecord = (key: string, value: Record<number, number>) => {
  localStorage.setItem(key, JSON.stringify(value));
};

const readUnreadState = (userId: number) => {
  try {
    const parsed = JSON.parse(localStorage.getItem(unreadKey(userId)) || '{}') as {
      channels?: Record<string, boolean>;
      direct?: Record<string, boolean>;
    };
    return {
      channels: Object.fromEntries(
        Object.keys(parsed.channels || {}).map((channelId) => [Number(channelId), true])
      ) as Record<number, true>,
      direct: Object.fromEntries(
        Object.keys(parsed.direct || {}).map((conversationId) => [Number(conversationId), true])
      ) as Record<number, true>,
    };
  } catch {
    return { channels: {}, direct: {} };
  }
};

export const useUnreadStore = defineStore('unread', () => {
  const currentUserId = ref<number | null>(null);
  const unreadChannels = ref<Record<number, true>>({});
  const unreadDirects = ref<Record<number, true>>({});
  const channelServerIds = ref<Record<number, number>>({});
  const latestChannelMarkers = ref<Record<number, number>>({});
  const latestDirectMarkers = ref<Record<number, number>>({});

  const unreadServerIds = computed(() => {
    const serverIds = new Set<number>();
    Object.keys(unreadChannels.value).forEach((channelId) => {
      const serverId = channelServerIds.value[Number(channelId)];
      if (serverId) {
        serverIds.add(serverId);
      }
    });
    return serverIds;
  });

  const persistUnread = () => {
    if (!currentUserId.value) {
      return;
    }

    localStorage.setItem(unreadKey(currentUserId.value), JSON.stringify({
      channels: unreadChannels.value,
      direct: unreadDirects.value,
    }));
  };

  const initializeForUser = (userId: number | null | undefined) => {
    if (!userId || currentUserId.value === userId) {
      return;
    }

    currentUserId.value = userId;
    const state = readUnreadState(userId);
    unreadChannels.value = state.channels;
    unreadDirects.value = state.direct;
    latestChannelMarkers.value = {};
    latestDirectMarkers.value = {};
  };

  const registerChannels = (channels: Channel[], fallbackServerId?: number | null) => {
    const nextMap = { ...channelServerIds.value };
    channels.forEach((channel) => {
      const serverId = channel.server?.id ?? fallbackServerId ?? null;
      if (channel.id && serverId) {
        nextMap[channel.id] = serverId;
      }
    });
    channelServerIds.value = nextMap;
  };

  const markChannelUnread = (message: Message, activeChannelId?: number | null, currentUserIdOverride?: number | null) => {
    const userId = currentUserIdOverride ?? currentUserId.value;
    if (!userId || message.author?.id === userId || message.deleted) {
      return;
    }

    const channelId = message.channel?.id;
    if (!channelId) {
      return;
    }

    latestChannelMarkers.value = {
      ...latestChannelMarkers.value,
      [channelId]: Math.max(latestChannelMarkers.value[channelId] || 0, message.id),
    };

    const serverId = message.channel.server?.id;
    if (serverId) {
      channelServerIds.value = {
        ...channelServerIds.value,
        [channelId]: serverId,
      };
    }

    if (activeChannelId === channelId) {
      markChannelRead(channelId, message.id);
      return;
    }

    unreadChannels.value = {
      ...unreadChannels.value,
      [channelId]: true,
    };
    persistUnread();
  };

  const markDirectUnread = (
    message: DirectMessage,
    activeConversationId?: number | null,
    currentUserIdOverride?: number | null
  ) => {
    const userId = currentUserIdOverride ?? currentUserId.value;
    if (!userId || message.author?.id === userId || message.deleted) {
      return;
    }

    latestDirectMarkers.value = {
      ...latestDirectMarkers.value,
      [message.conversationId]: Math.max(latestDirectMarkers.value[message.conversationId] || 0, message.id),
    };

    if (activeConversationId === message.conversationId) {
      markDirectRead(message.conversationId, message.id);
      return;
    }

    unreadDirects.value = {
      ...unreadDirects.value,
      [message.conversationId]: true,
    };
    persistUnread();
  };

  const syncDirectConversations = (conversations: DirectConversation[], userId: number | null | undefined) => {
    if (!userId) {
      return;
    }

    initializeForUser(userId);
    const readMarkers = readRecord(readKey(userId, 'direct'));
    let nextUnread = { ...unreadDirects.value };
    let changed = false;

    conversations.forEach((conversation) => {
      const message = conversation.lastMessage;
      if (!message || message.author.id === userId || message.deleted) {
        return;
      }

      latestDirectMarkers.value = {
        ...latestDirectMarkers.value,
        [conversation.id]: Math.max(latestDirectMarkers.value[conversation.id] || 0, message.id),
      };

      if ((readMarkers[conversation.id] || 0) < message.id) {
        nextUnread = { ...nextUnread, [conversation.id]: true };
        changed = true;
      }
    });

    if (changed) {
      unreadDirects.value = nextUnread;
      persistUnread();
    }
  };

  const markChannelRead = (channelId: number, marker?: number) => {
    const userId = currentUserId.value;
    if (!userId) {
      return;
    }

    const nextUnread = { ...unreadChannels.value };
    delete nextUnread[channelId];
    unreadChannels.value = nextUnread;

    const markers = readRecord(readKey(userId, 'channels'));
    markers[channelId] = Math.max(markers[channelId] || 0, marker || latestChannelMarkers.value[channelId] || 0);
    writeRecord(readKey(userId, 'channels'), markers);
    persistUnread();
  };

  const markDirectRead = (conversationId: number, marker?: number) => {
    const userId = currentUserId.value;
    if (!userId) {
      return;
    }

    const nextUnread = { ...unreadDirects.value };
    delete nextUnread[conversationId];
    unreadDirects.value = nextUnread;

    const markers = readRecord(readKey(userId, 'direct'));
    markers[conversationId] = Math.max(
      markers[conversationId] || 0,
      marker || latestDirectMarkers.value[conversationId] || 0
    );
    writeRecord(readKey(userId, 'direct'), markers);
    persistUnread();
  };

  const hasChannelUnread = (channelId: number) => Boolean(unreadChannels.value[channelId]);
  const hasDirectUnread = (conversationId: number) => Boolean(unreadDirects.value[conversationId]);
  const hasServerUnread = (serverId: number) => unreadServerIds.value.has(serverId);
  const hasHomeUnread = computed(() => Object.keys(unreadDirects.value).length > 0);

  const reset = () => {
    currentUserId.value = null;
    unreadChannels.value = {};
    unreadDirects.value = {};
    latestChannelMarkers.value = {};
    latestDirectMarkers.value = {};
  };

  return {
    unreadChannels,
    unreadDirects,
    unreadServerIds,
    hasHomeUnread,
    initializeForUser,
    registerChannels,
    markChannelUnread,
    markDirectUnread,
    syncDirectConversations,
    markChannelRead,
    markDirectRead,
    hasChannelUnread,
    hasDirectUnread,
    hasServerUnread,
    reset,
  };
});
