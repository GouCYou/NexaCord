<template>
  <aside class="member-sidebar" aria-label="服务器成员列表">
    <div v-if="isLoading" class="member-state">正在加载成员……</div>

    <template v-else>
      <section v-if="onlineMembers.length > 0" class="member-group">
        <h2>在线 — {{ onlineMembers.length }}</h2>
        <button v-for="member in onlineMembers" :key="member.id" class="member-row" type="button" @click="openUserPopover(member, $event)">
          <div class="member-avatar">
            <img :src="member.user.avatarUrl || defaultAvatarUrl" :alt="memberName(member)" />
            <i :class="['status-dot', member.user.status || 'online']"></i>
          </div>
          <span>
            <strong>{{ memberName(member) }}</strong>
            <small>{{ roleLabel(member.role) }}</small>
          </span>
        </button>
      </section>

      <section v-if="offlineMembers.length > 0" class="member-group">
        <h2>离线 — {{ offlineMembers.length }}</h2>
        <button v-for="member in offlineMembers" :key="member.id" class="member-row offline" type="button" @click="openUserPopover(member, $event)">
          <div class="member-avatar">
            <img :src="member.user.avatarUrl || defaultAvatarUrl" :alt="memberName(member)" />
          </div>
          <span>
            <strong>{{ memberName(member) }}</strong>
            <small>{{ roleLabel(member.role) }}</small>
          </span>
        </button>
      </section>

      <div v-if="members.length === 0" class="member-state">这个服务器还没有成员。</div>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useServerStore } from '../stores/serverStore';
import type { Member, MemberRole } from '../types';
import { usernameTag } from '../utils/userDisplay';

const props = defineProps<{
  serverId: number | null;
}>();

const serverStore = useServerStore();
const members = ref<Member[]>([]);
const isLoading = ref(false);
const defaultAvatarUrl = '/logo.png';

const onlineMembers = computed(() =>
  members.value.filter((member) => ['online', 'away', 'dnd'].includes(member.user.status || 'online'))
);

const offlineMembers = computed(() =>
  members.value.filter((member) => !['online', 'away', 'dnd'].includes(member.user.status || 'online'))
);

const memberName = (member: Member) =>
  member.nickname?.trim() || member.user.displayName?.trim() || usernameTag(member.user.username);

const openUserPopover = (member: Member, event: MouseEvent) => {
  window.dispatchEvent(new CustomEvent('nexacord:open-user-popover', {
    detail: {
      user: member.user,
      role: member.role,
      serverName: serverStore.currentServer?.name,
      x: event.clientX,
      y: event.clientY,
    },
  }));
};

const roleLabel = (role: MemberRole) => {
  const labels: Record<MemberRole, string> = {
    OWNER: '服主',
    ADMIN: '管理员',
    MODERATOR: '协管',
    MEMBER: '成员',
  };

  return labels[role] || '成员';
};

const loadMembers = async () => {
  if (!props.serverId) {
    members.value = [];
    return;
  }

  isLoading.value = true;
  try {
    members.value = await serverStore.fetchServerMembers(props.serverId);
  } finally {
    isLoading.value = false;
  }
};

onMounted(loadMembers);
watch(() => props.serverId, loadMembers);
</script>

<style scoped>
.member-sidebar {
  width: 280px;
  min-width: 280px;
  height: 100%;
  min-height: 0;
  overflow-y: auto;
  padding: 18px 10px;
  border-left: 1px solid var(--discord-border);
  background: var(--discord-bg);
}

.member-group + .member-group {
  margin-top: 18px;
}

.member-group h2 {
  margin: 0 8px 8px;
  color: var(--discord-text-faint);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.member-row {
  width: 100%;
  min-height: 46px;
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 8px;
  background: transparent;
  color: var(--discord-text-muted);
  text-align: left;
}

.member-row:hover {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.member-row.offline {
  opacity: 0.45;
}

.member-avatar {
  position: relative;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-size: 13px;
  font-weight: 900;
}

.member-avatar img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.member-avatar span {
  line-height: 1;
}

.status-dot {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 10px;
  height: 10px;
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

.member-row span {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.member-row strong,
.member-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-row strong {
  font-size: 14px;
}

.member-row small {
  color: var(--discord-text-faint);
  font-size: 12px;
}

.member-state {
  padding: 18px 10px;
  color: var(--discord-text-faint);
  font-size: 13px;
}

@media (max-width: 1180px) {
  .member-sidebar {
    display: none;
  }
}
</style>
