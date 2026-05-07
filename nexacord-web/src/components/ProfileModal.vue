<template>
  <div v-if="isOpen" class="profile-overlay" @click="closeModal">
    <section class="profile-modal" role="dialog" aria-modal="true" aria-label="编辑个人资料" @click.stop>
      <header class="modal-heading">
        <h2>编辑个人资料</h2>
        <button class="close-button" type="button" aria-label="关闭弹窗" @click="closeModal">
          <X :size="22" aria-hidden="true" />
        </button>
      </header>

      <div class="profile-body">
        <aside class="profile-preview">
          <div class="profile-banner" :style="profileBannerStyle"></div>
          <button class="avatar-upload" type="button" title="上传头像" @click="avatarInput?.click()">
            <img :src="avatarPreviewUrl || profileForm.avatarUrl || defaultAvatarUrl" alt="头像预览" />
          </button>
          <strong>{{ previewName }}</strong>
          <small>@{{ profileForm.username || currentUser?.username }}</small>
          <p>{{ profileForm.bio || '编辑个人简介，让好友更快认识你。' }}</p>
        </aside>

        <form class="profile-form" @submit.prevent="saveProfile">
          <input ref="avatarInput" class="hidden-input" type="file" accept="image/*" @change="uploadProfileImage($event, 'avatarUrl')" />
          <input ref="bannerInput" class="hidden-input" type="file" accept="image/*" @change="uploadProfileImage($event, 'bannerUrl')" />

          <label class="field">
            <span>昵称</span>
            <input v-model="profileForm.displayName" type="text" maxlength="32" placeholder="好友看到的名称" />
          </label>

          <div class="field email-field">
            <span>邮箱</span>
            <div class="email-display-row">
              <input :value="profileForm.email" type="email" disabled />
              <button class="inline-action" type="button" @click="openEmailChangeModal">编辑</button>
            </div>
          </div>

          <label class="field">
            <span>个人简介</span>
            <textarea v-model="profileForm.bio" rows="4" maxlength="190" placeholder="写一点关于自己的介绍"></textarea>
          </label>

          <div class="banner-tools">
            <span>个人资料横幅</span>
            <div class="banner-swatches">
              <button
                v-for="color in bannerColors"
                :key="color"
                class="banner-swatch"
                :class="{ active: profileForm.bannerColor === color }"
                type="button"
                :style="{ background: color }"
                :title="color"
                @click="setBannerColor(color)"
              ></button>
            </div>
            <div class="banner-actions">
              <button class="banner-button" type="button" @click="bannerInput?.click()">
                <ImagePlus :size="18" aria-hidden="true" />
                <span>上传横幅图片</span>
              </button>
              <button v-if="bannerPreviewUrl || profileForm.bannerUrl" class="banner-button" type="button" @click="clearBannerImage">
                清除图片
              </button>
            </div>
          </div>

          <p v-if="profileNotice" class="form-notice">{{ profileNotice }}</p>
          <p v-if="profileError" class="form-error">{{ profileError }}</p>

          <footer class="modal-actions">
            <button class="secondary-button" type="button" @click="closeModal">取消</button>
            <button class="primary-button" type="submit" :disabled="isSavingProfile">
              <Save :size="18" aria-hidden="true" />
              <span>{{ isSavingProfile ? '保存中……' : '保存更改' }}</span>
            </button>
          </footer>
        </form>
      </div>
    </section>

    <div v-if="showEmailChangeModal" class="nested-overlay" @click.stop="closeEmailChangeModal">
      <section class="email-change-modal" role="dialog" aria-modal="true" aria-label="更换邮箱" @click.stop>
        <button class="email-close-button" type="button" aria-label="关闭更换邮箱弹窗" @click="closeEmailChangeModal">
          <X :size="22" aria-hidden="true" />
        </button>

        <div class="email-illustration">
          <MailCheck :size="78" aria-hidden="true" />
        </div>

        <template v-if="emailStep === 'verify'">
          <h3>输入验证码</h3>
          <p>
            我们已向 {{ maskedCurrentEmail }} 发送了验证码，请进入邮箱查看，并在此输入验证码验证自己的身份。
          </p>

          <label class="email-modal-field">
            <span>验证码</span>
            <input v-model="emailFlow.currentCode" type="text" inputmode="numeric" maxlength="12" autofocus />
          </label>

          <button class="email-resend-button" type="button" :disabled="isSendingEmailCode" @click="sendCurrentEmailCode">
            没有收到验证码或验证码已过期？重新发送。
          </button>

          <p v-if="emailModalError" class="form-error">{{ emailModalError }}</p>
          <p v-if="emailModalNotice" class="form-notice">{{ emailModalNotice }}</p>

          <button
            class="email-primary-button"
            type="button"
            :disabled="isVerifyingEmailCode || emailFlow.currentCode.trim().length < 6"
            @click="verifyCurrentEmailCode"
          >
            {{ isVerifyingEmailCode ? '验证中……' : '下一步' }}
          </button>
        </template>

        <template v-else>
          <button class="email-back-button" type="button" @click="emailStep = 'verify'">返回验证</button>
          <h3>输入新的邮箱</h3>
          <p>身份验证已通过。请输入新的邮箱地址，保存后会作为你的登录邮箱。</p>

          <label class="email-modal-field">
            <span>新邮箱</span>
            <input v-model="emailFlow.newEmail" type="email" placeholder="新的邮箱地址" />
          </label>

          <p v-if="emailModalError" class="form-error">{{ emailModalError }}</p>
          <p v-if="emailModalNotice" class="form-notice">{{ emailModalNotice }}</p>

          <button
            class="email-primary-button"
            type="button"
            :disabled="isSavingEmailChange || !emailFlow.newEmail.trim()"
            @click="saveEmailChange"
          >
            {{ isSavingEmailChange ? '保存中……' : '保存邮箱' }}
          </button>
        </template>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { ImagePlus, MailCheck, Save, X } from 'lucide-vue-next';
import fileService from '../services/fileService';
import userService from '../services/userService';
import { useUserStore } from '../stores/userStore';
import type { User } from '../types';

const userStore = useUserStore();
const { currentUser } = storeToRefs(userStore);

const isOpen = ref(false);
const isSavingProfile = ref(false);
const isSendingEmailCode = ref(false);
const isVerifyingEmailCode = ref(false);
const isSavingEmailChange = ref(false);
const profileError = ref('');
const profileNotice = ref('');
const emailModalError = ref('');
const emailModalNotice = ref('');
const avatarInput = ref<HTMLInputElement | null>(null);
const bannerInput = ref<HTMLInputElement | null>(null);
const pendingAvatarFile = ref<File | null>(null);
const pendingBannerFile = ref<File | null>(null);
const avatarPreviewUrl = ref('');
const bannerPreviewUrl = ref('');
const showEmailChangeModal = ref(false);
const emailStep = ref<'verify' | 'new-email'>('verify');
const defaultAvatarUrl = '/logo.png';

const profileForm = reactive({
  username: '',
  displayName: '',
  email: '',
  avatarUrl: null as string | null,
  bannerUrl: null as string | null,
  bannerColor: '#5865f2',
  bio: '',
  status: 'online' as User['status'],
});

const emailFlow = reactive({
  currentCode: '',
  newEmail: '',
});

const bannerColors = ['#5865f2', '#3ba55d', '#f0b232', '#ed4245', '#eb459e', '#8e5cf7', '#1abc9c', '#34495e'];

const profileBannerStyle = computed(() => {
  const style: Record<string, string> = {
    backgroundColor: profileForm.bannerColor || '#5865f2',
  };

  if (profileForm.bannerUrl) {
    style.backgroundImage = `url(${profileForm.bannerUrl})`;
  }

  if (bannerPreviewUrl.value) {
    style.backgroundImage = `url(${bannerPreviewUrl.value})`;
  }

  return style;
});

const previewName = computed(() => profileForm.displayName.trim() || profileForm.username || currentUser.value?.username || 'N');

const maskedCurrentEmail = computed(() => {
  const email = currentUser.value?.email || profileForm.email;
  const [name, domain] = email.split('@');
  if (!name || !domain) {
    return email || '当前邮箱';
  }

  const visiblePrefix = name.slice(0, Math.min(2, name.length));
  return `${visiblePrefix}${'*'.repeat(Math.max(4, name.length - visiblePrefix.length))}@${domain}`;
});

const syncProfileForm = () => {
  if (!currentUser.value) {
    return;
  }

  profileForm.username = currentUser.value.username;
  profileForm.displayName = currentUser.value.displayName || currentUser.value.username;
  profileForm.email = currentUser.value.email;
  profileForm.avatarUrl = currentUser.value.avatarUrl || null;
  profileForm.bannerUrl = currentUser.value.bannerUrl || null;
  profileForm.bannerColor = currentUser.value.bannerColor || '#5865f2';
  profileForm.bio = currentUser.value.bio || '';
  profileForm.status = currentUser.value.status || 'online';
};

const openModal = () => {
  syncProfileForm();
  profileError.value = '';
  profileNotice.value = '';
  isOpen.value = true;
};

const closeModal = () => {
  isOpen.value = false;
  profileError.value = '';
  profileNotice.value = '';
  clearPendingImages();
  closeEmailChangeModal();
};

const saveProfile = async () => {
  profileError.value = '';
  profileNotice.value = '';

  isSavingProfile.value = true;
  const uploadedUrls: string[] = [];

  try {
    let nextAvatarUrl = profileForm.avatarUrl;
    let nextBannerUrl = profileForm.bannerUrl;

    if (pendingAvatarFile.value) {
      profileNotice.value = '正在上传头像……';
      nextAvatarUrl = await fileService.uploadFile(pendingAvatarFile.value);
      uploadedUrls.push(nextAvatarUrl);
    }

    if (pendingBannerFile.value) {
      profileNotice.value = '正在上传横幅……';
      nextBannerUrl = await fileService.uploadFile(pendingBannerFile.value);
      uploadedUrls.push(nextBannerUrl);
    }

    const success = await userStore.updateProfile({
      displayName: profileForm.displayName.trim(),
      avatarUrl: nextAvatarUrl,
      bannerUrl: nextBannerUrl,
      bannerColor: profileForm.bannerColor,
      bio: profileForm.bio.trim(),
    });

    if (!success) {
      await cleanupUploadedUrls(uploadedUrls);
      profileError.value = userStore.error || '保存个人资料失败。';
      return;
    }

    clearPendingImages();
    closeModal();
  } catch (error: any) {
    await cleanupUploadedUrls(uploadedUrls);
    profileError.value =
      error.response?.data?.error ||
      error.response?.data?.message ||
      '上传图片或保存个人资料失败。';
  } finally {
    profileNotice.value = '';
    isSavingProfile.value = false;
  }
};

const setBannerColor = (color: string) => {
  profileForm.bannerColor = color;
};

const cleanupUploadedUrls = async (urls: string[]) => {
  await Promise.allSettled(urls.map((url) => fileService.deleteFile(url)));
};

const revokePreviewUrl = (url: string) => {
  if (url) {
    URL.revokeObjectURL(url);
  }
};

const setAvatarPreview = (file: File) => {
  revokePreviewUrl(avatarPreviewUrl.value);
  pendingAvatarFile.value = file;
  avatarPreviewUrl.value = URL.createObjectURL(file);
};

const setBannerPreview = (file: File) => {
  revokePreviewUrl(bannerPreviewUrl.value);
  pendingBannerFile.value = file;
  bannerPreviewUrl.value = URL.createObjectURL(file);
};

const clearPendingImages = () => {
  revokePreviewUrl(avatarPreviewUrl.value);
  revokePreviewUrl(bannerPreviewUrl.value);
  pendingAvatarFile.value = null;
  pendingBannerFile.value = null;
  avatarPreviewUrl.value = '';
  bannerPreviewUrl.value = '';
};

const clearBannerImage = () => {
  revokePreviewUrl(bannerPreviewUrl.value);
  pendingBannerFile.value = null;
  bannerPreviewUrl.value = '';
  profileForm.bannerUrl = null;
};

const resetEmailFlow = () => {
  emailStep.value = 'verify';
  emailFlow.currentCode = '';
  emailFlow.newEmail = '';
  emailModalError.value = '';
  emailModalNotice.value = '';
};

const openEmailChangeModal = async () => {
  resetEmailFlow();
  showEmailChangeModal.value = true;
  await sendCurrentEmailCode();
};

const closeEmailChangeModal = () => {
  showEmailChangeModal.value = false;
  resetEmailFlow();
};

const sendCurrentEmailCode = async () => {
  if (!currentUser.value?.email) {
    emailModalError.value = '当前账号还没有可验证的邮箱。';
    return;
  }

  emailModalError.value = '';
  emailModalNotice.value = '';
  isSendingEmailCode.value = true;
  try {
    await userService.sendCurrentEmailCode();
    emailModalNotice.value = '验证码已发送，请检查当前邮箱。';
  } catch (error: any) {
    emailModalError.value =
      error.response?.data?.error ||
      error.response?.data?.message ||
      '发送邮箱验证码失败。';
  } finally {
    isSendingEmailCode.value = false;
  }
};

const verifyCurrentEmailCode = async () => {
  emailModalError.value = '';
  emailModalNotice.value = '';
  isVerifyingEmailCode.value = true;

  try {
    await userService.verifyCurrentEmailCode(emailFlow.currentCode.trim());
    emailStep.value = 'new-email';
    emailModalNotice.value = '身份验证已通过。';
  } catch (error: any) {
    emailModalError.value =
      error.response?.data?.error ||
      error.response?.data?.message ||
      '验证码校验失败，请重新输入。';
  } finally {
    isVerifyingEmailCode.value = false;
  }
};

const saveEmailChange = async () => {
  const nextEmail = emailFlow.newEmail.trim().toLowerCase();
  const currentEmail = currentUser.value?.email.trim().toLowerCase();

  emailModalError.value = '';
  emailModalNotice.value = '';

  if (!nextEmail) {
    emailModalError.value = '请输入新的邮箱地址。';
    return;
  }

  if (nextEmail === currentEmail) {
    emailModalError.value = '新的邮箱不能和当前邮箱相同。';
    return;
  }

  isSavingEmailChange.value = true;
  try {
    const success = await userStore.updateProfile({
      email: nextEmail,
      emailVerificationCode: emailFlow.currentCode.trim(),
    });

    if (!success) {
      emailModalError.value = userStore.error || '保存邮箱失败。';
      return;
    }

    profileForm.email = currentUser.value?.email || nextEmail;
    profileNotice.value = '邮箱已更新。';
    closeEmailChangeModal();
  } finally {
    isSavingEmailChange.value = false;
  }
};

const uploadProfileImage = async (event: Event, field: 'avatarUrl' | 'bannerUrl') => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) {
    return;
  }

  if (!file.type.startsWith('image/')) {
    profileError.value = '请选择图片文件。';
    input.value = '';
    return;
  }

  profileError.value = '';
  profileNotice.value = '';
  try {
    if (field === 'avatarUrl') {
      setAvatarPreview(await createSquareAvatarFile(file));
      profileNotice.value = '头像已预览，保存后才会上传。';
      return;
    }

    setBannerPreview(file);
    profileNotice.value = '横幅已预览，保存后才会上传。';
  } catch (error: any) {
    profileError.value =
      error.response?.data?.error ||
      error.response?.data?.message ||
      '读取图片失败。';
  } finally {
    input.value = '';
  }
};

const createSquareAvatarFile = async (file: File) => {
  const bitmap = await createImageBitmap(file);
  const size = Math.min(bitmap.width, bitmap.height);
  const sourceX = Math.floor((bitmap.width - size) / 2);
  const sourceY = Math.floor((bitmap.height - size) / 2);
  const targetSize = 512;
  const canvas = document.createElement('canvas');
  canvas.width = targetSize;
  canvas.height = targetSize;

  const context = canvas.getContext('2d');
  if (!context) {
    bitmap.close();
    return file;
  }

  context.drawImage(bitmap, sourceX, sourceY, size, size, 0, 0, targetSize, targetSize);
  bitmap.close();

  const blob = await new Promise<Blob | null>((resolve) => {
    canvas.toBlob(resolve, 'image/webp', 0.92);
  });

  if (!blob) {
    return file;
  }

  const fileName = file.name.replace(/\.[^.]+$/, '') || 'avatar';
  return new File([blob], `${fileName}.webp`, { type: 'image/webp' });
};

onMounted(() => {
  syncProfileForm();
  window.addEventListener('nexacord:open-profile', openModal);
});

onBeforeUnmount(() => {
  clearPendingImages();
  window.removeEventListener('nexacord:open-profile', openModal);
});

watch(currentUser, syncProfileForm);
</script>

<style scoped>
.profile-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 22px;
  background: var(--discord-overlay);
}

.profile-modal {
  width: min(980px, calc(100vw - 48px));
  max-height: min(760px, calc(100vh - 56px));
  display: grid;
  grid-template-rows: auto 1fr;
  overflow: hidden;
  border-radius: 8px;
  background: var(--discord-bg);
  box-shadow: var(--discord-shadow);
}

.modal-heading {
  min-height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 22px;
  border-bottom: 1px solid var(--discord-border);
}

.modal-heading h2 {
  margin: 0;
  font-size: 18px;
}

.close-button {
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

.profile-body {
  min-height: 0;
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  overflow: hidden;
}

.profile-preview {
  position: relative;
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 122px 18px 18px;
  background: var(--discord-surface-soft);
}

.profile-banner {
  position: absolute;
  inset: 0 0 auto;
  height: 132px;
  background:
    linear-gradient(135deg, rgba(88, 101, 242, 0.72), rgba(59, 165, 93, 0.42)),
    var(--discord-input);
  background-size: cover;
  background-position: center;
}

.avatar-upload {
  position: relative;
  width: 84px;
  height: 84px;
  border: 6px solid var(--discord-surface-soft);
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: var(--discord-brand);
  color: white;
  font-size: 26px;
  font-weight: 900;
  overflow: hidden;
}

.avatar-upload img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-upload span {
  line-height: 1;
}

.profile-preview strong,
.profile-preview small,
.profile-preview p {
  position: relative;
  margin: 0;
}

.profile-preview small {
  color: var(--discord-text-faint);
  font-size: 13px;
}

.profile-preview p {
  color: var(--discord-text-muted);
  line-height: 1.5;
}

.profile-form {
  min-width: 0;
  min-height: 0;
  display: grid;
  gap: 14px;
  padding: 22px 22px 26px;
  overflow-y: auto;
}

.hidden-input {
  display: none;
}

.field {
  min-width: 0;
  display: grid;
  gap: 7px;
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.field input,
.field select,
.field textarea {
  min-width: 0;
  width: 100%;
  border: 1px solid var(--discord-border);
  border-radius: 6px;
  background: var(--discord-input);
  color: var(--discord-text);
}

.field input,
.field select {
  min-height: 40px;
  padding: 0 10px;
}

.field textarea {
  min-height: 108px;
  padding: 10px;
  resize: vertical;
}

.email-display-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 88px;
  gap: 8px;
}

.email-code-field {
  min-width: 0;
  display: grid;
  gap: 7px;
}

.text-action {
  width: fit-content;
  padding: 0;
  background: transparent;
  color: var(--discord-brand);
  font-size: 13px;
  font-weight: 900;
}

.text-action:hover {
  text-decoration: underline;
}

.inline-action {
  min-height: 40px;
  border-radius: 6px;
  padding: 0 12px;
  background: var(--discord-muted-surface);
  color: var(--discord-text);
  font-weight: 900;
  white-space: nowrap;
}

.inline-action:hover:not(:disabled) {
  background: var(--discord-hover-strong);
}

.field input:disabled {
  opacity: 0.72;
  cursor: not-allowed;
}

.banner-tools {
  display: grid;
  gap: 10px;
  color: var(--discord-text-muted);
  font-size: 12px;
  font-weight: 900;
  text-transform: uppercase;
}

.banner-swatches {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 8px;
}

.banner-swatch {
  aspect-ratio: 1.8;
  min-height: 28px;
  border: 2px solid transparent;
  border-radius: 8px;
}

.banner-swatch.active {
  border-color: var(--discord-text);
  box-shadow: 0 0 0 2px var(--discord-brand);
}

.banner-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.banner-button,
.primary-button,
.secondary-button {
  min-height: 40px;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 14px;
  font-weight: 900;
}

.banner-button,
.secondary-button {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.banner-button:hover,
.secondary-button:hover {
  background: var(--discord-hover-strong);
}

.primary-button {
  background: var(--discord-brand);
  color: white;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 12px;
  padding-top: 14px;
  border-top: 1px solid var(--discord-border);
}

.form-error,
.form-notice {
  margin: 0;
  font-size: 13px;
}

.form-error {
  color: #ff8b8d;
}

.form-notice {
  color: var(--discord-green);
}

.nested-overlay {
  position: fixed;
  inset: 0;
  z-index: 5;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(0, 0, 0, 0.18);
}

.email-change-modal {
  position: relative;
  width: min(520px, calc(100vw - 42px));
  display: grid;
  gap: 18px;
  padding: 50px 34px 34px;
  border: 1px solid var(--discord-border);
  border-radius: 14px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--discord-brand) 18%, transparent), transparent 230px),
    var(--discord-elevated);
  color: var(--discord-text);
  box-shadow: var(--discord-shadow);
  animation: modal-pop 150ms ease-out;
}

.email-close-button {
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

.email-close-button:hover {
  background: var(--discord-hover);
  color: var(--discord-text);
}

.email-illustration {
  justify-self: center;
  width: 120px;
  height: 96px;
  border-radius: 28px;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--discord-brand) 16%, var(--discord-bg));
  color: var(--discord-brand);
}

.email-change-modal h3 {
  margin: 0;
  text-align: center;
  font-size: 28px;
  line-height: 1.12;
}

.email-change-modal p {
  margin: 0;
  color: var(--discord-text-muted);
  font-size: 15px;
  line-height: 1.6;
  text-align: center;
}

.email-modal-field {
  display: grid;
  gap: 8px;
  color: var(--discord-text-muted);
  font-size: 13px;
  font-weight: 900;
}

.email-modal-field input {
  width: 100%;
  min-height: 48px;
  border: 1px solid var(--discord-border);
  border-radius: 8px;
  padding: 0 14px;
  background: var(--discord-input);
  color: var(--discord-text);
  font-size: 18px;
}

.email-resend-button,
.email-back-button {
  justify-self: start;
  padding: 0;
  background: transparent;
  color: var(--discord-brand);
  font-size: 14px;
  font-weight: 900;
}

.email-resend-button:hover:not(:disabled),
.email-back-button:hover {
  color: var(--discord-link-hover);
  text-decoration: underline;
}

.email-primary-button {
  min-height: 50px;
  border-radius: 8px;
  background: var(--discord-brand);
  color: white;
  font-size: 16px;
  font-weight: 900;
}

.email-primary-button:hover:not(:disabled) {
  background: var(--discord-brand-hover);
}

@keyframes modal-pop {
  from {
    opacity: 0;
    transform: translateY(8px) scale(0.98);
  }

  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.62;
}

@media (max-width: 760px) {
  .profile-body {
    grid-template-columns: 1fr;
  }

  .profile-modal {
    width: min(100%, calc(100vw - 28px));
  }

  .email-display-row {
    grid-template-columns: 1fr;
  }
}
</style>
