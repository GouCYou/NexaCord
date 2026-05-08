<template>
  <router-view />
  <Teleport to="body">
    <div v-if="sessionReplacementNotice" class="session-replaced-overlay" role="dialog" aria-modal="true">
      <section class="session-replaced-dialog">
        <span class="session-replaced-icon">!</span>
        <h2>账号已在其他设备登录</h2>
        <p>
          你的账号刚刚在
          <strong>{{ sessionReplacementNotice.deviceName || '另一台设备' }}</strong>
          登录，当前设备已自动下线。
        </p>
        <p class="session-replaced-warning">如果这不是你本人的操作，请尽快重新登录并修改密码。</p>
        <button type="button" @click="confirmSessionReplacement">确定</button>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { useRouter } from 'vue-router';
import { useUserStore } from './stores/userStore';

const router = useRouter();
const userStore = useUserStore();
const { sessionReplacementNotice } = storeToRefs(userStore);

const confirmSessionReplacement = () => {
  userStore.acknowledgeSessionReplacement();
  if (router.currentRoute.value.name !== 'Login') {
    void router.push('/login');
  }
};
</script>

<style scoped>
.session-replaced-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.42);
  backdrop-filter: blur(8px);
}

.session-replaced-dialog {
  width: min(420px, 100%);
  display: grid;
  gap: 14px;
  padding: 28px;
  border: 1px solid var(--discord-border);
  border-radius: 16px;
  background: var(--discord-surface);
  color: var(--discord-text);
  box-shadow: var(--discord-shadow);
}

.session-replaced-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #f2c94c;
  color: #2b2d31;
  font-weight: 900;
  font-size: 24px;
}

.session-replaced-dialog h2,
.session-replaced-dialog p {
  margin: 0;
}

.session-replaced-dialog h2 {
  font-size: 22px;
}

.session-replaced-dialog p {
  color: var(--discord-text-secondary);
  line-height: 1.55;
}

.session-replaced-dialog strong {
  color: var(--discord-text);
}

.session-replaced-warning {
  font-size: 14px;
}

.session-replaced-dialog button {
  height: 44px;
  margin-top: 6px;
  border-radius: 8px;
  background: var(--discord-brand);
  color: white;
  font-weight: 800;
}
</style>
