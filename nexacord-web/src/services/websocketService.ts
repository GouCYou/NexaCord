import { Client, type IMessage } from '@stomp/stompjs';
import type { StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export type WebSocketEvent =
  | 'connect'
  | 'disconnect'
  | 'error'
  | 'message:new'
  | 'message:update'
  | 'message:delete'
  | 'user:status:update'
  | 'user:join'
  | 'user:leave'
  | 'channel:update'
  | 'server:update';

export type WebSocketEventHandler = (event: WebSocketEvent, payload?: unknown) => void;

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://weiladream.cn:18080/api';
const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || API_BASE_URL.replace(/\/api\/?$/, '');

class WebSocketService {
  private client: Client | null = null;
  private handlers = new Map<WebSocketEvent, Set<WebSocketEventHandler>>();

  public initialize(token: string): void {
    if (this.client?.active) {
      return;
    }

    if (this.client) {
      void this.client.deactivate();
      this.client = null;
    }

    this.client = new Client({
      webSocketFactory: () => new SockJS(`${WS_BASE_URL}/ws`),
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      reconnectDelay: 3000,
      onConnect: () => {
        this.subscribeToTopics();
        this.dispatchEvent('connect');
      },
      onDisconnect: () => {
        this.dispatchEvent('disconnect');
      },
      onWebSocketClose: () => {
        this.dispatchEvent('disconnect');
      },
      onStompError: (frame) => {
        this.dispatchEvent('error', frame);
      },
    });

    this.client.activate();
  }

  public disconnect(): void {
    if (this.client) {
      void this.client.deactivate();
      this.client = null;
    }
  }

  public isConnected(): boolean {
    return this.client?.connected ?? false;
  }

  public emit(destination: string, data?: unknown): void {
    if (!this.client?.connected) {
      return;
    }

    this.client.publish({
      destination: `/app${destination}`,
      body: JSON.stringify(data ?? {}),
    });
  }

  public on(event: WebSocketEvent, handler: WebSocketEventHandler): void {
    const existingHandlers = this.handlers.get(event) || new Set<WebSocketEventHandler>();
    existingHandlers.add(handler);
    this.handlers.set(event, existingHandlers);
  }

  public off(event: WebSocketEvent, handler: WebSocketEventHandler): void {
    const existingHandlers = this.handlers.get(event);
    if (!existingHandlers) {
      return;
    }

    existingHandlers.delete(handler);
    if (existingHandlers.size === 0) {
      this.handlers.delete(event);
    }
  }

  public subscribe(destination: string, handler: (payload: unknown) => void): (() => void) | null {
    if (!this.client?.connected) {
      return null;
    }

    const subscription: StompSubscription = this.client.subscribe(destination, (message) => {
      try {
        handler(JSON.parse(message.body));
      } catch (error) {
        console.error(`解析实时订阅内容失败：${destination}`, error);
      }
    });

    return () => subscription.unsubscribe();
  }

  private subscribeToTopics(): void {
    if (!this.client?.connected) {
      return;
    }

    this.client.subscribe('/topic/messages/new', (message) => {
      this.handleMessage('message:new', message);
    });

    this.client.subscribe('/topic/messages/update', (message) => {
      this.handleMessage('message:update', message);
    });

    this.client.subscribe('/topic/messages/delete', (message) => {
      this.handleMessage('message:delete', message);
    });

    this.client.subscribe('/topic/users/status', (message) => {
      this.handleMessage('user:status:update', message);
    });

    this.client.subscribe('/topic/users/join', (message) => {
      this.handleMessage('user:join', message);
    });

    this.client.subscribe('/topic/users/leave', (message) => {
      this.handleMessage('user:leave', message);
    });

    this.client.subscribe('/topic/channels/update', (message) => {
      this.handleMessage('channel:update', message);
    });

    this.client.subscribe('/topic/servers/update', (message) => {
      this.handleMessage('server:update', message);
    });
  }

  private handleMessage(event: WebSocketEvent, message: IMessage): void {
    try {
      this.dispatchEvent(event, JSON.parse(message.body));
    } catch (error) {
      console.error(`解析实时消息内容失败：${event}`, error);
    }
  }

  private dispatchEvent(event: WebSocketEvent, payload?: unknown): void {
    const eventHandlers = this.handlers.get(event);
    if (!eventHandlers) {
      return;
    }

    eventHandlers.forEach((handler) => {
      try {
        handler(event, payload);
      } catch (error) {
        console.error(`处理实时消息事件失败：${event}`, error);
      }
    });
  }
}

export default new WebSocketService();
