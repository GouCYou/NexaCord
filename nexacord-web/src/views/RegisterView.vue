<template>
  <div class="auth-shell">
    <div class="auth-card">
      <section class="brand-panel">
        <div class="brand-mark">
          <img src="/logo.png" alt="Nexacord" />
        </div>
        <span class="brand-kicker">Nexacord</span>
        <h1>建立属于你的实时交流社区</h1>
        <p>创建账号后即可进入频道、好友和语音空间。</p>
        <div class="brand-preview" aria-hidden="true">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </section>

      <section class="form-panel">
        <header class="form-header">
          <div>
            <h2>创建账号</h2>
            <p>填写信息后进入 Nexacord</p>
          </div>
        </header>

        <div v-if="error || localError" class="error-message">{{ localError || error }}</div>
        <div v-if="localNotice" class="notice-message">{{ localNotice }}</div>

        <form class="form-body" @submit.prevent="handleRegister">
          <label class="field">
            <span>用户名</span>
            <input
              v-model="registerForm.username"
              type="text"
              placeholder="请输入用户名"
              autocomplete="username"
              pattern="[A-Za-z0-9_]+"
              maxlength="30"
              required
              autofocus
            />
          </label>
          <p v-if="usernameError" class="error-inline">{{ usernameError }}</p>
          <p v-else class="hint-inline">用户名只能包含英文字母、数字和下划线。</p>

          <label class="field">
            <span>昵称</span>
            <input
              v-model="registerForm.displayName"
              type="text"
              placeholder="请输入显示昵称"
              maxlength="32"
            />
          </label>

          <label class="field">
            <span>邮箱</span>
            <input v-model="registerForm.email" type="email" placeholder="请输入邮箱地址" required />
          </label>

          <label class="field">
            <span>邮箱验证码</span>
            <div class="code-row">
              <input
                v-model="registerForm.verificationCode"
                type="text"
                inputmode="numeric"
                placeholder="输入 6 位验证码"
                required
              />
              <button
                class="secondary-button"
                type="button"
                :disabled="isSendingCode || !registerForm.email.trim()"
                @click="sendRegisterCode"
              >
                {{ isSendingCode ? '发送中' : '发送验证码' }}
              </button>
            </div>
          </label>

          <label class="field">
            <span>密码</span>
            <input
              v-model="registerForm.password"
              type="password"
              placeholder="8-20 位，包含大小写、数字和符号"
              minlength="8"
              maxlength="20"
              required
            />
          </label>

          <label class="field">
            <span>确认密码</span>
            <input
              v-model="confirmPassword"
              type="password"
              placeholder="再次输入密码"
              minlength="8"
              maxlength="20"
              required
            />
          </label>

          <p v-if="passwordError" class="error-inline">{{ passwordError }}</p>

          <p v-if="passwordsMismatch" class="error-inline">
            两次输入的密码不一致。
          </p>

          <button
            class="primary-button"
            type="submit"
            :disabled="isLoading || !canSubmit"
          >
            {{ isLoading ? '注册中……' : '注册并进入' }}
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
import { computed, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { useRouter } from 'vue-router';
import authService from '../services/authService';
import { useUserStore } from '../stores/userStore';
import type { RegisterRequest } from '../types';
import { getDeviceName } from '../utils/deviceInfo';
import { getPasswordStrengthError } from '../utils/passwordPolicy';

const router = useRouter();
const userStore = useUserStore();

const registerForm = ref<RegisterRequest>({
  username: '',
  displayName: '',
  email: '',
  password: '',
  verificationCode: '',
});

const confirmPassword = ref('');
const isSendingCode = ref(false);
const localError = ref('');
const localNotice = ref('');
const { isLoading, error } = storeToRefs(userStore);

const usernameError = computed(() => {
  const username = registerForm.value.username.trim();
  if (!username) {
    return '';
  }

  if (username.length < 3 || username.length > 30) {
    return '用户名长度需要在 3 到 30 个字符之间。';
  }

  if (!/^[A-Za-z0-9_]+$/.test(username)) {
    return '用户名只能使用英文字母、数字和下划线。';
  }

  return '';
});

const passwordError = computed(() => getPasswordStrengthError(registerForm.value.password));
const passwordsMismatch = computed(
  () => Boolean(registerForm.value.password || confirmPassword.value) && registerForm.value.password !== confirmPassword.value
);
const canSubmit = computed(
  () =>
    Boolean(registerForm.value.username.trim()) &&
    Boolean(registerForm.value.email.trim()) &&
    Boolean(registerForm.value.verificationCode.trim()) &&
    Boolean(registerForm.value.password) &&
    !usernameError.value &&
    !passwordError.value &&
    !passwordsMismatch.value
);

const sendRegisterCode = async () => {
  localError.value = '';
  localNotice.value = '';
  isSendingCode.value = true;

  try {
    await authService.sendEmailCode({
      email: registerForm.value.email.trim(),
      purpose: 'REGISTER',
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

const handleRegister = async () => {
  localError.value = '';
  localNotice.value = '';
  if (!canSubmit.value) {
    return;
  }

  const success = await userStore.register({
    ...registerForm.value,
    username: registerForm.value.username.trim(),
    displayName: registerForm.value.displayName?.trim(),
    email: registerForm.value.email.trim(),
    verificationCode: registerForm.value.verificationCode.trim(),
    deviceName: getDeviceName(),
  });
  if (success) {
    router.push('/');
  }
};
</script>

<style scoped>
.auth-shell {
  min-height: 100dvh;
  display: grid;
  place-items: center;
  padding: 20px;
  overflow-y: auto;
}

.auth-card {
  width: min(980px, 100%);
  max-height: calc(100dvh - 40px);
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
  background: var(--discord-brand);
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
  align-content: start;
  gap: 20px;
  overflow-y: auto;
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

.error-message,
.error-inline,
.hint-inline {
  color: #ff8b8d;
  font-size: 14px;
}

.error-message,
.notice-message {
  padding: 12px 14px;
  border-radius: 12px;
  font-size: 14px;
}

.error-message {
  border: 1px solid rgba(237, 66, 69, 0.3);
  background: rgba(237, 66, 69, 0.1);
}

.notice-message {
  border: 1px solid rgba(59, 165, 93, 0.28);
  background: rgba(59, 165, 93, 0.1);
  color: var(--discord-green);
}

.error-inline,
.hint-inline {
  margin: 0;
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

.auth-shell {
  place-items: center;
  padding: clamp(12px, 2.4dvh, 24px);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(244, 247, 251, 0.98)),
    var(--discord-bg);
}

.auth-card {
  width: min(1040px, calc(100vw - 24px));
  height: min(720px, calc(100dvh - clamp(24px, 4.8dvh, 48px)));
  min-height: 0;
  max-height: none;
  grid-template-columns: minmax(360px, 0.96fr) minmax(360px, 0.9fr);
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.16);
}

.brand-panel {
  position: relative;
  align-content: end;
  min-height: 0;
  padding: clamp(28px, 5dvh, 48px);
  color: #ffffff;
  background:
    linear-gradient(145deg, rgba(9, 14, 28, 0.94), rgba(25, 40, 76, 0.9) 58%, rgba(23, 93, 112, 0.88)),
    #111827;
}

.brand-panel::before {
  content: '';
  position: absolute;
  inset: 0;
  opacity: 0.18;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.22) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.18) 1px, transparent 1px);
  background-size: 44px 44px;
}

.brand-panel > * {
  position: relative;
}

.brand-mark {
  width: 56px;
  height: 56px;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.12);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.18);
  overflow: hidden;
}

.brand-mark img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.brand-kicker {
  color: rgba(255, 255, 255, 0.78);
  letter-spacing: 0.16em;
}

.brand-panel h1 {
  max-width: 500px;
  color: #ffffff;
  font-size: clamp(34px, 5.8dvh, 60px);
  line-height: 1;
}

.brand-panel p {
  max-width: 360px;
  color: rgba(255, 255, 255, 0.78);
}

.brand-preview {
  width: min(360px, 100%);
  display: grid;
  gap: 10px;
  margin-top: 18px;
  padding: 14px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.1);
}

.brand-preview span {
  height: 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.42);
}

.brand-preview span:nth-child(2) {
  width: 72%;
  background: rgba(125, 211, 252, 0.54);
}

.brand-preview span:nth-child(3) {
  width: 46%;
  background: rgba(134, 239, 172, 0.52);
}

.form-panel {
  min-height: 0;
  padding: 40px 48px;
  overflow-y: auto;
  background: rgba(248, 250, 252, 0.88);
  color: #111827;
}

.form-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.form-header h2 {
  color: #111827;
}

.form-header p,
.field span,
.form-footer {
  color: #64748b;
}

.form-body {
  gap: 13px;
}

.field {
  gap: 6px;
}

.field input {
  border-color: rgba(15, 23, 42, 0.1);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  color: #111827;
}

.primary-button {
  border-radius: 14px;
  background: #2563eb;
  box-shadow: 0 12px 28px rgba(37, 99, 235, 0.22);
}

.primary-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.secondary-button {
  border-radius: 14px;
  background: #e2e8f0;
  color: #111827;
}

.form-footer {
  position: sticky;
  bottom: -40px;
  padding-top: 8px;
  background: linear-gradient(180deg, transparent, rgba(248, 250, 252, 0.96) 38%);
}

@media (max-width: 860px) {
  .auth-card {
    grid-template-columns: 1fr;
    height: auto;
    max-height: none;
  }

  .brand-panel {
    min-height: 260px;
    padding-bottom: 24px;
  }
}

@media (max-height: 760px) {
  .auth-card {
    height: calc(100dvh - 24px);
  }

  .brand-panel,
  .form-panel {
    padding: 28px;
  }

  .brand-panel {
    gap: 10px;
  }

  .brand-panel h1 {
    font-size: clamp(30px, 5.2dvh, 44px);
  }

  .brand-panel p {
    font-size: 14px;
  }

  .brand-preview {
    width: min(320px, 100%);
    margin-top: 4px;
    padding: 12px;
  }
}

@media (max-height: 640px) and (min-width: 861px) {
  .brand-panel h1 {
    font-size: 30px;
  }

  .brand-mark {
    width: 48px;
    height: 48px;
    border-radius: 14px;
  }

  .brand-preview {
    gap: 8px;
    padding: 10px;
  }

  .brand-preview span {
    height: 8px;
  }

  .form-panel {
    gap: 14px;
  }
}
</style>
