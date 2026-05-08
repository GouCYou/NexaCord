<template>
  <div v-if="isOpen" class="settings-overlay">
    <aside class="settings-sidebar">
      <button class="server-card" type="button" @click="activeTab = 'overview'">
        <div class="server-icon-preview">
          <img v-if="form.iconUrl" :src="form.iconUrl" :alt="form.name" />
          <ServerIcon v-else :size="26" aria-hidden="true" />
        </div>
        <span>
          <strong>{{ form.name || '服务器' }}</strong>
          <small>服务器设置</small>
        </span>
      </button>

      <nav class="settings-nav" aria-label="服务器设置导航">
        <span>设置</span>
        <button :class="{ active: activeTab === 'overview' }" type="button" @click="activeTab = 'overview'">
          服务器资料
        </button>
        <button :class="{ active: activeTab === 'members' }" type="button" @click="activeTab = 'members'">
          成员与身份组
        </button>
        <button :class="{ active: activeTab === 'invites' }" type="button" @click="openInvite">
          邀请成员
        </button>
      </nav>
    </aside>

    <main class="settings-main">
      <button class="close-button" type="button" aria-label="关闭服务器设置" @click="close">
        <X :size="28" aria-hidden="true" />
        <span>关闭</span>
      </button>

      <section v-if="activeTab === 'overview'" class="settings-content">
        <header class="content-header">
          <h1>服务器资料</h1>
          <p>管理服务器名称、图标、横幅和简介。保存后左侧服务器列表会立即更新。</p>
        </header>

        <div class="overview-grid">
          <form class="settings-form" @submit.prevent="saveOverview">
            <label class="field">
              <span>名称</span>
              <input v-model="form.name" type="text" maxlength="80" placeholder="服务器名称" />
            </label>

            <label class="field">
              <span>简介</span>
              <textarea v-model="form.description" rows="4" maxlength="500" placeholder="介绍这个服务器的用途"></textarea>
            </label>

            <div class="asset-section">
              <div>
                <strong>图标</strong>
                <p>建议使用 512x512 或更高分辨率的图片。</p>
              </div>
              <div class="asset-actions">
                <input ref="iconInput" class="visually-hidden" type="file" accept="image/*" @change="uploadIcon" />
                <button class="primary-button" type="button" @click="iconInput?.click()">更改服务器图标</button>
                <button class="danger-button" type="button" @click="form.iconUrl = null">删除图标</button>
              </div>
            </div>

            <div class="asset-section">
              <div>
                <strong>横幅</strong>
                <p>横幅会显示在服务器资料预览里，也可作为后续服务器发现页素材。</p>
              </div>
              <div class="asset-actions">
                <input ref="bannerInput" class="visually-hidden" type="file" accept="image/*" @change="uploadBanner" />
                <button class="secondary-button" type="button" @click="bannerInput?.click()">上传横幅</button>
                <button class="secondary-button" type="button" @click="form.bannerUrl = null">使用默认横幅</button>
              </div>
            </div>

            <p v-if="feedback" class="feedback">{{ feedback }}</p>

            <button class="save-button" type="submit" :disabled="isSaving || !form.name.trim()">
              {{ isSaving ? '保存中……' : '保存更改' }}
            </button>
          </form>

          <aside class="server-preview">
            <div class="preview-banner" :style="bannerStyle"></div>
            <div class="preview-body">
              <div class="preview-icon">
                <img v-if="form.iconUrl" :src="form.iconUrl" :alt="form.name" />
                <ServerIcon v-else :size="34" aria-hidden="true" />
              </div>
              <h2>{{ form.name || '未命名服务器' }}</h2>
              <p>{{ form.description || '还没有填写服务器简介。' }}</p>
              <small>身份组：服主、管理员、成员</small>
            </div>
          </aside>
        </div>
      </section>

      <section v-else class="settings-content">
        <header class="content-header">
          <h1>成员与身份组</h1>
          <p>基础身份组保持清晰：服主拥有全部权限，管理员可以管理服务器，成员可以参与聊天。</p>
        </header>

        <div class="role-grid">
          <article class="role-card owner"><strong>服主</strong><span>拥有服务器</span></article>
          <article class="role-card admin"><strong>管理员</strong><span>管理频道、邀请和服务器资料</span></article>
          <article class="role-card member"><strong>成员</strong><span>参与文字和语音频道</span></article>
        </div>

        <div class="members-panel">
          <header>
            <strong>服务器成员</strong>
            <button type="button" @click="loadMembers">刷新</button>
          </header>

          <div v-if="membersLoading" class="member-status">正在加载成员……</div>
          <div v-else-if="members.length === 0" class="member-status">还没有成员。</div>

          <template v-else>
            <article v-for="member in members" :key="member.id" class="member-row">
              <div class="member-avatar">
                <img :src="member.user.avatarUrl || defaultAvatarUrl" :alt="usernameTag(member.user.username)" />
              </div>
              <div class="member-copy">
                <strong>{{ usernameTag(member.user.username) }}</strong>
                <span>{{ roleLabel(member.role) }}</span>
              </div>
              <select
                v-if="member.role !== 'OWNER'"
                :value="member.role === 'ADMIN' ? 'ADMIN' : 'MEMBER'"
                :disabled="!currentUserIsOwner"
                @change="handleRoleChange(member.id, $event)"
              >
                <option value="ADMIN">管理员</option>
                <option value="MEMBER">成员</option>
              </select>
              <span v-else class="owner-badge">服主</span>
              <button
                v-if="member.role !== 'OWNER'"
                class="remove-button"
                type="button"
                :disabled="!currentUserCanManage"
                @click="removeMember(member.id)"
              >
                移除
              </button>
            </article>
          </template>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { Server as ServerIcon, X } from 'lucide-vue-next';
import fileService from '../services/fileService';
import { useServerStore } from '../stores/serverStore';
import { useUserStore } from '../stores/userStore';
import type { Member, MemberRole, Server } from '../types';
import { usernameTag } from '../utils/userDisplay';

type SettingsTab = 'overview' | 'members' | 'invites';

const serverStore = useServerStore();
const userStore = useUserStore();
const { currentServer, currentServerId } = storeToRefs(serverStore);
const { currentUser } = storeToRefs(userStore);

const defaultAvatarUrl = '/logo.png';
const isOpen = ref(false);
const activeTab = ref<SettingsTab>('overview');
const isSaving = ref(false);
const membersLoading = ref(false);
const feedback = ref('');
const members = ref<Member[]>([]);
const iconInput = ref<HTMLInputElement | null>(null);
const bannerInput = ref<HTMLInputElement | null>(null);
const form = ref({
  name: '',
  description: '',
  iconUrl: null as string | null,
  bannerUrl: null as string | null,
});

const currentMember = computed(() =>
  members.value.find((member) => member.user.id === currentUser.value?.id) || null
);

const currentUserIsOwner = computed(() => currentMember.value?.role === 'OWNER');
const currentUserCanManage = computed(() => ['OWNER', 'ADMIN'].includes(currentMember.value?.role || ''));

const bannerStyle = computed(() => {
  if (form.value.bannerUrl) {
    return { backgroundImage: `url(${form.value.bannerUrl})` };
  }

  return {};
});

const hydrateForm = (server: Server | null) => {
  form.value = {
    name: server?.name || '',
    description: server?.description || '',
    iconUrl: server?.iconUrl || null,
    bannerUrl: server?.bannerUrl || null,
  };
};

const open = async (event?: Event) => {
  if (!currentServerId.value) {
    return;
  }

  const detail = (event as CustomEvent<{ tab?: SettingsTab }> | undefined)?.detail;
  activeTab.value = detail?.tab === 'members' ? 'members' : 'overview';
  isOpen.value = true;
  feedback.value = '';
  hydrateForm(currentServer.value);
  await loadMembers();
};

const close = () => {
  isOpen.value = false;
  feedback.value = '';
};

const saveOverview = async () => {
  if (!currentServerId.value || !form.value.name.trim()) {
    return;
  }

  isSaving.value = true;
  feedback.value = '';

  const success = await serverStore.updateServer(currentServerId.value, {
    name: form.value.name.trim(),
    description: form.value.description.trim(),
    iconUrl: form.value.iconUrl,
    bannerUrl: form.value.bannerUrl,
  });

  feedback.value = success ? '服务器资料已保存。' : (serverStore.error || '保存失败。');
  isSaving.value = false;
};

const loadMembers = async () => {
  if (!currentServerId.value) {
    return;
  }

  membersLoading.value = true;
  members.value = await serverStore.fetchServerMembers(currentServerId.value);
  membersLoading.value = false;
};

const uploadImage = async (event: Event, target: 'iconUrl' | 'bannerUrl') => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }

  feedback.value = '正在上传图片……';
  form.value[target] = await fileService.uploadFile(file);
  feedback.value = '图片已上传，记得保存更改。';
  input.value = '';
};

const uploadIcon = (event: Event) => uploadImage(event, 'iconUrl');
const uploadBanner = (event: Event) => uploadImage(event, 'bannerUrl');

const changeRole = async (memberId: number, value: string) => {
  if (!currentServerId.value) {
    return;
  }

  const role = value === 'ADMIN' ? 'ADMIN' : 'MEMBER';
  const updatedMember = await serverStore.updateServerMemberRole(currentServerId.value, memberId, role);
  if (updatedMember) {
    members.value = members.value.map((member) => (member.id === memberId ? updatedMember : member));
  }
};

const handleRoleChange = (memberId: number, event: Event) => {
  const select = event.target as HTMLSelectElement;
  changeRole(memberId, select.value);
};

const removeMember = async (memberId: number) => {
  if (!currentServerId.value) {
    return;
  }

  const success = await serverStore.removeServerMember(currentServerId.value, memberId);
  if (success) {
    members.value = members.value.filter((member) => member.id !== memberId);
  }
};

const openInvite = () => {
  close();
  window.dispatchEvent(new CustomEvent('nexacord:open-invite'));
};

const roleLabel = (role: MemberRole) => {
  const labels = {
    OWNER: '服主',
    ADMIN: '管理员',
    MODERATOR: '管理成员',
    MEMBER: '成员',
  } as const;

  return labels[role] || '成员';
};

const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') {
    close();
  }
};

onMounted(() => {
  window.addEventListener('nexacord:open-server-settings', open as EventListener);
  window.addEventListener('keydown', handleKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:open-server-settings', open as EventListener);
  window.removeEventListener('keydown', handleKeydown);
});
</script>

<style scoped>
.settings-overlay {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: grid;
  grid-template-columns: minmax(220px, 28vw) minmax(0, 1fr);
  background: var(--discord-bg);
  animation: fade-in 120ms ease-out;
}

.settings-sidebar {
  min-height: 0;
  padding: 40px 24px 24px max(24px, 8vw);
  background: var(--discord-surface);
  border-right: 1px solid var(--discord-border);
}

.server-card {
  width: 100%;
  display: grid;
  grid-template-columns: 58px minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  padding: 10px;
  border-radius: 12px;
  background: var(--discord-hover);
  color: var(--discord-text);
  text-align: left;
}

.server-icon-preview {
  width: 54px;
  height: 54px;
  border-radius: 18px;
  display: grid;
  place-items: center;
  background: #f2f3f5;
  color: #1e1f22;
  font-weight: 900;
  overflow: hidden;
}

.server-icon-preview img,
.preview-icon img,
.member-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.server-card strong,
.server-card small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-card span {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.server-card small {
  color: var(--discord-text-faint);
  font-size: 12px;
}

.settings-nav {
  display: grid;
  gap: 4px;
  margin-top: 28px;
}

.settings-nav span {
  margin: 10px 8px 4px;
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.settings-nav button {
  min-height: 40px;
  border-radius: 8px;
  padding: 0 10px;
  background: transparent;
  color: var(--discord-text-muted);
  font-weight: 800;
  text-align: left;
}

.settings-nav button:hover,
.settings-nav button.active {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.settings-main {
  position: relative;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
}

.close-button {
  position: fixed;
  top: 34px;
  right: 34px;
  display: grid;
  gap: 6px;
  place-items: center;
  background: transparent;
  color: var(--discord-text-faint);
  font-size: 11px;
  font-weight: 900;
}

.close-button svg {
  width: 42px;
  height: 42px;
  padding: 8px;
  border: 2px solid currentColor;
  border-radius: 50%;
}

.close-button:hover {
  color: var(--discord-text);
}

.settings-content {
  width: min(1120px, calc(100% - 120px));
  display: grid;
  gap: 32px;
  padding: 48px 0 80px 64px;
}

.content-header h1 {
  margin: 0;
  font-size: 28px;
}

.content-header p {
  max-width: 720px;
  margin: 10px 0 0;
  color: var(--discord-text-muted);
  line-height: 1.55;
}

.overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 56px;
  align-items: start;
}

.settings-form {
  display: grid;
  gap: 24px;
}

.field {
  display: grid;
  gap: 8px;
}

.field span,
.asset-section strong {
  color: var(--discord-text);
  font-weight: 900;
}

.field input,
.field textarea,
.member-row select {
  width: 100%;
  border: 1px solid var(--discord-strong-border);
  border-radius: 10px;
  padding: 13px 14px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.field textarea {
  resize: vertical;
}

.asset-section {
  display: grid;
  gap: 14px;
  padding-top: 24px;
  border-top: 1px solid var(--discord-border);
}

.asset-section p {
  margin: 6px 0 0;
  color: var(--discord-text-muted);
}

.asset-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.primary-button,
.secondary-button,
.danger-button,
.save-button,
.members-panel header button,
.remove-button {
  min-height: 38px;
  border-radius: 9px;
  padding: 0 14px;
  font-weight: 900;
}

.primary-button,
.save-button {
  background: var(--discord-brand);
  color: white;
}

.primary-button:hover,
.save-button:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

.secondary-button {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.danger-button,
.remove-button {
  background: rgba(237, 66, 69, 0.12);
  color: #ff8b8d;
}

.save-button {
  justify-self: start;
  min-width: 120px;
}

.save-button:disabled,
.member-row select:disabled,
.remove-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.feedback {
  margin: 0;
  color: var(--discord-text-muted);
}

.server-preview {
  border: 1px solid var(--discord-border);
  border-radius: 18px;
  background: var(--discord-elevated);
  overflow: hidden;
  box-shadow: var(--discord-shadow);
}

.preview-banner {
  height: 132px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.16), transparent),
    linear-gradient(135deg, #f0ad73, #ffd986);
  background-size: cover;
  background-position: center;
}

.preview-body {
  position: relative;
  display: grid;
  gap: 10px;
  padding: 52px 24px 26px;
}

.preview-icon {
  position: absolute;
  top: -52px;
  left: 24px;
  width: 92px;
  height: 92px;
  border: 6px solid var(--discord-elevated);
  border-radius: 26px;
  display: grid;
  place-items: center;
  background: #f2f3f5;
  color: #1e1f22;
  font-size: 30px;
  font-weight: 900;
  overflow: hidden;
}

.preview-body h2,
.preview-body p {
  margin: 0;
}

.preview-body p,
.preview-body small {
  color: var(--discord-text-muted);
  line-height: 1.45;
}

.role-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.role-card {
  display: grid;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--discord-border);
  border-radius: 12px;
  background: var(--discord-surface);
}

.role-card strong {
  font-size: 17px;
}

.role-card span {
  color: var(--discord-text-muted);
  font-size: 13px;
}

.role-card.owner { border-color: rgba(240, 178, 50, 0.48); }
.role-card.admin { border-color: rgba(88, 101, 242, 0.52); }
.role-card.member { border-color: rgba(59, 165, 93, 0.45); }

.role-card {
  position: relative;
  min-height: 104px;
  align-content: end;
  overflow: hidden;
  border-radius: 10px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--discord-elevated) 88%, transparent), var(--discord-surface));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.role-card::before {
  content: '';
  position: absolute;
  left: 16px;
  top: 16px;
  width: 34px;
  height: 6px;
  border-radius: 999px;
  background: currentColor;
  opacity: 0.6;
}

.role-card.owner {
  color: #f0b232;
  background: linear-gradient(180deg, rgba(240, 178, 50, 0.12), var(--discord-surface));
}

.role-card.admin {
  color: #98a1ff;
  background: linear-gradient(180deg, rgba(88, 101, 242, 0.14), var(--discord-surface));
}

.role-card.member {
  color: var(--discord-green);
  background: linear-gradient(180deg, rgba(59, 165, 93, 0.12), var(--discord-surface));
}

.role-card strong {
  color: var(--discord-text);
}

.role-card span {
  color: var(--discord-text-muted);
}

.members-panel {
  display: grid;
  gap: 8px;
  border: 1px solid var(--discord-border);
  border-radius: 14px;
  background: var(--discord-surface);
  overflow: hidden;
}

.members-panel {
  border-radius: 10px;
  background: color-mix(in srgb, var(--discord-surface) 86%, var(--discord-elevated));
}

.members-panel header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px;
  border-bottom: 1px solid var(--discord-border);
}

.members-panel header {
  min-height: 62px;
  padding: 16px 18px;
  background: var(--discord-elevated);
}

.members-panel header button {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.members-panel header button {
  border-radius: 8px;
  background: var(--discord-muted-surface);
}

.member-status {
  padding: 28px 16px;
  color: var(--discord-text-faint);
  text-align: center;
}

.member-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 150px auto;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
}

.member-row {
  min-height: 66px;
  padding: 12px 18px;
  transition: background-color 120ms ease;
}

.member-row:hover {
  background: var(--discord-hover);
}

.member-row + .member-row {
  border-top: 1px solid var(--discord-border);
}

.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-weight: 900;
  overflow: hidden;
}

.member-avatar {
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--discord-bg) 70%, transparent);
}

.member-row select {
  min-height: 40px;
  border-radius: 8px;
  background: var(--discord-elevated);
}

.remove-button {
  border-radius: 8px;
}

.member-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.member-copy strong,
.member-copy span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-copy span {
  color: var(--discord-text-faint);
  font-size: 13px;
}

.owner-badge {
  justify-self: start;
  padding: 8px 10px;
  border-radius: 999px;
  background: rgba(240, 178, 50, 0.15);
  color: #f0b232;
  font-weight: 900;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

@keyframes fade-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@media (max-width: 980px) {
  .settings-overlay {
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .overview-grid,
  .role-grid {
    grid-template-columns: 1fr;
  }

  .settings-content {
    width: calc(100% - 48px);
    padding-left: 24px;
  }
}
</style>
