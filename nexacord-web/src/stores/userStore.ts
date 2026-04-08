import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import type { LoginRequest, RegisterRequest, User } from '../types';
import authService from '../services/authService';
import websocketService from '../services/websocketService';

export const useUserStore = defineStore('user', () => {
  const currentUser = ref<User | null>(null);
  const isLoading = ref(false);
  const error = ref<string | null>(null);

  const isAuthenticated = computed(() => Boolean(currentUser.value));

  const initializeUser = () => {
    const storedUser = authService.getCurrentUser();
    const token = authService.getToken();

    if (!storedUser || !token) {
      currentUser.value = null;
      return;
    }

    currentUser.value = storedUser;
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

  const register = async (credentials: RegisterRequest) => {
    isLoading.value = true;
    error.value = null;

    try {
      const response = await authService.register(credentials);
      currentUser.value = response.user;

      const token = authService.getToken();
      if (token) {
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
    logout,
  };
});
