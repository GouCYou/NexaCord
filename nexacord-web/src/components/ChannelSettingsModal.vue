<template>
  <div class="settings-shell" role="dialog" aria-modal="true" aria-label="频道设置">
    <aside class="settings-nav">
      <strong>{{ channel.type === 'VOICE' ? channel.name : `# ${channel.name}` }} {{ channelLabel }}</strong>

      <button
        v-for="item in navItems"
        :key="item.value"
        type="button"
        :class="{ active: activeTab === item.value, danger: item.value === 'delete' }"
        @click="activeTab = item.value"
      >
        <component :is="item.icon" :size="18" aria-hidden="true" />
        <span>{{ item.label }}</span>
      </button>
    </aside>

    <main class="settings-content">
      <button class="settings-close" type="button" aria-label="关闭频道设置" @click="$emit('close')">
        <X :size="26" aria-hidden="true" />
        <small>ESC</small>
      </button>

      <form v-if="activeTab === 'overview'" class="settings-panel" @submit.prevent="saveChannel">
        <header>
          <h2>概况</h2>
          <p>调整频道名称、主题和基础访问体验。</p>
        </header>

        <label class="field">
          <span>频道名称</span>
          <div class="input-with-icon">
            <Hash v-if="channel.type !== 'VOICE'" :size="20" aria-hidden="true" />
            <Volume2 v-else :size="20" aria-hidden="true" />
            <input v-model="form.name" type="text" maxlength="64" placeholder="频道名称" />
          </div>
        </label>

        <label class="field">
          <span>频道主题</span>
          <textarea v-model="form.topic" maxlength="1024" rows="7" placeholder="告诉大家如何使用该频道吧！"></textarea>
          <small>{{ 1024 - form.topic.length }}</small>
        </label>

        <label class="field">
          <span>慢速模式</span>
          <select v-model="slowMode">
            <option value="0">关</option>
            <option value="5">5 秒</option>
            <option value="10">10 秒</option>
            <option value="30">30 秒</option>
            <option value="60">1 分钟</option>
          </select>
          <small>后续接入权限细分后，可以把慢速模式落到服务端。</small>
        </label>

        <label class="switch-field">
          <span>
            <strong>有年龄限制的频道</strong>
            <small>用户需要确认自己已达到法定年龄才能查看此频道中的内容。</small>
          </span>
          <input v-model="form.nsfw" type="checkbox" />
        </label>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>

        <footer class="save-bar">
          <button class="secondary-button" type="button" @click="resetForm">重置</button>
          <button class="primary-button" type="submit" :disabled="isSaving || !form.name.trim()">
            <Save :size="18" aria-hidden="true" />
            <span>{{ isSaving ? '保存中……' : '保存更改' }}</span>
          </button>
        </footer>
      </form>

      <section v-else-if="activeTab === 'permissions'" class="settings-panel">
        <header>
          <h2>权限</h2>
          <p>服务器拥有者和管理员可以管理频道，成员可以查看和发送消息。</p>
        </header>

        <div class="permission-card">
          <Shield :size="26" aria-hidden="true" />
          <span>
            <strong>基础权限组</strong>
            <small>服主拥有全部权限，管理员可以管理频道和邀请成员，成员保留基础使用权限。</small>
          </span>
        </div>

        <div class="permission-grid">
          <article>
            <strong>服主</strong>
            <p>管理服务器、成员、频道和邀请。</p>
          </article>
          <article>
            <strong>管理员</strong>
            <p>创建、编辑和删除频道。</p>
          </article>
          <article>
            <strong>成员</strong>
            <p>查看频道、发送消息、加入语音。</p>
          </article>
        </div>
      </section>

      <section v-else-if="activeTab === 'invites'" class="settings-panel">
        <header>
          <h2>邀请</h2>
          <p>向好友发送邀请，或者复制服务器邀请链接。</p>
        </header>

        <button class="wide-action" type="button" @click="openInvite">
          <UserPlus :size="20" aria-hidden="true" />
          <span>邀请朋友加入服务器</span>
        </button>
      </section>

      <section v-else-if="activeTab === 'integrations'" class="settings-panel">
        <header>
          <h2>整合</h2>
          <p>这里预留给机器人、Webhook 和频道自动化。</p>
        </header>

        <div class="empty-card">
          <SlidersHorizontal :size="30" aria-hidden="true" />
          <strong>暂时没有整合</strong>
          <span>后续可以把通知机器人和 Webhook 放在这里。</span>
        </div>
      </section>

      <section v-else class="settings-panel">
        <header>
          <h2>删除频道</h2>
          <p>删除后，频道里的消息也会一起从列表中移除。这个操作不能撤销。</p>
        </header>

        <div class="delete-card">
          <Trash2 :size="28" aria-hidden="true" />
          <span>
            <strong>删除 #{{ channel.name }}</strong>
            <small>{{ confirmDelete ? '再次点击删除按钮，频道和消息会被移除。' : '如果仍要继续，请先确认删除这个频道。' }}</small>
          </span>
          <button type="button" :class="{ confirming: confirmDelete }" :disabled="isDeleting" @click="deleteChannel">
            {{ isDeleting ? '删除中……' : confirmDelete ? '确认删除频道' : '删除频道' }}
          </button>
        </div>

        <p v-if="errorMessage" class="form-error">{{ errorMessage }}</p>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import {
  Hash,
  Link2,
  Save,
  Shield,
  SlidersHorizontal,
  Trash2,
  UserPlus,
  Volume2,
  X,
} from 'lucide-vue-next';
import { useChannelStore } from '../stores/channelStore';
import type { Channel } from '../types';

const props = defineProps<{
  channel: Channel;
}>();

const emit = defineEmits<{
  close: [];
  saved: [];
  deleted: [];
}>();

type SettingsTab = 'overview' | 'permissions' | 'invites' | 'integrations' | 'delete';

const channelStore = useChannelStore();
const activeTab = ref<SettingsTab>('overview');
const isSaving = ref(false);
const isDeleting = ref(false);
const confirmDelete = ref(false);
const errorMessage = ref('');
const slowMode = ref('0');
const form = reactive({
  name: '',
  topic: '',
  nsfw: false,
});

const navItems = [
  { value: 'overview' as const, label: '概况', icon: SlidersHorizontal },
  { value: 'permissions' as const, label: '权限', icon: Shield },
  { value: 'invites' as const, label: '邀请', icon: UserPlus },
  { value: 'integrations' as const, label: '整合', icon: Link2 },
  { value: 'delete' as const, label: '删除频道', icon: Trash2 },
];

const channelLabel = computed(() => (props.channel.type === 'VOICE' ? '语音频道' : '文字频道'));

const resetForm = () => {
  form.name = props.channel.name || '';
  form.topic = props.channel.topic || '';
  form.nsfw = Boolean(props.channel.nsfw);
  errorMessage.value = '';
};

const saveChannel = async () => {
  errorMessage.value = '';
  isSaving.value = true;

  try {
    const success = await channelStore.updateChannel(props.channel.id, {
      name: form.name.trim(),
      topic: form.topic.trim(),
      nsfw: form.nsfw,
      type: props.channel.type,
      parentId: props.channel.parentId ?? null,
    });

    if (!success) {
      errorMessage.value = channelStore.error || '保存频道设置失败。';
      return;
    }

    emit('saved');
  } finally {
    isSaving.value = false;
  }
};

const deleteChannel = async () => {
  errorMessage.value = '';
  if (!confirmDelete.value) {
    confirmDelete.value = true;
    return;
  }

  isDeleting.value = true;

  try {
    const success = await channelStore.deleteChannel(props.channel.id);
    if (!success) {
      errorMessage.value = channelStore.error || '删除频道失败。';
      return;
    }

    emit('deleted');
  } finally {
    isDeleting.value = false;
  }
};

const openInvite = () => {
  window.dispatchEvent(new CustomEvent('nexacord:open-invite', {
    detail: { channelId: props.channel.id },
  }));
};

watch(() => props.channel.id, resetForm, { immediate: true });
watch(activeTab, () => {
  confirmDelete.value = false;
});
</script>

<style scoped>
.settings-shell {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  background: var(--discord-bg);
  color: var(--discord-text);
  animation: fade-in 120ms ease-out;
}

.settings-nav {
  display: grid;
  align-content: start;
  gap: 6px;
  padding: 76px 16px 24px 52px;
  background: var(--discord-surface);
}

.settings-nav strong {
  margin-bottom: 8px;
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.settings-nav button {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-radius: 6px;
  padding: 0 12px;
  background: transparent;
  color: var(--discord-text-muted);
  font-weight: 900;
  text-align: left;
}

.settings-nav button:hover,
.settings-nav button.active {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.settings-nav button.danger {
  color: var(--discord-red);
}

.settings-content {
  position: relative;
  min-width: 0;
  overflow-y: auto;
  padding: 76px min(160px, 8vw) 120px 54px;
}

.settings-close {
  position: fixed;
  top: 72px;
  right: 44px;
  display: grid;
  place-items: center;
  gap: 8px;
  background: transparent;
  color: var(--discord-text-faint);
  font-weight: 900;
}

.settings-close svg {
  width: 44px;
  height: 44px;
  padding: 8px;
  border: 2px solid currentColor;
  border-radius: 50%;
}

.settings-close:hover {
  color: var(--discord-text);
}

.settings-panel {
  width: min(760px, 100%);
  display: grid;
  gap: 24px;
}

.settings-panel header h2,
.settings-panel header p {
  margin: 0;
}

.settings-panel header h2 {
  font-size: 26px;
}

.settings-panel header p {
  margin-top: 8px;
  color: var(--discord-text-muted);
  line-height: 1.45;
}

.field {
  display: grid;
  gap: 10px;
}

.field > span,
.switch-field strong {
  color: var(--discord-text);
  font-weight: 900;
}

.input-with-icon {
  position: relative;
}

.input-with-icon svg {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--discord-text-faint);
}

.field input,
.field textarea,
.field select {
  width: 100%;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.field input,
.field select {
  min-height: 44px;
  padding: 0 14px;
}

.input-with-icon input {
  padding-left: 46px;
}

.field textarea {
  min-height: 190px;
  padding: 14px;
  resize: vertical;
}

.field small {
  justify-self: end;
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 800;
}

.switch-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--discord-border);
}

.switch-field span {
  display: grid;
  gap: 6px;
}

.switch-field small {
  color: var(--discord-text-muted);
  line-height: 1.45;
}

.switch-field input {
  width: 46px;
  height: 26px;
  accent-color: var(--discord-brand);
}

.save-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
  padding-top: 6px;
}

.primary-button,
.secondary-button,
.wide-action,
.delete-card button {
  min-height: 42px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 16px;
  font-weight: 900;
}

.primary-button {
  background: var(--discord-brand);
  color: white;
}

.secondary-button,
.wide-action {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.secondary-button:hover,
.wide-action:hover {
  background: var(--discord-hover-strong);
}

.permission-card,
.empty-card,
.delete-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 14px;
  padding: 18px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  background: var(--discord-surface);
}

.permission-card span,
.delete-card span,
.empty-card {
  display: grid;
  gap: 4px;
}

.permission-card small,
.delete-card small,
.empty-card span,
.permission-grid p {
  color: var(--discord-text-muted);
  line-height: 1.45;
}

.permission-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.permission-grid article {
  padding: 16px;
  border-radius: 8px;
  background: var(--discord-surface-soft);
}

.permission-grid p {
  margin: 8px 0 0;
  font-size: 13px;
}

.wide-action {
  width: fit-content;
}

.empty-card {
  justify-items: center;
  grid-template-columns: 1fr;
  min-height: 220px;
  text-align: center;
}

.delete-card {
  grid-template-columns: auto minmax(0, 1fr) auto;
  border-color: color-mix(in srgb, var(--discord-red) 35%, var(--discord-border));
}

.delete-card svg {
  color: var(--discord-red);
}

.delete-card button {
  background: var(--discord-red);
  color: white;
}

.delete-card button.confirming {
  box-shadow:
    0 0 0 2px color-mix(in srgb, var(--discord-red) 28%, transparent),
    0 12px 30px color-mix(in srgb, var(--discord-red) 24%, transparent);
}

.form-error {
  margin: 0;
  color: #ff8b8d;
  font-size: 13px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

@media (max-width: 820px) {
  .settings-shell {
    grid-template-columns: 1fr;
  }

  .settings-nav {
    display: none;
  }

  .settings-content {
    padding: 72px 22px 100px;
  }

  .settings-close {
    top: 18px;
    right: 18px;
  }

  .permission-grid,
  .delete-card {
    grid-template-columns: 1fr;
  }
}
</style>
