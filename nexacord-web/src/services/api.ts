import axios from 'axios';
import type { AxiosInstance, AxiosRequestConfig } from 'axios';
import type { User } from '../types';
import { clearAuthSession, isRememberedSession, persistAuthSession, readAuthValue } from '../utils/authStorage';
import { getDeviceName } from '../utils/deviceInfo';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://weiladream.cn:18080/api';

type AuthResponse = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  user: User;
};

type RetriableRequestConfig = AxiosRequestConfig & {
  _retry?: boolean;
};

class ApiService {
  private axiosInstance: AxiosInstance;
  private refreshPromise: Promise<string | null> | null = null;

  constructor() {
    this.axiosInstance = axios.create({
      baseURL: API_BASE_URL,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    this.axiosInstance.interceptors.request.use((config) => {
      const token = readAuthValue('token');
      if (token && !this.isPublicAuthEndpoint(config.url)) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    });

    this.axiosInstance.interceptors.response.use(
      (response) => response.data,
      async (error) => {
        const originalConfig = error.config as RetriableRequestConfig | undefined;
        const status = error.response?.status;
        const replacementNotice = this.getSessionReplacementNotice(error);
        if (replacementNotice) {
          this.clearSession('session-replaced', replacementNotice);
          return Promise.reject(error);
        }

        const canRefresh =
          this.isRefreshableAuthError(error) &&
          originalConfig &&
          !originalConfig._retry &&
          !String(originalConfig.url || '').includes('/auth/refresh') &&
          Boolean(readAuthValue('refreshToken'));

        if (canRefresh) {
          originalConfig._retry = true;
          const refreshedToken = await this.refreshAccessToken();
          if (refreshedToken) {
            originalConfig.headers = {
              ...originalConfig.headers,
              Authorization: `Bearer ${refreshedToken}`,
            };
            return this.axiosInstance(originalConfig);
          }
        }

        if (status === 401) {
          this.clearSession('expired');
        }

        return Promise.reject(error);
      }
    );
  }

  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.axiosInstance.get(url, config) as Promise<T>;
  }

  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.axiosInstance.post(url, data, config) as Promise<T>;
  }

  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.axiosInstance.put(url, data, config) as Promise<T>;
  }

  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.axiosInstance.delete(url, config) as Promise<T>;
  }

  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return this.axiosInstance.patch(url, data, config) as Promise<T>;
  }

  uploadFile<T>(url: string, file: File, config?: AxiosRequestConfig): Promise<T> {
    const formData = new FormData();
    formData.append('file', file);

    return this.axiosInstance.post(url, formData, {
      ...config,
      headers: {
        ...config?.headers,
        'Content-Type': 'multipart/form-data',
      },
    }) as Promise<T>;
  }

  uploadMultipleFiles<T>(url: string, files: File[], config?: AxiosRequestConfig): Promise<T> {
    const formData = new FormData();
    files.forEach((file) => formData.append('files', file));

    return this.axiosInstance.post(url, formData, {
      ...config,
      headers: {
        ...config?.headers,
        'Content-Type': 'multipart/form-data',
      },
    }) as Promise<T>;
  }

  private isRefreshableAuthError(error: unknown): boolean {
    const response = (error as any)?.response;
    const status = response?.status;
    if (status === 401) {
      return true;
    }

    if (status !== 403) {
      return false;
    }

    const reason = this.getHeader(response.headers, 'x-nexacord-auth-reason') || response.data?.reason;
    if (reason === 'TOKEN_INVALID_OR_EXPIRED') {
      return true;
    }

    return this.hasEmptyResponseBody(response.data);
  }

  private hasEmptyResponseBody(data: unknown): boolean {
    if (data == null || data === '') {
      return true;
    }

    return typeof data === 'object' && !Array.isArray(data) && Object.keys(data as Record<string, unknown>).length === 0;
  }

  private isPublicAuthEndpoint(url?: string): boolean {
    return String(url || '').includes('/auth/');
  }

  private async refreshAccessToken(): Promise<string | null> {
    if (!this.refreshPromise) {
      this.refreshPromise = this.requestTokenRefresh().finally(() => {
        this.refreshPromise = null;
      });
    }

    return this.refreshPromise;
  }

  private async requestTokenRefresh(): Promise<string | null> {
    const refreshToken = readAuthValue('refreshToken');
    if (!refreshToken) {
      this.clearSession();
      return null;
    }

    try {
      const response = await axios.post<AuthResponse>(`${API_BASE_URL}/auth/refresh`, {
        refreshToken,
        deviceName: getDeviceName(),
      });
      const session = response.data;
      persistAuthSession(session, isRememberedSession());
      window.dispatchEvent(new CustomEvent('nexacord:auth-refreshed', {
        detail: session,
      }));
      return session.accessToken;
    } catch (error) {
      const replacementNotice = this.getSessionReplacementNotice(error);
      this.clearSession(replacementNotice ? 'session-replaced' : 'expired', replacementNotice ?? undefined);
      return null;
    }
  }

  private clearSession(reason: 'expired' | 'session-replaced' = 'expired', replacementNotice?: SessionReplacementNotice): void {
    clearAuthSession(false);
    if (reason === 'session-replaced') {
      window.dispatchEvent(new CustomEvent('nexacord:session-replaced', {
        detail: replacementNotice ?? { deviceName: '另一台设备' },
      }));
      return;
    }

    window.dispatchEvent(new CustomEvent('nexacord:auth-expired'));
  }

  private getSessionReplacementNotice(error: unknown): SessionReplacementNotice | null {
    const response = (error as any)?.response;
    if (!response || response.status !== 401) {
      return null;
    }

    const reason =
      this.getHeader(response.headers, 'x-nexacord-auth-reason') ||
      response.data?.reason;
    if (reason !== 'SESSION_REPLACED') {
      return null;
    }

    const encodedDeviceName = this.getHeader(response.headers, 'x-nexacord-auth-device');
    const bodyDeviceName = response.data?.deviceName;
    return {
      deviceName: this.decodeHeaderValue(encodedDeviceName) || bodyDeviceName || '另一台设备',
    };
  }

  private getHeader(headers: unknown, key: string): string | undefined {
    if (!headers) {
      return undefined;
    }

    const headerRecord = headers as Record<string, string | undefined>;
    return headerRecord[key] || headerRecord[key.toLowerCase()] || headerRecord[key.toUpperCase()];
  }

  private decodeHeaderValue(value?: string): string | undefined {
    if (!value) {
      return undefined;
    }

    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  }
}

type SessionReplacementNotice = {
  deviceName: string;
};

export default new ApiService();
