<template>
  <div class="auth-shell">
    <div class="auth-card">
      <section class="brand-panel">
        <span class="brand-kicker">创建</span>
        <h1>建立属于你的 Discord 风格社区</h1>
        <p>注册后即可创建服务器和频道，并在熟悉的三栏布局里使用实时消息功能。</p>
      </section>

      <section class="form-panel">
        <header class="form-header">
          <h2>创建账号</h2>
          <p>填写基础信息后即可进入 NexaCord。</p>
        </header>

        <div v-if="error" class="error-message">{{ error }}</div>

        <form class="form-body" @submit.prevent="handleRegister">
          <label class="field">
            <span>用户名</span>
            <input v-model="registerForm.username" type="text" placeholder="请输入用户名" required autofocus />
          </label>

          <label class="field">
            <span>邮箱</span>
            <input v-model="registerForm.email" type="email" placeholder="请输入邮箱地址" required />
          </label>

          <label class="field">
            <span>密码</span>
            <input
              v-model="registerForm.password"
              type="password"
              placeholder="至少 6 位字符"
              minlength="6"
              required
            />
          </label>

          <label class="field">
            <span>确认密码</span>
            <input
              v-model="confirmPassword"
              type="password"
              placeholder="再次输入密码"
              minlength="6"
              required
            />
          </label>

          <p v-if="registerForm.password !== confirmPassword" class="error-inline">
            两次输入的密码不一致。
          </p>

          <button
            class="primary-button"
            type="submit"
            :disabled="isLoading || registerForm.password !== confirmPassword"
          >
            {{ isLoading ? '注册中...' : '注册并进入' }}
          </button>
        </form>

        <footer class="form-footer">
          <span>已经有账号？</span>
          <router-link to="/login">立即登录</router-link>
        </footer>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useRouter } from 'vue-router';
import { useUserStore } from '../stores/userStore';
import type { RegisterRequest } from '../types';

const router = useRouter();
const userStore = useUserStore();

const registerForm = ref<RegisterRequest>({
  username: '',
  email: '',
  password: '',
});

const confirmPassword = ref('');
const { isLoading, error } = storeToRefs(userStore);

const handleRegister = async () => {
  if (registerForm.value.password !== confirmPassword.value) {
    return;
  }

  const success = await userStore.register(registerForm.value);
  if (success) {
    router.push('/');
  }
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
    radial-gradient(circle at top left, rgba(59, 165, 93, 0.28), transparent 34%),
    radial-gradient(circle at bottom right, rgba(88, 101, 242, 0.3), transparent 40%),
    #202226;
}

.brand-kicker {
  color: #8effbf;
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

.error-message,
.error-inline {
  color: #ff8b8d;
  font-size: 14px;
}

.error-message {
  padding: 12px 14px;
  border: 1px solid rgba(237, 66, 69, 0.3);
  border-radius: 12px;
  background: rgba(237, 66, 69, 0.1);
}

.error-inline {
  margin: 0;
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
