<template>
  <div class="voice-audio-sink" aria-hidden="true">
    <audio
      v-for="remote in remoteStreams"
      :key="remote.userId"
      :srcObject.prop="remote.stream"
      autoplay
      playsinline
      :muted="isDeafened"
    ></audio>
  </div>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia';
import { useVoiceStore } from '../stores/voiceStore';

const voiceStore = useVoiceStore();
const { remoteStreams, isDeafened } = storeToRefs(voiceStore);
</script>

<style scoped>
.voice-audio-sink {
  position: fixed;
  width: 1px;
  height: 1px;
  overflow: hidden;
  pointer-events: none;
}
</style>
