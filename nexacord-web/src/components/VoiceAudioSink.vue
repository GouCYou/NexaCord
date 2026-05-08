<template>
  <div ref="voiceAudioSink" class="voice-audio-sink" aria-hidden="true">
    <audio
      v-for="remote in remoteStreams"
      :key="remote.userId"
      :srcObject.prop="remote.stream"
      autoplay
      playsinline
      muted
    ></audio>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useVoiceStore } from '../stores/voiceStore';

const voiceStore = useVoiceStore();
const { remoteStreams, isDeafened, outputVolume, userVolumes } = storeToRefs(voiceStore);
const voiceAudioSink = ref<HTMLElement | null>(null);
let audioContext: AudioContext | null = null;
const sinks = new Map<number, { source: MediaStreamAudioSourceNode; gain: GainNode }>();

const ensureAudioContext = () => {
  if (!audioContext) {
    audioContext = new AudioContext();
  }

  if (audioContext.state === 'suspended') {
    void audioContext.resume();
  }

  return audioContext;
};

const syncAudioGraph = () => {
  const activeUserIds = new Set(remoteStreams.value.map((remote) => remote.userId));
  sinks.forEach((sink, userId) => {
    if (!activeUserIds.has(userId)) {
      sink.source.disconnect();
      sink.gain.disconnect();
      sinks.delete(userId);
    }
  });

  remoteStreams.value.forEach((remote) => {
    if (sinks.has(remote.userId)) {
      return;
    }

    const context = ensureAudioContext();
    const source = context.createMediaStreamSource(remote.stream);
    const gain = context.createGain();
    source.connect(gain);
    gain.connect(context.destination);
    sinks.set(remote.userId, { source, gain });
  });

  updateGains();
};

const updateGains = () => {
  sinks.forEach((sink, userId) => {
    sink.gain.gain.value = voiceStore.getPlaybackGain(userId);
  });
};

watch(
  remoteStreams,
  () => {
    syncAudioGraph();
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

watch([isDeafened, outputVolume, userVolumes], updateGains, { deep: true });

onBeforeUnmount(() => {
  sinks.forEach((sink) => {
    sink.source.disconnect();
    sink.gain.disconnect();
  });
  sinks.clear();
  void audioContext?.close();
  audioContext = null;
});
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
