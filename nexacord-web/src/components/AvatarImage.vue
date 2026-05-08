<template>
  <span class="avatar-image-frame">
    <img
      class="avatar-image-backdrop"
      :src="resolvedSrc"
      alt=""
      aria-hidden="true"
      draggable="false"
    />
    <img
      class="avatar-image-foreground"
      :src="resolvedSrc"
      :alt="alt"
      draggable="false"
      @error="retryLoad"
    />
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

const props = withDefaults(
  defineProps<{
    src?: string | null;
    alt?: string;
  }>(),
  {
    src: '/logo.png',
    alt: '',
  }
);

const retryCount = ref(0);
const reloadToken = ref(0);
const baseSrc = computed(() => props.src || '/logo.png');
const resolvedSrc = computed(() => {
  if (!reloadToken.value) {
    return baseSrc.value;
  }

  const separator = baseSrc.value.includes('?') ? '&' : '?';
  return `${baseSrc.value}${separator}avatarRetry=${reloadToken.value}`;
});

watch(baseSrc, () => {
  retryCount.value = 0;
  reloadToken.value = 0;
});

const retryLoad = () => {
  if (retryCount.value >= 3) {
    return;
  }

  retryCount.value += 1;
  window.setTimeout(() => {
    reloadToken.value = Date.now();
  }, 450 * retryCount.value);
};
</script>

<style scoped>
.avatar-image-frame {
  position: relative;
  width: 100%;
  height: 100%;
  display: block;
  overflow: hidden;
  border-radius: inherit;
  font-size: 0;
  line-height: 0;
  background: var(--discord-avatar-bg);
}

.avatar-image-frame img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
  user-select: none;
}

.avatar-image-backdrop {
  transform: scale(1.32);
  filter: blur(8px) saturate(1.1);
}

.avatar-image-foreground {
  transform: scale(1.1);
}
</style>
