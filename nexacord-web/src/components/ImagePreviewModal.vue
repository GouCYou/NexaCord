<template>
  <div v-if="imageUrl" class="preview-overlay" @click="$emit('close')">
    <button class="close-button" type="button" aria-label="关闭预览" @click.stop="$emit('close')">
      <X :size="22" aria-hidden="true" />
    </button>
    <img :src="imageUrl" alt="图片预览" @click.stop />
  </div>
</template>

<script setup lang="ts">
import { X } from 'lucide-vue-next';

defineProps<{
  imageUrl: string | null;
}>();

defineEmits<{
  close: [];
}>();
</script>

<style scoped>
.preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: grid;
  place-items: center;
  padding: 42px;
  background: rgba(0, 0, 0, 0.82);
}

.preview-overlay img {
  display: block;
  max-width: 100%;
  max-height: 100%;
  border-radius: 8px;
  object-fit: contain;
  box-shadow: 0 22px 80px rgba(0, 0, 0, 0.45);
}

.close-button {
  position: fixed;
  top: 18px;
  right: 18px;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.12);
  color: white;
}

.close-button:hover {
  background: rgba(255, 255, 255, 0.2);
}
</style>
