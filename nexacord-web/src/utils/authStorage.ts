const TOKEN_KEY = 'token';
const REFRESH_TOKEN_KEY = 'refreshToken';
const USER_KEY = 'user';
const REMEMBER_ME_KEY = 'rememberMe';

type StoredAuthSession = {
  accessToken: string;
  refreshToken?: string | null;
  user: unknown;
};

const authKeys = [TOKEN_KEY, REFRESH_TOKEN_KEY, USER_KEY] as const;

export const readAuthValue = (key: (typeof authKeys)[number]) =>
  localStorage.getItem(key) ?? sessionStorage.getItem(key);

export const hasPersistentAuth = () =>
  Boolean(localStorage.getItem(TOKEN_KEY) || localStorage.getItem(REFRESH_TOKEN_KEY));

export const shouldRememberByDefault = () => {
  const remembered = localStorage.getItem(REMEMBER_ME_KEY);
  if (remembered != null) {
    return remembered === 'true';
  }

  return true;
};

export const getAuthStorage = () => (hasPersistentAuth() ? localStorage : sessionStorage);

export const persistAuthSession = (session: StoredAuthSession, rememberMe: boolean) => {
  clearAuthSession(false);
  localStorage.setItem(REMEMBER_ME_KEY, rememberMe ? 'true' : 'false');

  const storage = rememberMe ? localStorage : sessionStorage;
  storage.setItem(TOKEN_KEY, session.accessToken);
  if (session.refreshToken) {
    storage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
  }
  storage.setItem(USER_KEY, JSON.stringify(session.user));
};

export const updateStoredUser = (user: unknown) => {
  getAuthStorage().setItem(USER_KEY, JSON.stringify(user));
};

export const clearAuthSession = (clearPreference = true) => {
  authKeys.forEach((key) => {
    localStorage.removeItem(key);
    sessionStorage.removeItem(key);
  });

  if (clearPreference) {
    localStorage.removeItem(REMEMBER_ME_KEY);
  }
};

export const isRememberedSession = () => hasPersistentAuth() || localStorage.getItem(REMEMBER_ME_KEY) === 'true';
