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
  const sessionReplacementNotice = ref<SessionReplacementNotice | null>(null);
  let statusRealtimeInitialized = false;
  let authLifecycleInitialized = false;
  let authSessionSubscriptionCleanup: (() => void) | null = null;

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

  const subscribeAuthSessionEvents = () => {
    authSessionSubscriptionCleanup?.();
    authSessionSubscriptionCleanup = null;

    if (!currentUser.value || !websocketService.isConnected()) {
      return;
    }

    authSessionSubscriptionCleanup = websocketService.subscribe(`/topic/auth/user/${currentUser.value.id}`, (payload) => {
      const notice = payload as SessionReplacementNotice & { type?: string };
      if (notice?.type === 'SESSION_REPLACED') {
        handleSessionReplacement(notice);
      }
    });
  };

  const initializeAuthLifecycle = () => {
    if (authLifecycleInitialized) {
      return;
    }

    window.addEventListener('nexacord:auth-refreshed', (event) => {
      const session = (event as CustomEvent<{ accessToken?: string; user?: User }>).detail;
      if (session?.user) {
        currentUser.value = session.user;
        authService.setCurrentUser(session.user);
      }
      if (session?.accessToken) {
        websocketService.updateToken(session.accessToken);
      }
      subscribeAuthSessionEvents();
    });

    window.addEventListener('nexacord:auth-expired', () => {
      authSessionSubscriptionCleanup?.();
      authSessionSubscriptionCleanup = null;
      websocketService.disconnect();
      currentUser.value = null;
      error.value = '登录状态已过期，请重新登录。';
    });

    window.addEventListener('nexacord:session-replaced', (event) => {
      const detail = (event as CustomEvent<SessionReplacementNotice>).detail;
      handleSessionReplacement(detail);
    });

    websocketService.on('connect', () => {
      subscribeAuthSessionEvents();
    });

    authLifecycleInitialized = true;
  };

  const initializeUser = () => {
    initializeAuthLifecycle();
    const storedUser = authService.getCurrentUser();
    const token = authService.getToken();

    if (!storedUser || (!token && !authService.getRefreshToken())) {
      currentUser.value = null;
      return;
    }

    currentUser.value = storedUser;
    initializeStatusRealtime();
    if (token) {
      websocketService.initialize(token);
    }
    subscribeAuthSessionEvents();
  };

  const login = async (credentials: LoginRequest, options: { rememberMe?: boolean } = {}) => {
    isLoading.value = true;
    error.value = null;

    try {
      const response = await authService.login(credentials, options.rememberMe ?? true);
      currentUser.value = response.user;

      const token = authService.getToken();
      if (token) {
        initializeAuthLifecycle();
        initializeStatusRealtime();
        websocketService.initialize(token);
        subscribeAuthSessionEvents();
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
        initializeAuthLifecycle();
        initializeStatusRealtime();
        websocketService.initialize(token);
        subscribeAuthSessionEvents();
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
    authSessionSubscriptionCleanup?.();
    authSessionSubscriptionCleanup = null;
    websocketService.disconnect();
    currentUser.value = null;
    error.value = null;
  };

  const handleSessionReplacement = (notice?: SessionReplacementNotice | null) => {
    sessionReplacementNotice.value = {
      deviceName: notice?.deviceName || '另一台设备',
      loggedInAt: notice?.loggedInAt,
    };
    authService.clearSession(false);
    authSessionSubscriptionCleanup?.();
    authSessionSubscriptionCleanup = null;
    websocketService.disconnect();
    currentUser.value = null;
    error.value = '你的账号已在其他设备登录。';
  };

  const acknowledgeSessionReplacement = () => {
    sessionReplacementNotice.value = null;
    error.value = null;
  };

  return {
    currentUser,
    isLoading,
    error,
    sessionReplacementNotice,
    isAuthenticated,
    initializeUser,
    login,
    register,
    refreshCurrentUser,
    updateProfile,
    logout,
    acknowledgeSessionReplacement,
  };
});

type SessionReplacementNotice = {
  deviceName?: string;
  loggedInAt?: string;
};
