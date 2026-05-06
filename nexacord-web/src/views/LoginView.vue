<template>
  <div class="auth-shell">
    <div class="auth-card">
      <section class="brand-panel">
        <span class="brand-kicker">Nexacord</span>
        <h1>回到你的频道和社区</h1>
        <p>保留熟悉的服务器栏、频道栏和实时消息流，同时运行在你自己的技术栈上。</p>
      </section>

      <section class="form-panel">
        <header class="form-header">
          <h2>欢迎回来</h2>
          <p>使用用户名或邮箱登录。</p>
        </header>

        <div v-if="error || localError" class="error-message">{{ localError || error }}</div>
        <div v-if="localNotice" class="notice-message">{{ localNotice }}</div>

        <form v-if="authMode === 'login'" class="form-body" @submit.prevent="handleLogin">
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

          <button class="text-button" type="button" @click="authMode = 'reset'">忘记密码？</button>

          <button class="primary-button" type="submit" :disabled="isLoading">
            {{ isLoading ? '登录中……' : '登录' }}
          </button>
        </form>

        <form v-else class="form-body" @submit.prevent="handleResetPassword">
          <label class="field">
            <span>邮箱</span>
            <input v-model="resetForm.email" type="email" placeholder="请输入注册邮箱" required autofocus />
          </label>

          <label class="field">
            <span>邮箱验证码</span>
            <div class="code-row">
              <input v-model="resetForm.verificationCode" type="text" inputmode="numeric" placeholder="输入 6 位验证码" required />
              <button
                class="secondary-button"
                type="button"
                :disabled="isSendingCode || !resetForm.email.trim()"
                @click="sendResetCode"
              >
                {{ isSendingCode ? '发送中' : '发送验证码' }}
              </button>
            </div>
          </label>

          <label class="field">
            <span>新密码</span>
            <input
              v-model="resetForm.password"
              type="password"
              placeholder="8-20 位，包含大小写、数字和符号"
              minlength="8"
              maxlength="20"
              required
            />
          </label>

          <label class="field">
            <span>确认新密码</span>
            <input
              v-model="resetConfirmPassword"
              type="password"
              placeholder="再次输入新密码"
              minlength="8"
              maxlength="20"
              required
            />
          </label>

          <p v-if="resetPasswordError" class="error-inline">{{ resetPasswordError }}</p>
          <p v-else class="hint-inline">{{ passwordPolicyText }}</p>
          <p v-if="resetPasswordsMismatch" class="error-inline">两次输入的密码不一致。</p>

          <button class="primary-button" type="submit" :disabled="isResetting || !canResetPassword">
            {{ isResetting ? '重置中……' : '重置密码' }}
          </button>
          <button class="text-button" type="button" @click="authMode = 'login'">返回登录</button>
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
import { computed, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import authService from '../services/authService';
import { useUserStore } from '../stores/userStore';
import type { LoginRequest } from '../types';
import { getPasswordStrengthError, passwordPolicyText } from '../utils/passwordPolicy';

const router = useRouter();
const route = useRoute();
const userStore = useUserStore();

const loginForm = ref<LoginRequest>({
  usernameOrEmail: '',
  password: '',
});

const authMode = ref<'login' | 'reset'>('login');
const isSendingCode = ref(false);
const isResetting = ref(false);
const localError = ref('');
const localNotice = ref('');
const resetForm = ref({
  email: '',
  verificationCode: '',
  password: '',
});
const resetConfirmPassword = ref('');
const { isLoading, error } = storeToRefs(userStore);

const resetPasswordError = computed(() => getPasswordStrengthError(resetForm.value.password));
const resetPasswordsMismatch = computed(
  () => Boolean(resetForm.value.password || resetConfirmPassword.value) && resetForm.value.password !== resetConfirmPassword.value
);
const canResetPassword = computed(
  () =>
    Boolean(resetForm.value.email.trim()) &&
    Boolean(resetForm.value.verificationCode.trim()) &&
    Boolean(resetForm.value.password) &&
    !resetPasswordError.value &&
    !resetPasswordsMismatch.value
);

const handleLogin = async () => {
  localError.value = '';
  localNotice.value = '';
  const success = await userStore.login(loginForm.value);
  if (!success) {
    return;
  }

  const redirect = route.query.redirect as string | undefined;
  router.push(redirect || '/');
};

const sendResetCode = async () => {
  localError.value = '';
  localNotice.value = '';
  isSendingCode.value = true;

  try {
    await authService.sendEmailCode({
      email: resetForm.value.email.trim(),
      purpose: 'RESET_PASSWORD',
    });
    localNotice.value = '验证码已发送，请检查邮箱。';
  } catch (err: any) {
    localError.value =
      err.response?.data?.error ||
      err.response?.data?.message ||
      '发送验证码失败，请稍后再试。';
  } finally {
    isSendingCode.value = false;
  }
};

const handleResetPassword = async () => {
  localError.value = '';
  localNotice.value = '';
  if (!canResetPassword.value) {
    return;
  }

  isResetting.value = true;

  try {
    await authService.resetPassword({
      email: resetForm.value.email.trim(),
      verificationCode: resetForm.value.verificationCode.trim(),
      password: resetForm.value.password,
    });
    localNotice.value = '密码已重置，现在可以使用新密码登录。';
    authMode.value = 'login';
    loginForm.value.usernameOrEmail = resetForm.value.email.trim();
    resetForm.value = { email: '', verificationCode: '', password: '' };
    resetConfirmPassword.value = '';
  } catch (err: any) {
    localError.value =
      err.response?.data?.error ||
      err.response?.data?.message ||
      '重置密码失败，请检查验证码后重试。';
  } finally {
    isResetting.value = false;
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
  border: 1px solid var(--discord-border);
  border-radius: 24px;
  overflow: hidden;
  background: color-mix(in srgb, var(--discord-elevated) 82%, transparent);
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
  color: white;
  background:
    radial-gradient(circle at top left, rgba(88, 101, 242, 0.35), transparent 40%),
    linear-gradient(160deg, rgba(88, 101, 242, 0.18), transparent),
    var(--discord-surface-soft);
}

.brand-kicker {
  color: rgba(255, 255, 255, 0.9);
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
  color: rgba(255, 255, 255, 0.86);
  font-size: 16px;
}

.form-panel {
  display: grid;
  align-content: center;
  gap: 20px;
  background: color-mix(in srgb, var(--discord-surface) 96%, transparent);
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
  border: 1px solid var(--discord-border);
  border-radius: 12px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
}

.secondary-button {
  min-width: 116px;
  border-radius: 12px;
  background: var(--discord-muted-surface);
  color: var(--discord-text);
  font-weight: 800;
}

.secondary-button:hover:not(:disabled) {
  background: var(--discord-hover-strong);
}

.text-button {
  justify-self: start;
  padding: 0;
  background: transparent;
  color: var(--discord-brand);
  font-size: 14px;
  font-weight: 800;
}

.text-button:hover {
  color: var(--discord-link-hover);
}

.error-message {
  padding: 12px 14px;
  border: 1px solid rgba(237, 66, 69, 0.3);
  border-radius: 12px;
  background: rgba(237, 66, 69, 0.1);
  color: #ff8b8d;
  font-size: 14px;
}

.notice-message {
  padding: 12px 14px;
  border: 1px solid rgba(59, 165, 93, 0.28);
  border-radius: 12px;
  background: rgba(59, 165, 93, 0.1);
  color: var(--discord-green);
  font-size: 14px;
}

.error-inline,
.hint-inline {
  margin: 0;
  font-size: 14px;
  line-height: 1.45;
}

.error-inline {
  color: #ff8b8d;
}

.hint-inline {
  color: var(--discord-text-faint);
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
