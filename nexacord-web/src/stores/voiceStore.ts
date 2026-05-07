import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import websocketService from '../services/websocketService';
import { useUserStore } from './userStore';
import type { User } from '../types';
import { displayUserLabel } from '../utils/userDisplay';

export type VoiceUser = {
  id: number;
  username: string;
  displayName?: string | null;
  avatarUrl: string | null;
  status?: User['status'];
};

type VoiceSignalType =
  | 'VOICE_JOIN'
  | 'VOICE_LEAVE'
  | 'VOICE_OFFER'
  | 'VOICE_ANSWER'
  | 'VOICE_ICE_CANDIDATE';

type VoiceSignalMessage = {
  channelId: number;
  type: VoiceSignalType;
  targetUserId?: number;
  sender?: VoiceUser;
  participants?: VoiceUser[];
  description?: RTCSessionDescriptionInit;
  candidate?: RTCIceCandidateInit;
};

const displayNameOf = (user: VoiceUser | User | null | undefined) =>
  displayUserLabel(user) || '未知用户';

const toVoiceUser = (user: User): VoiceUser => ({
  id: user.id,
  username: user.username,
  displayName: user.displayName || null,
  avatarUrl: user.avatarUrl,
  status: user.status,
});

const directRoomIdFor = (leftUserId: number, rightUserId: number) => {
  const [left, right] = [leftUserId, rightUserId].sort((a, b) => a - b);
  let hash = 2166136261;
  for (const char of `${left}:${right}`) {
    hash ^= char.charCodeAt(0);
    hash = Math.imul(hash, 16777619);
  }

  return -Math.abs(hash || 1);
};

const parseIceServers = (): RTCIceServer[] => {
  const defaultIceServers: RTCIceServer[] = [{ urls: 'stun:stun.l.google.com:19302' }];
  const rawIceServers = import.meta.env.VITE_RTC_ICE_SERVERS;
  if (!rawIceServers) {
    return defaultIceServers;
  }

  try {
    const parsedIceServers = JSON.parse(rawIceServers) as RTCIceServer[];
    return Array.isArray(parsedIceServers) && parsedIceServers.length > 0
      ? parsedIceServers
      : defaultIceServers;
  } catch {
    const urls = String(rawIceServers)
      .split(',')
      .map((url) => url.trim())
      .filter(Boolean);

    return urls.length > 0 ? [{ urls }] : defaultIceServers;
  }
};

export const useVoiceStore = defineStore('voice', () => {
  const userStore = useUserStore();

  const activeChannelId = ref<number | null>(null);
  const activeChannelName = ref('');
  const activeServerId = ref<number | null>(null);
  const activeServerName = ref('');
  const isJoined = ref(false);
  const isConnecting = ref(false);
  const isMuted = ref(false);
  const isDeafened = ref(false);
  const voiceError = ref('');
  const participants = ref<VoiceUser[]>([]);
  const remoteStreams = ref<Array<{ userId: number; stream: MediaStream }>>([]);
  const speakingUserIds = ref<Set<number>>(new Set());
  const voiceLevels = ref<Record<number, number>>({});

  const peerConnections = new Map<number, RTCPeerConnection>();
  const pendingIceCandidates = new Map<number, RTCIceCandidateInit[]>();
  const voiceAnalysers = new Map<
    number,
    {
      analyser: AnalyserNode;
      data: Uint8Array;
      frameId: number;
      source: MediaStreamAudioSourceNode;
    }
  >();
  let audioContext: AudioContext | null = null;
  let localStream: MediaStream | null = null;
  let unsubscribeVoice: (() => void) | null = null;

  const currentUser = computed(() => userStore.currentUser);

  const visibleParticipants = computed(() => {
    const participantMap = new Map<number, VoiceUser>();
    participants.value.forEach((participant) => {
      participantMap.set(participant.id, participant);
    });

    if (isJoined.value && currentUser.value) {
      participantMap.set(currentUser.value.id, toVoiceUser(currentUser.value));
    }

    return [...participantMap.values()];
  });

  const participantCount = computed(() => visibleParticipants.value.length);

  const statusText = computed(() => {
    if (!isJoined.value) {
      return '加入后即可和同频道成员实时语音通话。';
    }

    return `正在语音通话，当前 ${participantCount.value} 人在线。`;
  });

  const selfVoiceStatus = computed(() => {
    if (isMuted.value && isDeafened.value) {
      return '已静音并拒听';
    }

    if (isMuted.value) {
      return '麦克风已关闭';
    }

    if (isDeafened.value) {
      return '已拒听';
    }

    return '你正在频道中';
  });

  const isActiveChannel = (channelId: number) =>
    isJoined.value && activeChannelId.value === channelId;

  const subscribeVoiceTopic = (channelId: number) => {
    unsubscribeVoice?.();
    unsubscribeVoice = websocketService.subscribe(`/topic/voice/${channelId}`, (payload) => {
      void handleVoiceSignal(payload as VoiceSignalMessage);
    });

    return Boolean(unsubscribeVoice);
  };

  const joinChannel = async (options: {
    channelId: number;
    channelName: string;
    serverId?: number | null;
    serverName?: string | null;
  }) => {
    if (!currentUser.value || isConnecting.value) {
      return;
    }

    if (isActiveChannel(options.channelId)) {
      return;
    }

    if (isJoined.value) {
      leaveChannel();
    }

    voiceError.value = '';
    isConnecting.value = true;

    try {
      if (!websocketService.isConnected()) {
        throw new Error('实时连接还没有准备好，请稍后再试。');
      }

      localStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
        video: false,
      });
      if (audioContext?.state === 'suspended') {
        await audioContext.resume();
      }

      if (!subscribeVoiceTopic(options.channelId)) {
        throw new Error('无法订阅语音频道。');
      }

      activeChannelId.value = options.channelId;
      activeChannelName.value = options.channelName;
      activeServerId.value = options.serverId ?? null;
      activeServerName.value = options.serverName || '';
      isJoined.value = true;
      participants.value = [toVoiceUser(currentUser.value)];
      setupVoiceAnalyser(currentUser.value.id, localStream, () => !isMuted.value);

      websocketService.emit(`/voice/${options.channelId}/join`, {});
    } catch (error: any) {
      voiceError.value = error?.message || '无法加入语音频道，请检查麦克风权限。';
      cleanupVoice(false);
    } finally {
      isConnecting.value = false;
    }
  };

  const startDirectCall = async (
    user: Pick<User, 'id' | 'username'> & Partial<Pick<User, 'displayName' | 'avatarUrl' | 'status'>>
  ) => {
    if (!currentUser.value || user.id === currentUser.value.id) {
      return;
    }

    await joinChannel({
      channelId: directRoomIdFor(currentUser.value.id, user.id),
      channelName: `与 ${displayUserLabel(user)} 的通话`,
      serverId: null,
      serverName: '私信',
    });
  };

  const leaveChannel = () => {
    if (isJoined.value && activeChannelId.value) {
      websocketService.emit(`/voice/${activeChannelId.value}/leave`, {});
    }

    cleanupVoice(true);
  };

  const toggleMute = () => {
    if (!isJoined.value) {
      return;
    }

    isMuted.value = !isMuted.value;
    localStream?.getAudioTracks().forEach((track) => {
      track.enabled = !isMuted.value;
    });
  };

  const toggleDeafen = () => {
    if (!isJoined.value) {
      return;
    }

    isDeafened.value = !isDeafened.value;
  };

  const handleVoiceSignal = async (message: VoiceSignalMessage) => {
    if (
      !isJoined.value ||
      !currentUser.value ||
      !activeChannelId.value ||
      message.channelId !== activeChannelId.value
    ) {
      return;
    }

    if (message.participants) {
      participants.value = dedupeParticipants(message.participants);
    }

    const senderId = message.sender?.id;
    if (!senderId || senderId === currentUser.value.id) {
      return;
    }

    if (message.targetUserId && message.targetUserId !== currentUser.value.id) {
      return;
    }

    if (message.type === 'VOICE_JOIN') {
      if (currentUser.value.id < senderId) {
        await createOffer(senderId);
      }
      return;
    }

    if (message.type === 'VOICE_LEAVE') {
      closePeer(senderId);
      return;
    }

    if (message.type === 'VOICE_OFFER' && message.description) {
      await acceptOffer(senderId, message.description);
      return;
    }

    if (message.type === 'VOICE_ANSWER' && message.description) {
      const peerConnection = peerConnections.get(senderId);
      await peerConnection?.setRemoteDescription(message.description);
      await flushPendingIceCandidates(senderId);
      return;
    }

    if (message.type === 'VOICE_ICE_CANDIDATE' && message.candidate) {
      await addIceCandidate(senderId, message.candidate);
    }
  };

  const createPeerConnection = (remoteUserId: number) => {
    const existingPeer = peerConnections.get(remoteUserId);
    if (existingPeer) {
      return existingPeer;
    }

    const peerConnection = new RTCPeerConnection({
      iceServers: parseIceServers(),
    });

    localStream?.getTracks().forEach((track) => {
      if (localStream) {
        peerConnection.addTrack(track, localStream);
      }
    });

    peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        sendVoiceSignal(remoteUserId, 'VOICE_ICE_CANDIDATE', {
          candidate: event.candidate.toJSON(),
        });
      }
    };

    peerConnection.ontrack = (event) => {
      const [stream] = event.streams;
      if (!stream) {
        return;
      }

      remoteStreams.value = [
        ...remoteStreams.value.filter((remote) => remote.userId !== remoteUserId),
        { userId: remoteUserId, stream },
      ];
      setupVoiceAnalyser(remoteUserId, stream);
    };

    peerConnection.onconnectionstatechange = () => {
      if (['failed', 'disconnected'].includes(peerConnection.connectionState)) {
        voiceError.value = '语音连接不稳定，可能需要配置 TURN 中继服务器。';
      }
    };

    peerConnection.oniceconnectionstatechange = () => {
      if (['failed', 'disconnected'].includes(peerConnection.iceConnectionState)) {
        voiceError.value = '无法建立稳定的语音媒体连接，请检查 TURN/UDP 网络配置。';
      }
    };

    peerConnections.set(remoteUserId, peerConnection);
    return peerConnection;
  };

  const createOffer = async (remoteUserId: number) => {
    const peerConnection = createPeerConnection(remoteUserId);
    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);
    sendVoiceSignal(remoteUserId, 'VOICE_OFFER', { description: offer });
  };

  const acceptOffer = async (remoteUserId: number, description: RTCSessionDescriptionInit) => {
    const peerConnection = createPeerConnection(remoteUserId);
    await peerConnection.setRemoteDescription(description);
    await flushPendingIceCandidates(remoteUserId);
    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);
    sendVoiceSignal(remoteUserId, 'VOICE_ANSWER', { description: answer });
  };

  const addIceCandidate = async (remoteUserId: number, candidate: RTCIceCandidateInit) => {
    const peerConnection = peerConnections.get(remoteUserId);
    if (!peerConnection?.remoteDescription) {
      pendingIceCandidates.set(remoteUserId, [
        ...(pendingIceCandidates.get(remoteUserId) || []),
        candidate,
      ]);
      return;
    }

    await peerConnection.addIceCandidate(candidate);
  };

  const flushPendingIceCandidates = async (remoteUserId: number) => {
    const peerConnection = peerConnections.get(remoteUserId);
    const candidates = pendingIceCandidates.get(remoteUserId) || [];
    if (!peerConnection?.remoteDescription || candidates.length === 0) {
      return;
    }

    pendingIceCandidates.delete(remoteUserId);
    for (const candidate of candidates) {
      await peerConnection.addIceCandidate(candidate);
    }
  };

  const ensureAudioContext = () => {
    if (!audioContext) {
      audioContext = new AudioContext();
    }

    return audioContext;
  };

  const setupVoiceAnalyser = (
    userId: number,
    stream: MediaStream,
    shouldMeasure: () => boolean = () => true
  ) => {
    removeVoiceAnalyser(userId);

    const audioTracks = stream.getAudioTracks();
    if (audioTracks.length === 0) {
      return;
    }

    const context = ensureAudioContext();
    const analyser = context.createAnalyser();
    analyser.fftSize = 512;
    analyser.smoothingTimeConstant = 0.62;

    const source = context.createMediaStreamSource(stream);
    source.connect(analyser);

    const data = new Uint8Array(analyser.fftSize);
    const tick = () => {
      const currentAnalyser = voiceAnalysers.get(userId);
      if (!currentAnalyser) {
        return;
      }

      let level = 0;
      if (shouldMeasure()) {
        analyser.getByteTimeDomainData(data);
        let sum = 0;
        for (const value of data) {
          const centeredValue = (value - 128) / 128;
          sum += centeredValue * centeredValue;
        }
        level = Math.min(1, Math.sqrt(sum / data.length) * 5);
      }

      setVoiceLevel(userId, level);
      currentAnalyser.frameId = window.requestAnimationFrame(tick);
    };

    voiceAnalysers.set(userId, {
      analyser,
      data,
      frameId: window.requestAnimationFrame(tick),
      source,
    });
  };

  const setVoiceLevel = (userId: number, level: number) => {
    const previousLevel = voiceLevels.value[userId] || 0;
    const smoothedLevel = previousLevel * 0.55 + level * 0.45;
    voiceLevels.value = {
      ...voiceLevels.value,
      [userId]: smoothedLevel,
    };

    const nextSpeakingUserIds = new Set(speakingUserIds.value);
    if (smoothedLevel >= 0.12) {
      nextSpeakingUserIds.add(userId);
    } else if (smoothedLevel <= 0.07) {
      nextSpeakingUserIds.delete(userId);
    }
    speakingUserIds.value = nextSpeakingUserIds;
  };

  const removeVoiceAnalyser = (userId: number) => {
    const analyser = voiceAnalysers.get(userId);
    if (analyser) {
      window.cancelAnimationFrame(analyser.frameId);
      analyser.source.disconnect();
      voiceAnalysers.delete(userId);
    }

    const nextVoiceLevels = { ...voiceLevels.value };
    delete nextVoiceLevels[userId];
    voiceLevels.value = nextVoiceLevels;

    if (speakingUserIds.value.has(userId)) {
      const nextSpeakingUserIds = new Set(speakingUserIds.value);
      nextSpeakingUserIds.delete(userId);
      speakingUserIds.value = nextSpeakingUserIds;
    }
  };

  const cleanupVoiceAnalysers = () => {
    [...voiceAnalysers.keys()].forEach(removeVoiceAnalyser);
    speakingUserIds.value = new Set();
    voiceLevels.value = {};
  };

  const sendVoiceSignal = (
    targetUserId: number,
    type: VoiceSignalType,
    payload: Partial<VoiceSignalMessage>
  ) => {
    if (!activeChannelId.value) {
      return;
    }

    websocketService.emit(`/voice/${activeChannelId.value}/signal`, {
      channelId: activeChannelId.value,
      targetUserId,
      type,
      ...payload,
    });
  };

  const closePeer = (remoteUserId: number) => {
    peerConnections.get(remoteUserId)?.close();
    peerConnections.delete(remoteUserId);
    pendingIceCandidates.delete(remoteUserId);
    removeVoiceAnalyser(remoteUserId);
    remoteStreams.value = remoteStreams.value.filter((remote) => remote.userId !== remoteUserId);
    participants.value = participants.value.filter((participant) => participant.id !== remoteUserId);
  };

  const cleanupVoice = (resetError: boolean) => {
    unsubscribeVoice?.();
    unsubscribeVoice = null;

    peerConnections.forEach((peerConnection) => peerConnection.close());
    peerConnections.clear();
    pendingIceCandidates.clear();
    cleanupVoiceAnalysers();

    localStream?.getTracks().forEach((track) => track.stop());
    localStream = null;
    remoteStreams.value = [];
    participants.value = [];
    activeChannelId.value = null;
    activeChannelName.value = '';
    activeServerId.value = null;
    activeServerName.value = '';
    isJoined.value = false;
    isConnecting.value = false;
    isMuted.value = false;
    isDeafened.value = false;

    if (resetError) {
      voiceError.value = '';
    }
  };

  const dedupeParticipants = (items: VoiceUser[]) => {
    const participantMap = new Map<number, VoiceUser>();
    items.forEach((participant) => {
      if (participant.id) {
        participantMap.set(participant.id, participant);
      }
    });
    return [...participantMap.values()];
  };

  const isUserSpeaking = (userId: number) => speakingUserIds.value.has(userId);
  const getVoiceLevel = (userId: number) => voiceLevels.value[userId] || 0;

  return {
    activeChannelId,
    activeChannelName,
    activeServerId,
    activeServerName,
    isJoined,
    isConnecting,
    isMuted,
    isDeafened,
    voiceError,
    participants,
    speakingUserIds,
    voiceLevels,
    visibleParticipants,
    participantCount,
    remoteStreams,
    statusText,
    selfVoiceStatus,
    displayNameOf,
    isUserSpeaking,
    getVoiceLevel,
    isActiveChannel,
    joinChannel,
    startDirectCall,
    leaveChannel,
    toggleMute,
    toggleDeafen,
  };
});
