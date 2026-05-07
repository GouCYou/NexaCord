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

  const peerConnections = new Map<number, RTCPeerConnection>();
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

      if (!subscribeVoiceTopic(options.channelId)) {
        throw new Error('无法订阅语音频道。');
      }

      activeChannelId.value = options.channelId;
      activeChannelName.value = options.channelName;
      activeServerId.value = options.serverId ?? null;
      activeServerName.value = options.serverName || '';
      isJoined.value = true;
      participants.value = [toVoiceUser(currentUser.value)];

      websocketService.emit(`/voice/${options.channelId}/join`, {});
    } catch (error: any) {
      voiceError.value = error?.message || '无法加入语音频道，请检查麦克风权限。';
      cleanupVoice(false);
    } finally {
      isConnecting.value = false;
    }
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
      await peerConnections.get(senderId)?.setRemoteDescription(message.description);
      return;
    }

    if (message.type === 'VOICE_ICE_CANDIDATE' && message.candidate) {
      await peerConnections.get(senderId)?.addIceCandidate(message.candidate);
    }
  };

  const createPeerConnection = (remoteUserId: number) => {
    const existingPeer = peerConnections.get(remoteUserId);
    if (existingPeer) {
      return existingPeer;
    }

    const peerConnection = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
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
    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);
    sendVoiceSignal(remoteUserId, 'VOICE_ANSWER', { description: answer });
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
    remoteStreams.value = remoteStreams.value.filter((remote) => remote.userId !== remoteUserId);
    participants.value = participants.value.filter((participant) => participant.id !== remoteUserId);
  };

  const cleanupVoice = (resetError: boolean) => {
    unsubscribeVoice?.();
    unsubscribeVoice = null;

    peerConnections.forEach((peerConnection) => peerConnection.close());
    peerConnections.clear();

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
    visibleParticipants,
    participantCount,
    remoteStreams,
    statusText,
    selfVoiceStatus,
    displayNameOf,
    isActiveChannel,
    joinChannel,
    leaveChannel,
    toggleMute,
    toggleDeafen,
  };
});
