<template>
  <div v-if="isOpen" class="password-overlay" @click="closeModal">
    <section class="password-modal" role="dialog" aria-modal="true" aria-label="重置密码" @click.stop>
      <button class="close-button" type="button" aria-label="关闭弹窗" @click="closeModal">
        <X :size="22" aria-hidden="true" />
      </button>

      <div class="password-hero">
        <MailCheck :size="70" aria-hidden="true" />
      </div>

      <header class="password-heading">
        <h2>重置密码</h2>
        <p>我们会先向当前邮箱发送验证码，验证通过后再保存新密码。</p>
      </header>

      <form class="password-form" @submit.prevent="savePassword">
        <label class="field">
          <span>当前邮箱</span>
          <input :value="maskedEmail" type="email" disabled />
        </label>

        <label class="field">
          <span>邮箱验证码</span>
          <div class="code-row">
            <input v-model="form.verificationCode" type="text" inputmode="numeric" placeholder="输入 6 位验证码" required />
            <button class="secondary-button" type="button" :disabled="isSendingCode || !currentUser?.email" @click="sendCode">
              {{ isSendingCode ? '发送中' : codeSent ? '重新发送' : '发送验证码' }}
            </button>
          </div>
        </label>

        <label class="field">
          <span>新密码</span>
          <input v-model="form.password" type="password" minlength="8" maxlength="20" placeholder="8-20 位，包含大小写、数字和符号" required />
        </label>

        <label class="field">
          <span>确认新密码</span>
          <input v-model="confirmPassword" type="password" minlength="8" maxlength="20" placeholder="再次输入新密码" required />
        </label>

        <p v-if="passwordError" class="form-error">{{ passwordError }}</p>
        <p v-if="confirmPassword && form.password !== confirmPassword" class="form-error">两次输入的密码不一致。</p>
        <p v-if="notice" class="form-notice">{{ notice }}</p>
        <p v-if="localError" class="form-error">{{ localError }}</p>

        <footer class="modal-actions">
          <button class="secondary-action" type="button" @click="closeModal">取消</button>
          <button class="primary-action" type="submit" :disabled="isSaving || !canSubmit">
            <Save :size="18" aria-hidden="true" />
            <span>{{ isSaving ? '保存中……' : '保存新密码' }}</span>
          </button>
        </footer>
      </form>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { storeToRefs } from 'pinia';
import { MailCheck, Save, X } from 'lucide-vue-next';
import authService from '../services/authService';
import userService from '../services/userService';
import { useUserStore } from '../stores/userStore';
import { getPasswordStrengthError } from '../utils/passwordPolicy';

const userStore = useUserStore();
const { currentUser } = storeToRefs(userStore);

const isOpen = ref(false);
const isSendingCode = ref(false);
const isSaving = ref(false);
const codeSent = ref(false);
const localError = ref('');
const notice = ref('');
const confirmPassword = ref('');

const form = reactive({
  verificationCode: '',
  password: '',
});

const maskedEmail = computed(() => {
  const email = currentUser.value?.email || '';
  const [name, domain] = email.split('@');
  if (!name || !domain) {
    return email;
  }

  return `${name.slice(0, 2)}${'*'.repeat(Math.max(4, name.length - 2))}@${domain}`;
});

const passwordError = computed(() => getPasswordStrengthError(form.password));

const canSubmit = computed(
  () =>
    form.verificationCode.trim().length >= 6 &&
    Boolean(form.password) &&
    !passwordError.value &&
    form.password === confirmPassword.value
);

const resetForm = () => {
  form.verificationCode = '';
  form.password = '';
  confirmPassword.value = '';
  codeSent.value = false;
  localError.value = '';
  notice.value = '';
};

const openModal = () => {
  resetForm();
  isOpen.value = true;
};

const closeModal = () => {
  isOpen.value = false;
  resetForm();
};

const sendCode = async () => {
  if (!currentUser.value?.email) {
    localError.value = '当前账号没有可验证的邮箱。';
    return;
  }

  localError.value = '';
  notice.value = '';
  isSendingCode.value = true;

  try {
    await authService.sendEmailCode({
      email: currentUser.value.email,
      purpose: 'RESET_PASSWORD',
    });
    codeSent.value = true;
    notice.value = '验证码已发送，请检查当前邮箱。';
  } catch (error: any) {
    localError.value =
      error.response?.data?.error ||
      error.response?.data?.message ||
      '发送验证码失败，请稍后再试。';
  } finally {
    isSendingCode.value = false;
  }
};

const savePassword = async () => {
  if (!canSubmit.value) {
    return;
  }

  localError.value = '';
  notice.value = '';
  isSaving.value = true;

  try {
    await userService.changePassword({
      verificationCode: form.verificationCode.trim(),
      password: form.password,
    });
    closeModal();
  } catch (error: any) {
    localError.value =
      error.response?.data?.error ||
      error.response?.data?.message ||
      '重置密码失败，请检查验证码后重试。';
  } finally {
    isSaving.value = false;
  }
};

onMounted(() => {
  window.addEventListener('nexacord:open-password-reset', openModal);
});

onBeforeUnmount(() => {
  window.removeEventListener('nexacord:open-password-reset', openModal);
});
</script>

<style scoped>
.password-overlay {
  position: fixed;
  inset: 0;
  z-index: 85;
  display: grid;
  place-items: center;
  padding: 24px;
  background: var(--discord-overlay);
}

.password-modal {
  position: relative;
  width: min(520px, calc(100vw - 44px));
  max-height: calc(100dvh - 48px);
  display: grid;
  gap: 14px;
  padding: 36px 32px 26px;
  overflow-y: auto;
  border: 1px solid var(--discord-border);
  border-radius: 14px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--discord-brand) 18%, transparent), transparent 210px),
    var(--discord-elevated);
  color: var(--discord-text);
  box-shadow: var(--discord-shadow);
}

.close-button {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: transparent;
  color: var(--discord-text-muted);
}

.close-button:hover {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.password-hero {
  justify-self: center;
  width: 92px;
  height: 76px;
  border-radius: 22px;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--discord-brand) 16%, var(--discord-bg));
  color: var(--discord-brand);
}

.password-heading {
  display: grid;
  gap: 8px;
  text-align: center;
}

.password-heading h2 {
  margin: 0;
  font-size: 26px;
}

.password-heading p {
  margin: 0;
  color: var(--discord-text-muted);
  line-height: 1.6;
}

.password-form {
  display: grid;
  gap: 14px;
}

.field {
  display: grid;
  gap: 8px;
  color: var(--discord-text-muted);
  font-size: 13px;
  font-weight: 900;
}

.field input {
  width: 100%;
  min-height: 44px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  padding: 0 12px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.field input:disabled {
  opacity: 0.72;
}

.code-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 116px;
  gap: 8px;
}

.secondary-button,
.secondary-action,
.primary-action {
  min-height: 42px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 14px;
  font-weight: 900;
}

.secondary-button,
.secondary-action {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.secondary-button:hover:not(:disabled),
.secondary-action:hover {
  background: var(--discord-hover-strong);
}

.primary-action {
  background: var(--discord-brand);
  color: white;
}

.primary-action:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 10px;
}

.form-error,
.form-hint,
.form-notice {
  margin: 0;
  font-size: 13px;
  line-height: 1.45;
}

.form-error {
  color: #ff8b8d;
}

.form-hint {
  color: var(--discord-text-faint);
}

.form-notice {
  color: var(--discord-green);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

@media (max-width: 620px) {
  .code-row {
    grid-template-columns: 1fr;
  }
}
</style>
