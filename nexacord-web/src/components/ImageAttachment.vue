<template>
  <button class="image-attachment" type="button" @click="handleClick">
    <img
      v-if="!hasFailed"
      :src="renderUrl"
      :alt="alt"
      loading="lazy"
      @error="handleImageError"
      @load="handleImageLoad"
    />
    <span v-else class="image-error">
      <ImageOff :size="22" aria-hidden="true" />
      <strong>图片加载失败</strong>
      <small>点击重新加载</small>
    </span>
  </button>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { ImageOff } from 'lucide-vue-next';

const props = defineProps<{
  sourceUrl: string;
  alt?: string;
}>();

const emit = defineEmits<{
  preview: [url: string];
}>();

const retryCount = ref(0);
const hasFailed = ref(false);
let retryTimer: number | null = null;

const renderUrl = computed(() => {
  if (retryCount.value === 0) {
    return props.sourceUrl;
  }

  const separator = props.sourceUrl.includes('?') ? '&' : '?';
  return `${props.sourceUrl}${separator}retry=${retryCount.value}`;
});

const scheduleRetry = () => {
  if (retryTimer != null) {
    window.clearTimeout(retryTimer);
  }

  retryTimer = window.setTimeout(() => {
    retryCount.value += 1;
    hasFailed.value = false;
    retryTimer = null;
  }, Math.min(6000, 700 * (retryCount.value + 1)));
};

const handleImageError = () => {
  if (retryCount.value >= 4) {
    hasFailed.value = true;
    return;
  }

  scheduleRetry();
};

const handleImageLoad = () => {
  hasFailed.value = false;
};

const handleClick = () => {
  if (hasFailed.value) {
    retryCount.value += 1;
    hasFailed.value = false;
    return;
  }

  emit('preview', props.sourceUrl);
};

onBeforeUnmount(() => {
  if (retryTimer != null) {
    window.clearTimeout(retryTimer);
  }
});
</script>

<style scoped>
.image-attachment {
  display: block;
  max-width: min(520px, 100%);
  padding: 0;
  border-radius: 10px;
  overflow: hidden;
  background: var(--discord-input);
  color: var(--discord-text);
  text-align: left;
}

.image-attachment:hover {
  filter: brightness(1.04);
}

.image-attachment img {
  display: block;
  width: 100%;
  max-height: 360px;
  object-fit: contain;
  background: var(--discord-input);
}

.image-error {
  min-width: 220px;
  min-height: 150px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 6px;
  padding: 22px;
  color: var(--discord-text-faint);
}

.image-error strong {
  color: var(--discord-text-muted);
  font-size: 14px;
}

.image-error small {
  font-size: 12px;
}
</style>
