<template>
  <section
    class="friends-home"
    @mousedown="handleFriendPointerStart"
    @mouseup="handleFriendPointerEnd"
    @pointerdown.passive="handleFriendPointerStart"
    @pointerup.passive="handleFriendPointerEnd"
    @touchstart.passive="handleFriendTouchStart"
    @touchend.passive="handleFriendTouchEnd"
  >
    <header class="friends-topbar">
      <button class="mobile-nav-button" type="button" aria-label="打开导航" @click="openMobileNav">
        <Menu :size="22" aria-hidden="true" />
      </button>

      <div class="topbar-title">
        <Users :size="22" aria-hidden="true" />
        <h1>好友</h1>
      </div>

      <nav class="friend-tabs" aria-label="好友筛选">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          :class="{ active: activeTab === tab.value, add: tab.value === 'add' }"
          @click="activeTab = tab.value"
        >
          {{ tab.label }}
        </button>
      </nav>
    </header>

    <div class="friends-content">
      <main class="friends-main">
        <form v-if="activeTab === 'add'" ref="addFriendForm" class="add-friend-panel" @submit.prevent="sendFriendRequest">
          <div>
            <h2>添加好友</h2>
            <p>你可以通过用户名或邮箱发送好友请求。</p>
          </div>
          <div class="add-friend-row">
            <input
              v-model="friendQuery"
              type="text"
              placeholder="输入用户名或邮箱"
              aria-label="输入用户名或邮箱"
            />
            <button class="primary-button" type="submit" :disabled="!friendQuery.trim() || isSubmittingFriend">
              <UserPlus :size="18" aria-hidden="true" />
              <span>{{ isSubmittingFriend ? '发送中……' : '发送好友请求' }}</span>
            </button>
          </div>
          <p v-if="friendError" class="form-error">{{ friendError }}</p>
        </form>

        <section v-else-if="activeTab === 'pending'" class="friends-section">
          <header class="section-heading">
            <span>待处理</span>
            <button class="icon-button" type="button" title="刷新" :disabled="isLoading" @click="loadFriends">
              <RefreshCw :size="17" aria-hidden="true" />
            </button>
          </header>

          <div class="request-columns">
            <div class="request-column">
              <h2>收到的请求</h2>
              <p v-if="incomingRequests.length === 0" class="empty-copy">暂时没有新的好友请求。</p>
              <article v-for="request in incomingRequests" :key="request.id" class="friend-row">
                <div class="avatar">
                  <AvatarImage :src="avatarUrl(request.user)" :alt="usernameTag(request.user.username)" />
                  <i :class="['status-dot', request.user.status || 'offline']"></i>
                </div>
                <div class="friend-copy">
                  <strong>{{ usernameTag(request.user.username) }}</strong>
                  <span>想添加你为好友</span>
                </div>
                <div class="row-actions">
                  <button class="circle-action accept" type="button" title="接受" @click="acceptRequest(request.id)">
                    <Check :size="18" aria-hidden="true" />
                  </button>
                  <button class="circle-action danger" type="button" title="忽略" @click="removeFriend(request.id)">
                    <X :size="18" aria-hidden="true" />
                  </button>
                </div>
              </article>
            </div>

            <div class="request-column">
              <h2>已发送</h2>
              <p v-if="outgoingRequests.length === 0" class="empty-copy">没有等待对方确认的请求。</p>
              <article v-for="request in outgoingRequests" :key="request.id" class="friend-row">
                <div class="avatar">
                  <AvatarImage :src="avatarUrl(request.user)" :alt="usernameTag(request.user.username)" />
                  <i :class="['status-dot', request.user.status || 'offline']"></i>
                </div>
                <div class="friend-copy">
                  <strong>{{ usernameTag(request.user.username) }}</strong>
                  <span>正在等待确认</span>
                </div>
                <button class="circle-action danger" type="button" title="撤回" @click="removeFriend(request.id)">
                  <Trash2 :size="17" aria-hidden="true" />
                </button>
              </article>
            </div>
          </div>
        </section>

        <section v-else class="friends-section">
          <div class="friend-search">
            <Search :size="20" aria-hidden="true" />
            <input v-model="searchQuery" type="text" placeholder="搜索" aria-label="搜索好友" />
          </div>

          <header class="section-heading">
            <span>{{ filteredVisibleFriends.length }} 位好友</span>
            <button class="icon-button" type="button" title="刷新" :disabled="isLoading" @click="loadFriends">
              <RefreshCw :size="17" aria-hidden="true" />
            </button>
          </header>

          <div v-if="isLoading" class="status-block">正在加载好友……</div>
          <div v-else-if="filteredVisibleFriends.length === 0" class="empty-state">
            <Users :size="52" aria-hidden="true" />
            <strong>{{ emptyFriendTitle }}</strong>
            <p>添加好友后，这里会成为你的默认社交入口。</p>
          </div>

          <article v-for="friendship in filteredVisibleFriends" v-else :key="friendship.id" class="friend-row">
            <div class="avatar">
              <AvatarImage :src="avatarUrl(friendship.user)" :alt="usernameTag(friendship.user.username)" />
              <i :class="['status-dot', friendship.user.status || 'offline']"></i>
            </div>
            <div class="friend-copy">
              <strong>{{ usernameTag(friendship.user.username) }}</strong>
              <span>{{ statusLabel(friendship.user.status) }}</span>
            </div>
            <div class="row-actions">
              <button class="circle-action" type="button" title="发送消息" @click="openDirectConversation(friendship.user)">
                <MessageCircle :size="18" aria-hidden="true" />
              </button>
              <button
                class="circle-action danger"
                type="button"
                :class="{ confirming: confirmingRemoveFriendId === friendship.id }"
                :title="confirmingRemoveFriendId === friendship.id ? '再次点击确认删除好友' : '移除好友'"
                @click="removeFriend(friendship.id)"
              >
                <Trash2 :size="17" aria-hidden="true" />
              </button>
            </div>
          </article>
        </section>
      </main>

      <aside class="activity-panel">
        <h2>当前活动</h2>
        <div class="activity-empty">
          <Gamepad2 :size="44" aria-hidden="true" />
          <strong>现在很安静……</strong>
          <p>当好友开始语音聊天或进入活动时，状态会显示在这里。</p>
        </div>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  Check,
  Gamepad2,
  Menu,
  MessageCircle,
  RefreshCw,
  Search,
  Trash2,
  UserPlus,
  Users,
  X,
} from 'lucide-vue-next';
import friendService from '../services/friendService';
import websocketService from '../services/websocketService';
import { useDirectMessageStore } from '../stores/directMessageStore';
import { useUserStore } from '../stores/userStore';
import type { Friendship, User } from '../types';
import { usernameTag } from '../utils/userDisplay';

type FriendTab = 'online' | 'all' | 'pending' | 'add';

const tabs: Array<{ label: string; value: FriendTab }> = [
  { label: '在线', value: 'online' },
  { label: '全部', value: 'all' },
  { label: '待处理', value: 'pending' },
  { label: '添加好友', value: 'add' },
];

const router = useRouter();
const directMessageStore = useDirectMessageStore();
const userStore = useUserStore();
const friends = ref<Friendship[]>([]);
const incomingRequests = ref<Friendship[]>([]);
const outgoingRequests = ref<Friendship[]>([]);
const activeTab = ref<FriendTab>('online');
const friendTouchStartX = ref(0);
const friendTouchStartY = ref(0);
const isLoading = ref(false);
const friendQuery = ref('');
const searchQuery = ref('');
const friendError = ref('');
const isSubmittingFriend = ref(false);
const addFriendForm = ref<HTMLFormElement | null>(null);
const confirmingRemoveFriendId = ref<number | null>(null);
let lastFriendSwipeAt = 0;

const onlineFriends = computed(() =>
  friends.value.filter((friendship) => friendship.user.status && friendship.user.status !== 'offline')
);

const visibleFriends = computed(() => (activeTab.value === 'online' ? onlineFriends.value : friends.value));

const filteredVisibleFriends = computed(() => {
  const query = searchQuery.value.trim().toLowerCase();
  if (!query) {
    return visibleFriends.value;
  }

  return visibleFriends.value.filter((friendship) =>
    friendship.user.username.toLowerCase().includes(query)
  );
});

const emptyFriendTitle = computed(() => {
  if (searchQuery.value.trim()) {
    return '没有找到匹配的好友';
  }

  return activeTab.value === 'online' ? '暂时没有在线好友' : '还没有好友';
});

const avatarUrl = (user: User) => user.avatarUrl || '/logo.png';

const statusLabel = (status: User['status']) => {
  const labels = {
    online: '在线',
    offline: '离线',
    away: '闲置',
    dnd: '请勿打扰',
  } as const;

  return labels[status] || '离线';
};

const loadFriends = async () => {
  isLoading.value = true;
  friendError.value = '';

  try {
    const [nextFriends, nextIncoming, nextOutgoing] = await Promise.all([
      friendService.getFriends(),
      friendService.getIncomingRequests(),
      friendService.getOutgoingRequests(),
    ]);

    friends.value = nextFriends;
    incomingRequests.value = nextIncoming;
    outgoingRequests.value = nextOutgoing;
  } catch (error: any) {
    friendError.value =
      error.response?.data?.error ||
      error.response?.data?.message ||
      '暂时无法加载好友数据。';
  } finally {
    isLoading.value = false;
  }
};

const sendFriendRequest = async () => {
  if (!friendQuery.value.trim()) {
    return;
  }

  isSubmittingFriend.value = true;
  friendError.value = '';

  try {
    await friendService.createFriendRequest(friendQuery.value.trim());
    friendQuery.value = '';
    activeTab.value = 'pending';
    await loadFriends();
  } catch (error: any) {
    friendError.value =
      error.response?.data?.error ||
      error.response?.data?.message ||
      '发送好友请求失败。';
  } finally {
    isSubmittingFriend.value = false;
  }
};

const acceptRequest = async (friendshipId: number) => {
  await friendService.acceptFriendRequest(friendshipId);
  await loadFriends();
};

const removeFriend = async (friendshipId: number) => {
  if (friends.value.some((friendship) => friendship.id === friendshipId)) {
    if (confirmingRemoveFriendId.value !== friendshipId) {
      confirmingRemoveFriendId.value = friendshipId;
      return;
    }
  }

  await friendService.deleteFriendship(friendshipId);
  confirmingRemoveFriendId.value = null;
  await loadFriends();
};

const openDirectConversation = async (user: User) => {
  const conversation = await directMessageStore.openConversationWithUser(user);
  if (conversation) {
    router.push(`/direct/${conversation.id}`);
  }
};

const focusFriendTab = (event: Event) => {
  const detail = (event as CustomEvent<{ tab?: FriendTab }>).detail;
  activeTab.value = detail?.tab || 'add';
  if (activeTab.value === 'add') {
    window.setTimeout(() => {
      addFriendForm.value?.querySelector('input')?.focus();
    }, 80);
  }
};

const openMobileNav = () => {
  window.dispatchEvent(new CustomEvent('nexacord:open-mobile-nav'));
};

const isSwipeIgnoredTarget = (target: EventTarget | null) =>
  target instanceof HTMLElement && Boolean(target.closest('input, textarea, select, button, a'));

const switchFriendTab = (step: -1 | 1) => {
  const tabOrder = tabs.map((tab) => tab.value);
  const activeIndex = tabOrder.indexOf(activeTab.value);
  const nextIndex = (activeIndex + step + tabOrder.length) % tabOrder.length;
  const nextTab = tabOrder[nextIndex];
  if (nextTab) {
    activeTab.value = nextTab;
  }
};

const handleFriendTouchStart = (event: TouchEvent) => {
  const touch = event.changedTouches[0];
  if (!touch || window.innerWidth > 760 || isSwipeIgnoredTarget(event.target)) {
    return;
  }

  friendTouchStartX.value = touch.clientX;
  friendTouchStartY.value = touch.clientY;
};

const handleFriendTouchEnd = (event: TouchEvent) => {
  const touch = event.changedTouches[0];
  if (!touch || window.innerWidth > 760 || !friendTouchStartX.value) {
    return;
  }

  finishFriendSwipe(touch.clientX, touch.clientY);
};

const handleFriendPointerStart = (event: PointerEvent | MouseEvent) => {
  if (window.innerWidth > 760 || isSwipeIgnoredTarget(event.target)) {
    return;
  }

  if ('button' in event && event.button !== 0) {
    return;
  }

  friendTouchStartX.value = event.clientX;
  friendTouchStartY.value = event.clientY;
};

const handleFriendPointerEnd = (event: PointerEvent | MouseEvent) => {
  if (window.innerWidth > 760 || !friendTouchStartX.value) {
    return;
  }

  finishFriendSwipe(event.clientX, event.clientY);
};

const finishFriendSwipe = (clientX: number, clientY: number) => {
  const deltaX = clientX - friendTouchStartX.value;
  const deltaY = clientY - friendTouchStartY.value;
  friendTouchStartX.value = 0;
  friendTouchStartY.value = 0;

  const now = Date.now();
  if (
    now - lastFriendSwipeAt < 320 ||
    Math.abs(deltaX) < 70 ||
    Math.abs(deltaX) < Math.abs(deltaY) * 1.3
  ) {
    return;
  }

  lastFriendSwipeAt = now;
  switchFriendTab(deltaX < 0 ? 1 : -1);
};

const applyUserStatusUpdate = (payload: unknown) => {
  const author = (payload as { author?: Pick<User, 'id' | 'status'> })?.author;
  if (!author?.id || !author.status) {
    return;
  }

  const updateFriendshipUser = (friendship: Friendship) =>
    friendship.user.id === author.id
      ? { ...friendship, user: { ...friendship.user, status: author.status as User['status'] } }
      : friendship;

  friends.value = friends.value.map(updateFriendshipUser);
  incomingRequests.value = incomingRequests.value.map(updateFriendshipUser);
  outgoingRequests.value = outgoingRequests.value.map(updateFriendshipUser);
};

let unsubscribeFriendRealtime: (() => void) | null = null;

const subscribeFriendRealtime = () => {
  const userId = userStore.currentUser?.id;
  if (!userId || !websocketService.isConnected() || unsubscribeFriendRealtime) {
    return;
  }

  unsubscribeFriendRealtime = websocketService.subscribe(`/topic/friends/user/${userId}`, () => {
    void loadFriends();
  });
};

const handleRealtimeConnect = () => {
  subscribeFriendRealtime();
};

const handleUserStatusRealtime = (_event: unknown, payload: unknown) => {
  applyUserStatusUpdate(payload);
};

onMounted(async () => {
  await loadFriends();
  subscribeFriendRealtime();
  websocketService.on('connect', handleRealtimeConnect);
  websocketService.on('user:status:update', handleUserStatusRealtime);
  window.addEventListener('nexacord:focus-friend-tab', focusFriendTab);
});

onBeforeUnmount(() => {
  unsubscribeFriendRealtime?.();
  unsubscribeFriendRealtime = null;
  websocketService.off('connect', handleRealtimeConnect);
  websocketService.off('user:status:update', handleUserStatusRealtime);
  window.removeEventListener('nexacord:focus-friend-tab', focusFriendTab);
});
</script>

<style scoped>
.friends-home {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-rows: 58px 1fr;
  background: var(--discord-bg);
}

.friends-topbar {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 0 18px;
  border-bottom: 1px solid var(--discord-border);
  background: var(--discord-bg);
}

.mobile-nav-button {
  display: none;
}

.topbar-title {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-right: 18px;
  border-right: 1px solid var(--discord-border);
}

.topbar-title h1 {
  margin: 0;
  font-size: 18px;
}

.friend-tabs {
  display: flex;
  align-items: center;
  gap: 10px;
}

.friend-tabs button {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 6px;
  background: transparent;
  color: var(--discord-text-muted);
  font-weight: 800;
}

.friend-tabs button:hover,
.friend-tabs button.active {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.friend-tabs button.add {
  background: var(--discord-green);
  color: white;
}

.friends-content {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
}

.friends-main {
  min-height: 0;
  overflow-y: auto;
  padding: 20px 30px;
}

.friends-section {
  display: grid;
  gap: 8px;
  max-width: 980px;
}

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 36px;
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.friend-search {
  min-height: 46px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  padding: 0 14px;
  border-radius: 8px;
  background: var(--discord-input);
  color: var(--discord-text-faint);
}

.friend-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--discord-text);
  font-size: 15px;
}

.friend-search input::placeholder {
  color: var(--discord-text-faint);
}

.icon-button,
.circle-action {
  display: grid;
  place-items: center;
  background: var(--discord-surface);
  color: var(--discord-text-muted);
}

.icon-button {
  width: 34px;
  height: 34px;
  border-radius: 8px;
}

.icon-button:hover:not(:disabled),
.circle-action:hover:not(:disabled) {
  color: var(--discord-text);
  background: var(--discord-pressed);
}

.friend-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 62px;
  padding: 8px 10px;
  border-top: 1px solid var(--discord-border);
  border-radius: 6px;
}

.friend-row:hover {
  background: var(--discord-subtle);
}

.avatar {
  position: relative;
  width: 40px;
  height: 40px;
  min-width: 40px;
  max-width: 40px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
  overflow: visible;
}

.avatar img {
  display: block;
  width: 40px;
  height: 40px;
  max-width: 40px;
  max-height: 40px;
  border-radius: inherit;
  object-fit: cover;
}

.avatar span {
  line-height: 1;
}

.status-dot {
  position: absolute;
  right: -1px;
  bottom: -1px;
  width: 13px;
  height: 13px;
  border: 3px solid var(--discord-bg);
  border-radius: 50%;
  background: var(--discord-green);
}

.status-dot.offline {
  background: #80848e;
}

.status-dot.away {
  background: #f0b232;
}

.status-dot.dnd {
  background: var(--discord-red);
}

.friend-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.friend-copy strong,
.friend-copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.friend-copy span {
  color: var(--discord-text-faint);
  font-size: 13px;
}

.row-actions {
  display: flex;
  gap: 8px;
}

.circle-action {
  width: 36px;
  height: 36px;
  border-radius: 50%;
}

.circle-action.accept:hover:not(:disabled) {
  color: #85e89d;
}

.circle-action.danger:hover:not(:disabled) {
  color: #ff8b8d;
}

.circle-action.confirming {
  background: color-mix(in srgb, var(--discord-red) 18%, var(--discord-surface));
  color: #ff8b8d;
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--discord-red) 28%, transparent);
}

.add-friend-panel {
  display: grid;
  gap: 16px;
  max-width: 760px;
}

.add-friend-panel h2,
.add-friend-panel p {
  margin: 0;
}

.add-friend-panel p,
.empty-copy {
  color: var(--discord-text-faint);
}

.add-friend-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}

.add-friend-row input {
  min-height: 46px;
  border: 0;
  border-radius: 8px;
  padding: 0 14px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.primary-button {
  min-height: 46px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  border-radius: 8px;
  background: var(--discord-green);
  color: white;
  font-weight: 900;
}

.request-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 22px;
}

.request-column {
  display: grid;
  align-content: start;
  gap: 8px;
}

.request-column h2 {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--discord-text-muted);
}

.empty-state,
.status-block {
  min-height: 340px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: var(--discord-text-faint);
  text-align: center;
}

.empty-state strong {
  color: var(--discord-text);
  font-size: 18px;
}

.empty-state p {
  margin: 0;
}

.form-error {
  margin: 0;
  color: #ff8b8d;
  font-size: 13px;
}

.activity-panel {
  min-height: 0;
  padding: 22px 26px;
  border-left: 1px solid var(--discord-border);
  background: var(--discord-bg);
}

.activity-panel h2 {
  margin: 0 0 34px;
  font-size: 18px;
}

.activity-empty {
  display: grid;
  justify-items: center;
  gap: 12px;
  padding-top: 38px;
  color: var(--discord-text-faint);
  text-align: center;
}

.activity-empty strong {
  color: var(--discord-text);
  font-size: 18px;
}

.activity-empty p {
  max-width: 280px;
  margin: 0;
  line-height: 1.55;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 1120px) {
  .friends-content {
    grid-template-columns: 1fr;
  }

  .activity-panel {
    display: none;
  }
}

@media (max-width: 860px) {
  .friends-home {
    grid-template-rows: auto 1fr;
  }

  .friends-topbar {
    align-items: flex-start;
    flex-direction: column;
    height: auto;
    padding: 12px 16px;
  }

  .topbar-title {
    border-right: 0;
  }

  .friend-tabs {
    flex-wrap: wrap;
  }

  .request-columns,
  .add-friend-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 760px) {
  .friends-home {
    height: 100dvh;
    grid-template-rows: auto minmax(0, 1fr);
  }

  .friends-topbar {
    display: grid;
    grid-template-columns: 38px minmax(0, 1fr);
    grid-template-rows: 38px auto;
    min-height: calc(98px + env(safe-area-inset-top));
    align-items: center;
    gap: 8px;
    padding: calc(8px + env(safe-area-inset-top)) 10px 10px;
    overflow: hidden;
  }

  .mobile-nav-button {
    width: 38px;
    height: 38px;
    border-radius: 10px;
    display: grid;
    place-items: center;
    background: var(--discord-muted-surface);
    color: var(--discord-text);
  }

  .topbar-title {
    min-width: 0;
    gap: 8px;
    padding-right: 0;
    border-right: 0;
  }

  .topbar-title h1 {
    font-size: 16px;
  }

  .friend-tabs {
    grid-column: 1 / -1;
    min-width: 0;
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 4px;
    padding: 4px;
    border-radius: 14px;
    overflow: hidden;
    background: var(--discord-surface-soft);
  }

  .friend-tabs button {
    min-width: 0;
    min-height: 34px;
    padding: 0 6px;
    border-radius: 10px;
    font-size: 13px;
    white-space: nowrap;
  }

  .friend-tabs button.add {
    background: transparent;
    color: var(--discord-green);
  }

  .friend-tabs button.add.active {
    background: var(--discord-green);
    color: white;
  }

  .friends-content {
    min-height: 0;
  }

  .friends-main {
    padding: 14px 12px calc(18px + env(safe-area-inset-bottom));
  }

  .friend-row {
    grid-template-columns: 42px minmax(0, 1fr) auto;
    gap: 10px;
    padding: 8px 6px;
  }

  .row-actions {
    gap: 6px;
  }

  .circle-action {
    width: 34px;
    height: 34px;
  }
}
</style>
