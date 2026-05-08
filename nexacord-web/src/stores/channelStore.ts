import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { Channel } from '../types';
import channelService from '../services/channelService';
import { useUnreadStore } from './unreadStore';

const normalizeChannel = (channel: Partial<Channel>): Channel => ({
  id: Number(channel.id),
  name: channel.name?.trim() || '未命名频道',
  type: channel.type || 'TEXT',
  topic: channel.topic || '',
  nsfw: channel.nsfw ?? false,
  parentId: channel.parentId ?? null,
  createdAt: channel.createdAt || '',
  updatedAt: channel.updatedAt || '',
  server: channel.server,
});

export const useChannelStore = defineStore('channel', () => {
  const unreadStore = useUnreadStore();
  const channels = ref<Channel[]>([]);
  const currentChannelId = ref<number | null>(null);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  const currentChannel = computed(() => {
    if (currentChannelId.value == null) {
      return null;
    }

    return channels.value.find((channel) => channel.id === currentChannelId.value) || null;
  });

  const resolveCurrentChannel = (preferredChannelId?: number) => {
    if (preferredChannelId && channels.value.some((channel) => channel.id === preferredChannelId)) {
      currentChannelId.value = preferredChannelId;
      return;
    }

    if (
      currentChannelId.value &&
      channels.value.some((channel) => channel.id === currentChannelId.value)
    ) {
      return;
    }

    const firstTextChannel = channels.value.find((channel) => channel.type === 'TEXT');
    currentChannelId.value = firstTextChannel?.id ?? channels.value[0]?.id ?? null;
  };

  const fetchChannels = async (serverId: number, preferredChannelId?: number) => {
    if (!serverId || Number.isNaN(serverId)) {
      return [];
    }

    isLoading.value = true;
    error.value = null;

    try {
      const fetchedChannels = await channelService.getServerChannels(serverId);
      channels.value = Array.isArray(fetchedChannels) ? fetchedChannels.map(normalizeChannel) : [];
      unreadStore.registerChannels(channels.value, serverId);
      resolveCurrentChannel(preferredChannelId);
      return channels.value;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '暂时无法加载该服务器的频道列表。';
      channels.value = [];
      currentChannelId.value = null;
      return [];
    } finally {
      isLoading.value = false;
    }
  };

  const fetchChannelById = async (channelId: number) => {
    isLoading.value = true;
    error.value = null;

    try {
      const fetchedChannel = normalizeChannel(await channelService.getChannelById(channelId));
      const index = channels.value.findIndex((channel) => channel.id === channelId);

      if (index >= 0) {
        channels.value[index] = fetchedChannel;
      } else {
        channels.value.push(fetchedChannel);
      }

      currentChannelId.value = channelId;
      return fetchedChannel;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '无法加载该频道。';
      return null;
    } finally {
      isLoading.value = false;
    }
  };

  const createChannel = async (
    serverId: number,
    channelData: Pick<Channel, 'name' | 'type' | 'topic' | 'nsfw' | 'parentId'>
  ) => {
    isLoading.value = true;
    error.value = null;

    try {
      const createdChannel = normalizeChannel(await channelService.createChannel(serverId, channelData));
      channels.value.push(createdChannel);
      currentChannelId.value = createdChannel.id;
      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '创建频道失败。';
      return false;
    } finally {
      isLoading.value = false;
    }
  };

  const updateChannel = async (channelId: number, channelData: Partial<Channel>) => {
    isLoading.value = true;
    error.value = null;

    try {
      const updatedChannel = normalizeChannel(await channelService.updateChannel(channelId, channelData));
      const index = channels.value.findIndex((channel) => channel.id === channelId);

      if (index >= 0) {
        channels.value[index] = updatedChannel;
      }

      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '更新频道失败。';
      return false;
    } finally {
      isLoading.value = false;
    }
  };

  const deleteChannel = async (channelId: number) => {
    isLoading.value = true;
    error.value = null;

    try {
      await channelService.deleteChannel(channelId);
      channels.value = channels.value.filter((channel) => channel.id !== channelId);

      if (currentChannelId.value === channelId) {
        resolveCurrentChannel();
      }

      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '删除频道失败。';
      return false;
    } finally {
      isLoading.value = false;
    }
  };

  const setCurrentChannel = (channelId: number | null) => {
    currentChannelId.value = channelId;
  };

  const clearChannels = () => {
    channels.value = [];
    currentChannelId.value = null;
  };

  return {
    channels,
    currentChannelId,
    currentChannel,
    isLoading,
    error,
    fetchChannels,
    fetchChannelById,
    createChannel,
    updateChannel,
    deleteChannel,
    setCurrentChannel,
    clearChannels,
  };
});
