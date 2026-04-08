<template>
  <div class="auth-shell">
    <div class="auth-card">
      <section class="brand-panel">
        <span class="brand-kicker">NexaCord</span>
        <h1>回到你的频道和社区</h1>
        <p>保留接近 Discord 的服务器栏、频道栏和实时消息流，同时运行在你自己的技术栈上。</p>
      </section>

      <section class="form-panel">
        <header class="form-header">
          <h2>欢迎回来</h2>
          <p>使用用户名或邮箱登录。</p>
        </header>

        <div v-if="error" class="error-message">{{ error }}</div>

        <form class="form-body" @submit.prevent="handleLogin">
          <label class="field">
            <span>用户名或邮箱</span>
            <input
              id="usernameOrEmail"
              v-model="loginForm.usernameOrEmail"
              type="text"
              placeholder="请输入用户名或邮箱"
              required
              autofocus
            />
          </label>

          <label class="field">
            <span>密码</span>
            <input
              id="password"
              v-model="loginForm.password"
              type="password"
              placeholder="请输入密码"
              required
            />
          </label>

          <button class="primary-button" type="submit" :disabled="isLoading">
            {{ isLoading ? '登录中...' : '登录' }}
          </button>
        </form>

        <footer class="form-footer">
          <span>还没有账号？</span>
          <router-link to="/register">立即注册</router-link>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '../stores/userStore';
import type { LoginRequest } from '../types';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const loginForm = ref<LoginRequest>({
  usernameOrEmail: '',
  password: '',
});

const { isLoading, error } = storeToRefs(userStore);

const handleLogin = async () => {
  const success = await userStore.login(loginForm.value);
  if (!success) {
    return;
  }

  const redirect = route.query.redirect as string | undefined;
  router.push(redirect || '/');
};
</script>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
}

.auth-card {
  width: min(980px, 100%);
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 24px;
  overflow: hidden;
  background: rgba(17, 18, 20, 0.72);
  box-shadow: var(--discord-shadow);
  backdrop-filter: blur(18px);
}

.brand-panel,
.form-panel {
  padding: 40px;
}

.brand-panel {
  display: grid;
  align-content: center;
  gap: 16px;
  background:
    radial-gradient(circle at top left, rgba(88, 101, 242, 0.35), transparent 40%),
    linear-gradient(160deg, rgba(88, 101, 242, 0.18), rgba(17, 18, 20, 0.1)),
    #202226;
}

.brand-kicker {
  color: #aeb6ff;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.brand-panel h1 {
  margin: 0;
  font-size: clamp(34px, 4.2vw, 50px);
  line-height: 0.96;
}

.brand-panel p {
  margin: 0;
  max-width: 420px;
  color: var(--discord-text-muted);
  font-size: 16px;
}

.form-panel {
  display: grid;
  align-content: center;
  gap: 20px;
  background: rgba(43, 45, 49, 0.96);
}

.form-header h2 {
  margin: 0;
  font-size: 28px;
}

.form-header p {
  margin: 6px 0 0;
  color: var(--discord-text-muted);
}

.form-body {
  display: grid;
  gap: 16px;
}

.field {
  display: grid;
  gap: 8px;
}

.field span {
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.field input {
  width: 100%;
  padding: 14px 16px;
  border: 1px solid rgba(0, 0, 0, 0.32);
  border-radius: 12px;
  background: #1e1f22;
  color: var(--discord-text);
}

.error-message {
  padding: 12px 14px;
  border: 1px solid rgba(237, 66, 69, 0.3);
  border-radius: 12px;
  background: rgba(237, 66, 69, 0.1);
  color: #ff8b8d;
  font-size: 14px;
}

.primary-button {
  width: 100%;
  padding: 14px 16px;
  border-radius: 12px;
  background: var(--discord-brand);
  color: white;
  font-weight: 800;
}

.primary-button:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

.primary-button:disabled {
  opacity: 0.64;
  cursor: not-allowed;
}

.form-footer {
  display: flex;
  gap: 6px;
  color: var(--discord-text-muted);
  font-size: 14px;
}

@media (max-width: 860px) {
  .auth-card {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    padding-bottom: 24px;
  }
}
</style>
