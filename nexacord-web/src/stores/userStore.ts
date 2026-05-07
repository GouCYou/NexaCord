import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { LoginRequest, RegisterRequest, User, UserProfileUpdateRequest } from '../types';
import authService from '../services/authService';
import userService from '../services/userService';
import websocketService from '../services/websocketService';

export const useUserStore = defineStore('user', () => {
  const currentUser = ref<User | null>(null);
  const isLoading = ref(false);
  const error = ref<string | null>(null);
  let statusRealtimeInitialized = false;

  const isAuthenticated = computed(() => Boolean(currentUser.value));

  const initializeStatusRealtime = () => {
    if (statusRealtimeInitialized) {
      return;
    }

    websocketService.on('user:status:update', (_event, payload) => {
      const author = (payload as { author?: Pick<User, 'id' | 'status'> })?.author;
      const existingUser = currentUser.value;
      if (!author?.id || !existingUser || author.id !== existingUser.id || !author.status) {
        return;
      }

      currentUser.value = {
        ...existingUser,
        status: author.status,
      };
      authService.setCurrentUser(currentUser.value);
    });
    statusRealtimeInitialized = true;
  };

  const initializeUser = () => {
    const storedUser = authService.getCurrentUser();
    const token = authService.getToken();

    if (!storedUser || !token) {
      currentUser.value = null;
      return;
    }

    currentUser.value = storedUser;
    initializeStatusRealtime();
    websocketService.initialize(token);
  };

  const login = async (credentials: LoginRequest) => {
    isLoading.value = true;
    error.value = null;

    try {
      const response = await authService.login(credentials);
      currentUser.value = response.user;

      const token = authService.getToken();
      if (token) {
        initializeStatusRealtime();
        websocketService.initialize(token);
      }

      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '登录失败，请检查账号和密码后重试。';
      return false;
    } finally {
      isLoading.value = false;
    }
  };

  const refreshCurrentUser = async () => {
    try {
      const user = await userService.getCurrentUser();
      currentUser.value = user;
      authService.setCurrentUser(user);
      return user;
    } catch {
      return currentUser.value;
    }
  };

  const updateProfile = async (profile: UserProfileUpdateRequest) => {
    isLoading.value = true;
    error.value = null;

    try {
      const updatedUser = await userService.updateCurrentUser(profile);
      currentUser.value = updatedUser;
      authService.setCurrentUser(updatedUser);
      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '更新个人资料失败。';
      return false;
    } finally {
      isLoading.value = false;
    }
  };

  const register = async (credentials: RegisterRequest) => {
    isLoading.value = true;
    error.value = null;

    try {
      const response = await authService.register(credentials);
      currentUser.value = response.user;

      const token = authService.getToken();
      if (token) {
        initializeStatusRealtime();
        websocketService.initialize(token);
      }

      return true;
    } catch (err: any) {
      error.value =
        err.response?.data?.error ||
        err.response?.data?.message ||
        '注册失败，请检查填写的信息后重试。';
      return false;
    } finally {
      isLoading.value = false;
    }
  };

  const logout = () => {
    authService.logout();
    websocketService.disconnect();
    currentUser.value = null;
    error.value = null;
  };

  return {
    currentUser,
    isLoading,
    error,
    isAuthenticated,
    initializeUser,
    login,
    register,
    refreshCurrentUser,
    updateProfile,
    logout,
  };
});
