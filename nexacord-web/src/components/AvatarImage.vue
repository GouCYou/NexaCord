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

  loadImage(currentLoadId, shouldTryCors(resolvedSrc.value));
}

function loadImage(currentLoadId: number, tryCors: boolean) {
  const image = new Image();
  image.decoding = 'async';
  if (tryCors) {
    image.crossOrigin = 'anonymous';
  }
  image.onload = () => {
    if (currentLoadId !== loadId) {
      return;
    }

    drawAvatar(image);
  };
  image.onerror = () => {
    if (tryCors) {
      loadImage(currentLoadId, false);
      return;
    }

    retryLoad();
  };
  image.src = resolvedSrc.value;
}

function shouldTryCors(src: string) {
  if (!src.startsWith('http')) {
    return false;
  }

  try {
    return new URL(src).origin !== window.location.origin;
  } catch {
    return false;
  }
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

  const visibleBounds = getVisibleBounds(image);
  drawSquareAvatar(context, image, visibleBounds, size);
  context.restore();
}

function drawSquareAvatar(
  context: CanvasRenderingContext2D,
  image: HTMLImageElement,
  sourceBounds: SourceBounds | null,
  size: number
) {
  const bounds = sourceBounds ?? {
    x: 0,
    y: 0,
    width: image.naturalWidth,
    height: image.naturalHeight,
  };
  context.imageSmoothingEnabled = true;
  context.imageSmoothingQuality = 'high';
  context.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, 0, 0, size, size);
}

function getVisibleBounds(image: HTMLImageElement): SourceBounds | null {
  const maxScanSize = 256;
  const scale = Math.min(1, maxScanSize / Math.max(image.naturalWidth, image.naturalHeight));
  const width = Math.max(1, Math.round(image.naturalWidth * scale));
  const height = Math.max(1, Math.round(image.naturalHeight * scale));
  const scanCanvas = document.createElement('canvas');
  scanCanvas.width = width;
  scanCanvas.height = height;
  const scanContext = scanCanvas.getContext('2d', { willReadFrequently: true });
  if (!scanContext) {
    return null;
  }

  try {
    scanContext.drawImage(image, 0, 0, width, height);
    const pixels = scanContext.getImageData(0, 0, width, height).data;
    let minX = width;
    let minY = height;
    let maxX = -1;
    let maxY = -1;

    for (let y = 0; y < height; y += 1) {
      for (let x = 0; x < width; x += 1) {
        const alpha = pixels[(y * width + x) * 4 + 3] ?? 0;
        if (alpha <= 12) {
          continue;
        }

        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
      }
    }

    if (maxX < minX || maxY < minY) {
      return null;
    }

    const padding = 2;
    const sourceX = Math.max(0, (minX - padding) / scale);
    const sourceY = Math.max(0, (minY - padding) / scale);
    const visibleBounds = {
      x: sourceX,
      y: sourceY,
      width: Math.min(image.naturalWidth - sourceX, (maxX - minX + 1 + padding * 2) / scale),
      height: Math.min(image.naturalHeight - sourceY, (maxY - minY + 1 + padding * 2) / scale),
    };

    return toSquareBounds(visibleBounds, image.naturalWidth, image.naturalHeight);
  } catch {
    return null;
  }
}

function toSquareBounds(bounds: SourceBounds, imageWidth: number, imageHeight: number): SourceBounds {
  const side = Math.min(Math.max(bounds.width, bounds.height), imageWidth, imageHeight);
  const centerX = bounds.x + bounds.width / 2;
  const centerY = bounds.y + bounds.height / 2;
  const x = clamp(centerX - side / 2, 0, imageWidth - side);
  const y = clamp(centerY - side / 2, 0, imageHeight - side);

  return {
    x,
    y,
    width: side,
    height: side,
  };
}

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
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

<script lang="ts">
type SourceBounds = {
  x: number;
  y: number;
  width: number;
  height: number;
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

.avatar-image-canvas {
  width: 100%;
  height: 100%;
  display: block;
  border-radius: inherit;
}
</style>
