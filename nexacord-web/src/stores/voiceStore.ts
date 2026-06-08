import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import websocketService from '../services/websocketService';
import { useUserStore } from './userStore';
import type { User } from '../types';
import { formatCallTimer } from '../utils/directCallMessage';
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

type JoinChannelOptions = {
  channelId: number;
  channelName: string;
  serverId?: number | null;
  serverName?: string | null;
  directPeer?: VoiceUser | null;
  callStartedAt?: string | null;
};

type VoiceStateMessage = {
  participantsByChannel?: Record<string, VoiceUser[]>;
};

type DirectCallSignalMessage = {
  channelId: number;
  type:
    | 'DIRECT_CALL_REQUEST'
    | 'DIRECT_CALL_ACCEPT'
    | 'DIRECT_CALL_DECLINE'
    | 'DIRECT_CALL_CANCEL'
    | 'DIRECT_CALL_END';
  caller: VoiceUser;
  callee: VoiceUser;
  reason?: string;
  startedAt?: string | null;
  durationSeconds?: number | null;
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
  const participantsByChannel = ref<Record<number, VoiceUser[]>>({});
  const remoteStreams = ref<Array<{ userId: number; stream: MediaStream }>>([]);
  const speakingUserIds = ref<Set<number>>(new Set());
  const voiceLevels = ref<Record<number, number>>({});
  const inputVolume = ref(100);
  const outputVolume = ref(100);
  const userVolumes = ref<Record<number, number>>({});
  const incomingCall = ref<DirectCallSignalMessage | null>(null);
  const outgoingCall = ref<DirectCallSignalMessage | null>(null);
  const activeDirectPeer = ref<VoiceUser | null>(null);
  const activeCallStartedAt = ref<string | null>(null);
  const activeCallElapsedSeconds = ref(0);

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
  let outboundStream: MediaStream | null = null;
  let localAudioSource: MediaStreamAudioSourceNode | null = null;
  let microphoneGainNode: GainNode | null = null;
  let localAudioDestination: MediaStreamAudioDestinationNode | null = null;
  let unsubscribeVoice: (() => void) | null = null;
  let unsubscribeVoiceState: (() => void) | null = null;
  let unsubscribeDirectCall: (() => void) | null = null;
  let realtimeInitialized = false;
  let subscribedDirectCallUserId: number | null = null;
  let lastJoinOptions: JoinChannelOptions | null = null;
  let reconnectTimer: number | null = null;
  let reconnectAttempts = 0;
  let directCallPeer: VoiceUser | null = null;
  let callTimer: number | null = null;

  const currentUser = computed(() => userStore.currentUser);

  const visibleParticipants = computed(() => {
    const participantMap = new Map<number, VoiceUser>();
    const activeParticipants = activeChannelId.value
      ? participantsByChannel.value[activeChannelId.value] || participants.value
      : participants.value;

    activeParticipants.forEach((participant) => {
      participantMap.set(participant.id, participant);
    });

    if (isJoined.value && currentUser.value) {
      participantMap.set(currentUser.value.id, toVoiceUser(currentUser.value));
    }

    return [...participantMap.values()];
  });

  const participantCount = computed(() => visibleParticipants.value.length);
  const isDirectCall = computed(() => Boolean(activeDirectPeer.value));

  const statusText = computed(() => {
    if (!isJoined.value) {
      return '加入后即可和同频道成员实时语音通话。';
    }

    if (activeDirectPeer.value) {
      return `正在与 ${displayNameOf(activeDirectPeer.value)} 通话，已通话 ${formatCallTimer(activeCallElapsedSeconds.value)}。`;
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

    if (activeDirectPeer.value) {
      return '私聊通话中';
    }

    return '你正在频道中';
  });

  const isActiveChannel = (channelId: number) =>
    isJoined.value && activeChannelId.value === channelId;

  const getParticipantsForChannel = (channelId: number) => participantsByChannel.value[channelId] || [];
  const getParticipantCountForChannel = (channelId: number) => getParticipantsForChannel(channelId).length;

  const subscribeVoiceTopic = (channelId: number) => {
    unsubscribeVoice?.();
    unsubscribeVoice = websocketService.subscribe(`/topic/voice/${channelId}`, (payload) => {
      void handleVoiceSignal(payload as VoiceSignalMessage);
    });

    return Boolean(unsubscribeVoice);
  };

  const subscribeVoiceState = () => {
    if (!websocketService.isConnected() || unsubscribeVoiceState) {
      return;
    }

    unsubscribeVoiceState = websocketService.subscribe('/topic/voice/state', (payload) => {
      const state = (payload as VoiceStateMessage)?.participantsByChannel || {};
      const nextState: Record<number, VoiceUser[]> = {};
      Object.entries(state).forEach(([channelId, channelParticipants]) => {
        nextState[Number(channelId)] = dedupeParticipants(channelParticipants || []);
      });
      participantsByChannel.value = nextState;
      if (activeChannelId.value) {
        participants.value = nextState[activeChannelId.value] || participants.value;
      }
    });

    websocketService.emit('/voice/state', {});
  };

  const subscribeDirectCall = () => {
    const userId = currentUser.value?.id;
    if (!userId || !websocketService.isConnected() || subscribedDirectCallUserId === userId) {
      return;
    }

    unsubscribeDirectCall?.();
    unsubscribeDirectCall = websocketService.subscribe(`/topic/direct-call/user/${userId}`, (payload) => {
      void handleDirectCallSignal(payload as DirectCallSignalMessage);
    });
    subscribedDirectCallUserId = unsubscribeDirectCall ? userId : null;
  };

  const handleRealtimeConnect = () => {
    subscribeVoiceState();
    subscribeDirectCall();
    if (lastJoinOptions && !isJoined.value && reconnectAttempts > 0) {
      scheduleVoiceReconnect('实时连接已恢复，正在重连语音。');
    }
  };

  const initializeRealtime = () => {
    if (realtimeInitialized) {
      subscribeVoiceState();
      subscribeDirectCall();
      return;
    }

    websocketService.on('connect', handleRealtimeConnect);
    websocketService.on('disconnect', () => {
      unsubscribeVoiceState?.();
      unsubscribeVoiceState = null;
      unsubscribeDirectCall?.();
      unsubscribeDirectCall = null;
      subscribedDirectCallUserId = null;
      if (isJoined.value && lastJoinOptions) {
        cleanupVoice(false, true);
        scheduleVoiceReconnect('实时连接已断开，正在等待自动重连。');
      }
    });
    realtimeInitialized = true;
    subscribeVoiceState();
    subscribeDirectCall();
  };

  const joinChannel = async (options: JoinChannelOptions, meta: { reconnecting?: boolean } = {}) => {
    if (!currentUser.value || isConnecting.value) {
      return;
    }

    if (isActiveChannel(options.channelId)) {
      return;
    }

    if (isJoined.value) {
      leaveChannel(false);
    }

    clearReconnectTimer();
    if (!meta.reconnecting) {
      reconnectAttempts = 0;
    }
    voiceError.value = meta.reconnecting ? '正在自动重连语音……' : '';
    isConnecting.value = true;
    lastJoinOptions = options;

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
      setupLocalAudioPipeline(localStream);
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
      directCallPeer = options.directPeer || null;
      activeDirectPeer.value = options.directPeer || null;
      if (options.directPeer) {
        startCallTimer(options.callStartedAt);
      } else {
        stopCallTimer();
      }
      isJoined.value = true;
      participants.value = [toVoiceUser(currentUser.value)];
      participantsByChannel.value = {
        ...participantsByChannel.value,
        [options.channelId]: participants.value,
      };
      setupVoiceAnalyser(currentUser.value.id, localStream, () => !isMuted.value);

      websocketService.emit(`/voice/${options.channelId}/join`, {});
      reconnectAttempts = 0;
      voiceError.value = '';
    } catch (error: any) {
      voiceError.value = error?.message || '无法加入语音频道，请检查麦克风权限。';
      cleanupVoice(false, true);
      if (meta.reconnecting) {
        scheduleVoiceReconnect('语音重连失败，稍后继续尝试。');
      }
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

    if (!websocketService.isConnected()) {
      voiceError.value = '实时连接还没有准备好，请稍后再试。';
      return;
    }

    const callee = {
      id: user.id,
      username: user.username,
      displayName: user.displayName || null,
      avatarUrl: user.avatarUrl || null,
      status: user.status,
    };
    outgoingCall.value = {
      channelId: directRoomIdFor(currentUser.value.id, user.id),
      type: 'DIRECT_CALL_REQUEST',
      caller: toVoiceUser(currentUser.value),
      callee,
    };
    websocketService.emit(`/direct-call/${user.id}/request`, {});
  };

  const leaveChannel = (notifyDirectCall = true) => {
    clearReconnectTimer();
    const peer = directCallPeer;
    if (isJoined.value && activeChannelId.value) {
      websocketService.emit(`/voice/${activeChannelId.value}/leave`, {});
    }

    if (notifyDirectCall && peer) {
      websocketService.emit(`/direct-call/${peer.id}/end`, {});
    }

    lastJoinOptions = null;
    reconnectAttempts = 0;
    directCallPeer = null;
    activeDirectPeer.value = null;
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
    updateMicrophoneGain();
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
      const nextParticipants = dedupeParticipants(message.participants);
      participants.value = nextParticipants;
      participantsByChannel.value = {
        ...participantsByChannel.value,
        [message.channelId]: nextParticipants,
      };
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

    const streamForPeers = outboundStream || localStream;
    streamForPeers?.getTracks().forEach((track) => {
      if (streamForPeers) {
        peerConnection.addTrack(track, streamForPeers);
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
        scheduleVoiceReconnect('语音连接断开，正在尝试自动重连。');
      }
    };

    peerConnection.oniceconnectionstatechange = () => {
      if (['failed', 'disconnected'].includes(peerConnection.iceConnectionState)) {
        voiceError.value = '无法建立稳定的语音媒体连接，请检查 TURN/UDP 网络配置。';
        scheduleVoiceReconnect('媒体连接断开，正在尝试自动重连。');
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

  const setupLocalAudioPipeline = (stream: MediaStream) => {
    cleanupLocalAudioPipeline();
    const context = ensureAudioContext();
    localAudioSource = context.createMediaStreamSource(stream);
    microphoneGainNode = context.createGain();
    localAudioDestination = context.createMediaStreamDestination();
    localAudioSource.connect(microphoneGainNode);
    microphoneGainNode.connect(localAudioDestination);
    outboundStream = localAudioDestination.stream;
    updateMicrophoneGain();
  };

  const updateMicrophoneGain = () => {
    if (!microphoneGainNode) {
      return;
    }

    microphoneGainNode.gain.value = isMuted.value ? 0 : inputVolume.value / 100;
  };

  const cleanupLocalAudioPipeline = () => {
    localAudioSource?.disconnect();
    microphoneGainNode?.disconnect();
    localAudioSource = null;
    microphoneGainNode = null;
    localAudioDestination = null;
    outboundStream = null;
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
    if (activeChannelId.value) {
      participantsByChannel.value = {
        ...participantsByChannel.value,
        [activeChannelId.value]: participants.value,
      };
    }
  };

  const cleanupVoice = (resetError: boolean, preserveReconnect = false) => {
    unsubscribeVoice?.();
    unsubscribeVoice = null;

    peerConnections.forEach((peerConnection) => peerConnection.close());
    peerConnections.clear();
    pendingIceCandidates.clear();
    cleanupVoiceAnalysers();
    cleanupLocalAudioPipeline();

    localStream?.getTracks().forEach((track) => track.stop());
    localStream = null;
    remoteStreams.value = [];
    participants.value = [];
    activeChannelId.value = null;
    activeChannelName.value = '';
    activeServerId.value = null;
    activeServerName.value = '';
    directCallPeer = preserveReconnect ? directCallPeer : null;
    activeDirectPeer.value = preserveReconnect ? activeDirectPeer.value : null;
    if (!preserveReconnect) {
      stopCallTimer();
    }
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

  const handleDirectCallSignal = async (message: DirectCallSignalMessage) => {
    const user = currentUser.value;
    if (!user || !message?.type) {
      return;
    }

    const isCaller = message.caller?.id === user.id;
    const isCallee = message.callee?.id === user.id;
    if (!isCaller && !isCallee) {
      return;
    }

    if (message.type === 'DIRECT_CALL_REQUEST' && isCallee) {
      incomingCall.value = message;
      return;
    }

    if (message.type === 'DIRECT_CALL_ACCEPT') {
      incomingCall.value = null;
      outgoingCall.value = null;
      const peer = isCaller ? message.callee : message.caller;
      await joinChannel({
        channelId: message.channelId,
        channelName: `与 ${displayNameOf(peer)} 的通话`,
        serverId: null,
        serverName: '私信',
        directPeer: peer,
        callStartedAt: message.startedAt || null,
      });
      return;
    }

    if (['DIRECT_CALL_DECLINE', 'DIRECT_CALL_CANCEL', 'DIRECT_CALL_END'].includes(message.type)) {
      incomingCall.value = null;
      outgoingCall.value = null;
      if (message.type === 'DIRECT_CALL_END' && activeChannelId.value === message.channelId) {
        leaveChannel(false);
      }
    }
  };

  const acceptIncomingCall = () => {
    const call = incomingCall.value;
    if (!call) {
      return;
    }

    websocketService.emit(`/direct-call/${call.caller.id}/accept`, {});
  };

  const declineIncomingCall = () => {
    const call = incomingCall.value;
    if (!call) {
      return;
    }

    websocketService.emit(`/direct-call/${call.caller.id}/decline`, {});
    incomingCall.value = null;
  };

  const cancelOutgoingCall = () => {
    const call = outgoingCall.value;
    if (!call) {
      return;
    }

    websocketService.emit(`/direct-call/${call.callee.id}/cancel`, {});
    outgoingCall.value = null;
  };

  const startCallTimer = (startedAt?: string | null) => {
    stopCallTimer();
    const startedTime = startedAt ? new Date(startedAt).getTime() : Date.now();
    const safeStartedTime = Number.isFinite(startedTime) ? startedTime : Date.now();
    activeCallStartedAt.value = new Date(safeStartedTime).toISOString();

    const updateTimer = () => {
      activeCallElapsedSeconds.value = Math.max(
        0,
        Math.floor((Date.now() - safeStartedTime) / 1000)
      );
    };
    updateTimer();
    callTimer = window.setInterval(updateTimer, 1000);
  };

  const stopCallTimer = () => {
    if (callTimer != null) {
      window.clearInterval(callTimer);
      callTimer = null;
    }
    activeCallStartedAt.value = null;
    activeCallElapsedSeconds.value = 0;
  };

  const clearReconnectTimer = () => {
    if (reconnectTimer != null) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  };

  const scheduleVoiceReconnect = (message: string) => {
    if (!lastJoinOptions || reconnectTimer != null) {
      return;
    }

    if (reconnectAttempts >= 5) {
      voiceError.value = '语音自动重连失败，请手动重新加入。';
      lastJoinOptions = null;
      return;
    }

    reconnectAttempts += 1;
    voiceError.value = message;
    reconnectTimer = window.setTimeout(() => {
      const options = lastJoinOptions;
      reconnectTimer = null;
      if (!options || !websocketService.isConnected()) {
        scheduleVoiceReconnect('实时连接尚未恢复，继续等待自动重连。');
        return;
      }

      cleanupVoice(false, true);
      void joinChannel(options, { reconnecting: true });
    }, Math.min(8000, 900 * reconnectAttempts));
  };

  const clampVolume = (value: number) => Math.min(200, Math.max(0, Math.round(value)));

  const setInputVolume = (value: number) => {
    inputVolume.value = clampVolume(value);
    updateMicrophoneGain();
  };

  const setOutputVolume = (value: number) => {
    outputVolume.value = clampVolume(value);
  };

  const setUserVolume = (userId: number, value: number) => {
    userVolumes.value = {
      ...userVolumes.value,
      [userId]: clampVolume(value),
    };
  };

  const getUserVolume = (userId: number) => userVolumes.value[userId] ?? 100;
  const getPlaybackGain = (userId: number) => {
    if (isDeafened.value) {
      return 0;
    }

    return (outputVolume.value / 100) * (getUserVolume(userId) / 100);
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
    participantsByChannel,
    speakingUserIds,
    voiceLevels,
    inputVolume,
    outputVolume,
    userVolumes,
    incomingCall,
    outgoingCall,
    activeDirectPeer,
    activeCallStartedAt,
    activeCallElapsedSeconds,
    isDirectCall,
    visibleParticipants,
    participantCount,
    remoteStreams,
    statusText,
    selfVoiceStatus,
    displayNameOf,
    getParticipantsForChannel,
    getParticipantCountForChannel,
    isUserSpeaking,
    getVoiceLevel,
    getUserVolume,
    getPlaybackGain,
    isActiveChannel,
    initializeRealtime,
    joinChannel,
    startDirectCall,
    acceptIncomingCall,
    declineIncomingCall,
    cancelOutgoingCall,
    leaveChannel,
    toggleMute,
    toggleDeafen,
    setInputVolume,
    setOutputVolume,
    setUserVolume,
  };
});
