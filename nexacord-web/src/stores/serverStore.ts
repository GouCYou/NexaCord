import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { Server } from '../types';
import serverService from '../services/serverService';

const normalizeServer = (server: Partial<Server>): Server => ({
  id: Number(server.id),
  name: server.name?.trim() || '未命名服务器',
  description: server.description || '',
  iconUrl: server.iconUrl || null,
  bannerUrl: server.bannerUrl || null,
  createdAt: server.createdAt || '',
  updatedAt: server.updatedAt || '',
  ownerId: server.ownerId,
});

export const useServerStore = defineStore('server', () => {
  const servers = ref<Server[]>([]);
  const currentServerId = ref<number | null>(null);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  const currentServer = computed(() => {
    if (currentServerId.value == null) {
      return null;
    }

    return servers.value.find((server) => server.id === currentServerId.value) || null;
  });

  const fetchServers = async (preferredServerId?: number) => {
    isLoading.value = true;
    error.value = null;

    try {
      const fetchedServers = await serverService.getUserServers();
      servers.value = Array.isArray(fetchedServers)
        ? fetchedServers
            .filter((server) => server && typeof server.id === 'number' && Boolean(server.name))
            .map(normalizeServer)
        : [];

      if (servers.value.length === 0) {
        currentServerId.value = null;
        return servers.value;
      }

      const nextServerId =
        preferredServerId && servers.value.some((server) => server.id === preferredServerId)
          ? preferredServerId
          : currentServerId.value && servers.value.some((server) => server.id === currentServerId.value)
            ? currentServerId.value
            : servers.value[0]?.id ?? null;

      currentServerId.value = nextServerId;
      return servers.value;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '暂时无法加载你的服务器列表。';
      servers.value = [];
      currentServerId.value = null;
      return [];
    } finally {
      isLoading.value = false;
    }
  };

  const fetchServerById = async (serverId: number) => {
    isLoading.value = true;
    error.value = null;

    try {
      const fetchedServer = normalizeServer(await serverService.getServerById(serverId));
      const index = servers.value.findIndex((server) => server.id === serverId);

      if (index >= 0) {
        servers.value[index] = fetchedServer;
      } else {
        servers.value.push(fetchedServer);
      }

      currentServerId.value = serverId;
      return fetchedServer;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '无法加载该服务器。';
      return null;
    } finally {
      isLoading.value = false;
    }
  };

  const createServer = async (serverData: Omit<Server, 'id' | 'createdAt' | 'updatedAt' | 'ownerId'>) => {
    isLoading.value = true;
    error.value = null;

    try {
      const createdServer = normalizeServer(await serverService.createServer(serverData));
      await fetchServers(createdServer.id);
      currentServerId.value = createdServer.id;
      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '创建服务器失败。';
      return false;
    } finally {
      isLoading.value = false;
    }
  };

  const updateServer = async (serverId: number, serverData: Partial<Server>) => {
    isLoading.value = true;
    error.value = null;

    try {
      const updatedServer = normalizeServer(await serverService.updateServer(serverId, serverData));
      const index = servers.value.findIndex((server) => server.id === serverId);

      if (index >= 0) {
        servers.value[index] = updatedServer;
      }

      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '更新服务器失败。';
      return false;
    } finally {
      isLoading.value = false;
    }
  };

  const deleteServer = async (serverId: number) => {
    isLoading.value = true;
    error.value = null;

    try {
      await serverService.deleteServer(serverId);
      servers.value = servers.value.filter((server) => server.id !== serverId);

      if (currentServerId.value === serverId) {
        currentServerId.value = servers.value[0]?.id ?? null;
      }

      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '删除服务器失败。';
      return false;
    } finally {
      isLoading.value = false;
    }
  };

  const setCurrentServer = (serverId: number | null) => {
    currentServerId.value = serverId;
  };

  return {
    servers,
    currentServerId,
    currentServer,
    isLoading,
    error,
    fetchServers,
    fetchServerById,
    createServer,
    updateServer,
    deleteServer,
    setCurrentServer,
  };
});
