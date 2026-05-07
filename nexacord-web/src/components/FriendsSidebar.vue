<template>
  <aside class="friends-sidebar">
    <header class="friends-header">
      <h2>私信</h2>
      <button class="header-icon" type="button" title="添加好友" @click="focusAddFriend">
        <UserPlus :size="18" aria-hidden="true" />
      </button>
    </header>

    <div class="dm-search">
      <Search :size="17" aria-hidden="true" />
      <input type="text" placeholder="寻找或开始新的对话" aria-label="寻找或开始新的对话" />
    </div>

    <nav class="friends-nav" aria-label="好友导航">
      <button class="nav-item active" type="button">
        <Users :size="20" aria-hidden="true" />
        <span>好友</span>
      </button>
      <button class="nav-item" type="button" @click="focusAddFriend">
        <Inbox :size="20" aria-hidden="true" />
        <span>好友请求</span>
      </button>
    </nav>

    <div class="dm-list">
      <span class="dm-title">直接消息</span>
      <p>私信会话会在好友系统继续完善后显示在这里。</p>
    </div>

    <UserControlPanel show-logout @logout="logout" />
  </aside>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { Inbox, Search, UserPlus, Users } from 'lucide-vue-next';
import UserControlPanel from './UserControlPanel.vue';
import { useUserStore } from '../stores/userStore';
import { useVoiceStore } from '../stores/voiceStore';

const router = useRouter();
const userStore = useUserStore();
const voiceStore = useVoiceStore();

const focusAddFriend = () => {
  window.dispatchEvent(new CustomEvent('nexacord:focus-add-friend'));
};

const logout = () => {
  voiceStore.leaveChannel();
  userStore.logout();
  router.push('/login');
};

</script>

<style scoped>
.friends-sidebar {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-rows: auto auto auto 1fr auto;
  background: var(--discord-surface);
}

.friends-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--discord-border);
}

.friends-header h2 {
  margin: 0;
  font-size: 16px;
}

.header-icon,
.panel-action {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--discord-text-faint);
}

.header-icon:hover,
.panel-action:hover {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.dm-search {
  position: relative;
  padding: 10px;
}

.dm-search svg {
  position: absolute;
  left: 22px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--discord-text-faint);
}

.dm-search input {
  width: 100%;
  height: 36px;
  border: 0;
  border-radius: 6px;
  padding: 0 12px 0 38px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.friends-nav {
  display: grid;
  gap: 4px;
  padding: 10px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 42px;
  padding: 0 12px;
  border-radius: 6px;
  background: transparent;
  color: var(--discord-text-muted);
  font-weight: 800;
  text-align: left;
}

.nav-item:hover,
.nav-item.active {
  background: var(--discord-channel-hover);
  color: var(--discord-text);
}

.dm-list {
  min-height: 0;
  padding: 12px 16px;
  color: var(--discord-text-faint);
  font-size: 13px;
}

.dm-title {
  display: block;
  margin-bottom: 8px;
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.dm-list p {
  margin: 0;
  line-height: 1.5;
}

.user-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 6px;
  padding: 8px;
  background: var(--discord-surface-soft);
}

.user-card {
  min-width: 0;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  border-radius: 8px;
  padding: 6px;
  background: transparent;
  color: var(--discord-text);
  text-align: left;
}

.user-card:hover {
  background: var(--discord-hover);
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
  overflow: hidden;
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-meta {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.user-meta strong,
.user-meta span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-meta strong {
  font-size: 14px;
}

.user-meta span {
  color: var(--discord-text-faint);
  font-size: 12px;
}
</style>
