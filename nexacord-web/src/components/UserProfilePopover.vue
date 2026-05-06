<template>
  <div v-if="isOpen && profileUser" class="popover-layer" @click="closePopover">
    <section class="user-popover" :style="popoverStyle" role="dialog" aria-label="用户资料卡" @click.stop>
      <div class="popover-banner" :style="bannerStyle">
        <div v-if="!isSelf" class="popover-tools">
          <button type="button" title="静音">
            <MicOff :size="14" aria-hidden="true" />
          </button>
          <button type="button" title="身份组">
            <UserCheck :size="14" aria-hidden="true" />
          </button>
          <button type="button" title="更多">
            <MoreHorizontal :size="15" aria-hidden="true" />
          </button>
        </div>
      </div>

      <div class="popover-body">
        <div class="popover-avatar">
          <img :src="profileUser.avatarUrl || defaultAvatarUrl" :alt="displayName" />
          <i :class="['status-dot', profileUser.status || 'online']"></i>
        </div>

        <div class="identity-block">
          <h2>{{ displayName }}</h2>
          <p>{{ profileUser.username }}</p>
          <span v-if="roleText" class="role-badge">{{ roleText }}</span>
        </div>

        <p class="bio">{{ profileUser.bio || '这个用户还没有填写个人简介。' }}</p>

        <div v-if="serverName" class="mutual-server">
          <span class="server-chip">{{ serverName.slice(0, 1).toUpperCase() }}</span>
          <span>{{ serverName }}</span>
        </div>

        <button v-if="isSelf" class="primary-profile-action" type="button" @click="openProfileEditor">
          <Pencil :size="14" aria-hidden="true" />
          <span>编辑个人资料</span>
        </button>

        <div v-else class="profile-actions">
          <button type="button">
            <MessageCircle :size="14" aria-hidden="true" />
            <span>消息</span>
          </button>
          <button type="button">
            <PhoneCall :size="14" aria-hidden="true" />
            <span>呼叫</span>
          </button>
        </div>

        <button class="add-role-button" type="button">
          <Plus :size="14" aria-hidden="true" />
          <span>添加身份组</span>
        </button>

        <label v-if="!isSelf" class="dm-input">
          <input :placeholder="`私信 @${profileUser.username}`" />
          <Smile :size="15" aria-hidden="true" />
        </label>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { storeToRefs } from 'pinia';
import {
  MessageCircle,
  MicOff,
  MoreHorizontal,
  Pencil,
  PhoneCall,
  Plus,
  Smile,
  UserCheck,
} from 'lucide-vue-next';
import { useServerStore } from '../stores/serverStore';
import { useUserStore } from '../stores/userStore';
import type { MemberRole, User } from '../types';

type PopoverUser = Partial<User> & {
  id: number;
  username: string;
  displayName?: string | null;
  avatarUrl?: string | null;
  status?: User['status'];
};

type OpenUserPopoverDetail = {
  user: PopoverUser;
  role?: MemberRole;
  serverName?: string | null;
  x: number;
  y: number;
};

const userStore = useUserStore();
const serverStore = useServerStore();
const { currentUser } = storeToRefs(userStore);
const { currentServer } = storeToRefs(serverStore);

const defaultAvatarUrl = '/logo.png';
const isOpen = ref(false);
const profileUser = ref<PopoverUser | null>(null);
const role = ref<MemberRole | null>(null);
const serverName = ref('');
const position = reactive({
  left: 0,
  top: 0,
});

const roleLabels: Record<MemberRole, string> = {
  OWNER: '服主',
  ADMIN: '管理员',
  MODERATOR: '协管',
  MEMBER: '成员',
};

const displayName = computed(() => {
  const user = profileUser.value;
  return user?.displayName?.trim() || user?.username || '用户';
});

const isSelf = computed(() => Boolean(profileUser.value && currentUser.value?.id === profileUser.value.id));
const roleText = computed(() => (role.value ? roleLabels[role.value] : ''));

const bannerStyle = computed(() => {
  const user = profileUser.value;
  const style: Record<string, string> = {
    backgroundColor: user?.bannerColor || '#5865f2',
  };

  if (user?.bannerUrl) {
    style.backgroundImage = `url(${user.bannerUrl})`;
  }

  return style;
});

const popoverStyle = computed(() => ({
  left: `${position.left}px`,
  top: `${position.top}px`,
}));

const clampPosition = (x: number, y: number) => {
  const margin = 12;
  const cardWidth = 252;
  const cardHeight = 420;
  const windowWidth = window.innerWidth || 1280;
  const windowHeight = window.innerHeight || 720;
  const preferredRight = x + 12;
  const preferredLeft = x - cardWidth - 12;

  position.left =
    preferredRight + cardWidth <= windowWidth - margin
    ? preferredRight
    : Math.max(margin, preferredLeft);
  position.top = Math.min(
    Math.max(margin, y - 20),
    Math.max(margin, windowHeight - cardHeight - margin)
  );
};

const openPopover = (event: Event) => {
  const detail = (event as CustomEvent<OpenUserPopoverDetail>).detail;
  if (!detail?.user) {
    return;
  }

  const enrichedUser =
    currentUser.value && currentUser.value.id === detail.user.id
      ? { ...detail.user, ...currentUser.value }
      : detail.user;

  profileUser.value = enrichedUser;
  role.value = detail.role || null;
  serverName.value = detail.serverName || currentServer.value?.name || '';
  clampPosition(detail.x, detail.y);
  isOpen.value = true;
};

const closePopover = () => {
  isOpen.value = false;
};

const openProfileEditor = () => {
  closePopover();
  window.dispatchEvent(new CustomEvent('nexacord:open-profile'));
};

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    closePopover();
  }
};

onMounted(() => {
  window.addEventListener('nexacord:open-user-popover', openPopover as EventListener);
  window.addEventListener('keydown', handleKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:open-user-popover', openPopover as EventListener);
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped>
.popover-layer {
  position: fixed;
  inset: 0;
  z-index: 95;
  background: transparent;
}

.user-popover {
  position: fixed;
  width: min(252px, calc(100vw - 24px));
  overflow: hidden;
  border: 1px solid var(--discord-border);
  border-radius: 9px;
  background: var(--discord-bg);
  box-shadow: 0 16px 46px rgba(0, 0, 0, 0.28);
  animation: popover-in 120ms ease-out;
}

.popover-banner {
  position: relative;
  height: 82px;
  background:
    linear-gradient(135deg, rgba(88, 101, 242, 0.72), rgba(235, 69, 158, 0.34)),
    var(--discord-brand);
  background-size: cover;
  background-position: center;
}

.popover-tools {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 6px;
}

.popover-tools button {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--discord-surface-alt) 86%, transparent);
  color: var(--discord-text);
}

.popover-tools button:hover {
  background: var(--discord-hover-strong);
}

.popover-body {
  position: relative;
  display: grid;
  gap: 8px;
  padding: 42px 12px 12px;
}

.popover-avatar {
  position: absolute;
  top: -36px;
  left: 12px;
  width: 74px;
  height: 74px;
  border: 5px solid var(--discord-bg);
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
}

.popover-avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.status-dot {
  position: absolute;
  right: 4px;
  bottom: 5px;
  width: 13px;
  height: 13px;
  border: 3px solid var(--discord-bg);
  border-radius: 50%;
  background: var(--discord-green);
}

.status-dot.away {
  background: #f0b232;
}

.status-dot.dnd {
  background: var(--discord-red);
}

.status-dot.offline {
  background: transparent;
  border-color: var(--discord-bg);
  box-shadow: inset 0 0 0 3px var(--discord-text-faint);
}

.identity-block {
  display: grid;
  gap: 3px;
}

.identity-block h2,
.identity-block p,
.bio {
  margin: 0;
}

.identity-block h2 {
  font-size: 18px;
  line-height: 1.1;
}

.identity-block p {
  color: var(--discord-text-muted);
  font-size: 12px;
}

.role-badge {
  width: fit-content;
  padding: 3px 6px;
  border-radius: 999px;
  background: var(--discord-muted-surface);
  color: var(--discord-text);
  font-size: 11px;
  font-weight: 900;
}

.bio {
  color: var(--discord-text);
  font-size: 12px;
  line-height: 1.4;
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.mutual-server {
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 8px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  color: var(--discord-text);
  font-size: 12px;
  font-weight: 800;
}

.server-chip {
  width: 20px;
  height: 20px;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-size: 11px;
  flex: 0 0 auto;
}

.mutual-server span:last-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.primary-profile-action,
.profile-actions button {
  min-height: 34px;
  border-radius: 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 900;
}

.primary-profile-action {
  background: var(--discord-brand);
  color: white;
}

.primary-profile-action:hover {
  background: var(--discord-brand-hover);
}

.profile-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
}

.profile-actions button {
  background: var(--discord-muted-surface);
  color: var(--discord-text);
}

.profile-actions button:hover,
.add-role-button:hover {
  background: var(--discord-hover-strong);
}

.add-role-button {
  width: fit-content;
  min-height: 28px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  background: transparent;
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.dm-input {
  min-height: 38px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 0 9px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  background: var(--discord-input);
  color: var(--discord-text-faint);
}

.dm-input input {
  min-width: 0;
  border: 0;
  background: transparent;
  color: var(--discord-text);
  font-size: 12px;
}

.dm-input input:focus {
  outline: none;
}

@keyframes popover-in {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
