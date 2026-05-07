<template>
  <div ref="voiceAudioSink" class="voice-audio-sink" aria-hidden="true">
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
import { nextTick, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useVoiceStore } from '../stores/voiceStore';

const voiceStore = useVoiceStore();
const { remoteStreams, isDeafened } = storeToRefs(voiceStore);
const voiceAudioSink = ref<HTMLElement | null>(null);

watch(
  remoteStreams,
  () => {
    nextTick(() => {
      voiceAudioSink.value?.querySelectorAll('audio').forEach((audio) => {
        audio.play().catch(() => {
          // Browser autoplay policies can still require a fresh user gesture.
        });
      });
    });
  },
  { deep: true }
);
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
