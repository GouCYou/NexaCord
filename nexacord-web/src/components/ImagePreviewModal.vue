<template>
  <ElImageViewer
    v-if="imageUrl"
    :url-list="[imageUrl]"
    :initial-index="0"
    :hide-on-click-modal="false"
    teleported
    @close="$emit('close')"
  />
</template>

<script setup lang="ts">
import { ElImageViewer } from 'element-plus';

defineProps<{
  imageUrl: string | null;
}>();

defineEmits<{
  close: [];
}>();
</script>

<style>
.el-image-viewer__wrapper {
  z-index: 9000 !important;
}

.el-image-viewer__mask {
  background: rgba(0, 0, 0, 0.82);
  opacity: 1;
}

.el-image-viewer__btn {
  color: #fff;
}
</style>
