import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

export type ThemeMode = 'dark' | 'light';

const STORAGE_KEY = 'nexacord-theme';

const resolveInitialTheme = (): ThemeMode => {
  if (typeof window === 'undefined') {
    return 'dark';
  }

  const storedTheme = window.localStorage.getItem(STORAGE_KEY);
  if (storedTheme === 'light' || storedTheme === 'dark') {
    return storedTheme;
  }

  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
};

const applyTheme = (theme: ThemeMode) => {
  if (typeof document === 'undefined') {
    return;
  }

  document.documentElement.dataset.theme = theme;
  document.documentElement.style.setProperty('color-scheme', theme);
};

export const useThemeStore = defineStore('theme', () => {
  const theme = ref<ThemeMode>(resolveInitialTheme());
  const isLight = computed(() => theme.value === 'light');

  const setTheme = (nextTheme: ThemeMode) => {
    theme.value = nextTheme;
    if (typeof window !== 'undefined') {
      window.localStorage.setItem(STORAGE_KEY, nextTheme);
    }
    applyTheme(nextTheme);
  };

  const toggleTheme = () => {
    setTheme(isLight.value ? 'dark' : 'light');
  };

  const initializeTheme = () => {
    applyTheme(theme.value);
  };

  return {
    theme,
    isLight,
    initializeTheme,
    setTheme,
    toggleTheme,
  };
});
