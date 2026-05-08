import axios from 'axios';
import type { AxiosInstance, AxiosRequestConfig } from 'axios';
import type { User } from '../types';
import { clearAuthSession, isRememberedSession, persistAuthSession, readAuthValue } from '../utils/authStorage';

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
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config;
    });

    this.axiosInstance.interceptors.response.use(
      (response) => response.data,
      async (error) => {
        const originalConfig = error.config as RetriableRequestConfig | undefined;
        const status = error.response?.status;
        const canRefresh =
          status === 401 &&
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
          this.clearSession();
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
      });
      const session = response.data;
      persistAuthSession(session, isRememberedSession());
      window.dispatchEvent(new CustomEvent('nexacord:auth-refreshed', {
        detail: session,
      }));
      return session.accessToken;
    } catch {
      this.clearSession();
      return null;
    }
  }

  private clearSession(): void {
    clearAuthSession(false);
    window.dispatchEvent(new CustomEvent('nexacord:auth-expired'));
  }
}

export default new ApiService();
