<template>
  <span class="avatar-image-frame">
    <canvas ref="canvasRef" class="avatar-image-canvas" role="img" :aria-label="alt"></canvas>
  </span>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';

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
const canvasRef = ref<HTMLCanvasElement | null>(null);
let loadId = 0;
const baseSrc = computed(() => props.src || '/logo.png');
const resolvedSrc = computed(() => {
  if (!reloadToken.value) {
    return baseSrc.value;
  }

  const separator = baseSrc.value.includes('?') ? '&' : '?';
  return `${baseSrc.value}${separator}avatarRetry=${reloadToken.value}`;
});

async function loadAvatar() {
  const currentLoadId = ++loadId;
  await nextTick();

  const image = new Image();
  image.decoding = 'async';
  image.onload = () => {
    if (currentLoadId !== loadId) {
      return;
    }

    drawAvatar(image);
  };
  image.onerror = retryLoad;
  image.src = resolvedSrc.value;
}

function drawAvatar(image: HTMLImageElement) {
  const canvas = canvasRef.value;
  if (!canvas || !image.naturalWidth || !image.naturalHeight) {
    return;
  }

  const size = 192;
  canvas.width = size;
  canvas.height = size;

  const context = canvas.getContext('2d');
  if (!context) {
    return;
  }

  context.clearRect(0, 0, size, size);
  context.save();
  context.beginPath();
  context.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
  context.clip();
  context.fillStyle = '#eef2f7';
  context.fillRect(0, 0, size, size);

  context.filter = 'blur(12px) saturate(1.12)';
  drawCover(context, image, size, 1.8);
  context.filter = 'none';
  drawCover(context, image, size, 1.28);
  context.restore();
}

function drawCover(
  context: CanvasRenderingContext2D,
  image: HTMLImageElement,
  size: number,
  scaleMultiplier: number
) {
  const imageScale = Math.max(size / image.naturalWidth, size / image.naturalHeight) * scaleMultiplier;
  const width = image.naturalWidth * imageScale;
  const height = image.naturalHeight * imageScale;
  const x = (size - width) / 2;
  const y = (size - height) / 2;
  context.drawImage(image, x, y, width, height);
}

function retryLoad() {
  if (retryCount.value >= 3) {
    return;
  }

  retryCount.value += 1;
  window.setTimeout(() => {
    reloadToken.value = Date.now();
  }, 450 * retryCount.value);
}

watch(baseSrc, () => {
  retryCount.value = 0;
  reloadToken.value = 0;
});

watch(
  resolvedSrc,
  () => {
    void loadAvatar();
  },
  { immediate: true }
);
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

.avatar-image-canvas {
  width: 100%;
  height: 100%;
  display: block;
  border-radius: inherit;
}
</style>
