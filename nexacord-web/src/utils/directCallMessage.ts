export type DirectCallMessagePayload = {
  type: 'direct_call';
  status: 'ended' | 'declined' | 'cancelled';
  durationSeconds: number;
  callerId?: number;
  calleeId?: number;
  startedAt?: string | null;
  endedAt?: string | null;
};

const DIRECT_CALL_PREFIX = 'nexacord:call:';

export const parseDirectCallMessage = (
  content: string | null | undefined
): DirectCallMessagePayload | null => {
  if (!content?.startsWith(DIRECT_CALL_PREFIX)) {
    return null;
  }

  try {
    const payload = JSON.parse(content.slice(DIRECT_CALL_PREFIX.length)) as DirectCallMessagePayload;
    if (
      payload?.type !== 'direct_call' ||
      !['ended', 'declined', 'cancelled'].includes(payload.status)
    ) {
      return null;
    }

    return {
      ...payload,
      durationSeconds: Math.max(0, Math.floor(Number(payload.durationSeconds) || 0)),
    };
  } catch {
    return null;
  }
};

export const formatCallTimer = (totalSeconds: number) => {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;
  const paddedMinutes = hours > 0 ? String(minutes).padStart(2, '0') : String(minutes);
  const paddedSeconds = String(remainingSeconds).padStart(2, '0');

  return hours > 0
    ? `${hours}:${paddedMinutes}:${paddedSeconds}`
    : `${paddedMinutes}:${paddedSeconds}`;
};

export const formatCallDuration = (totalSeconds: number) => {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const remainingSeconds = seconds % 60;

  if (hours > 0) {
    return `${hours} 小时 ${minutes} 分 ${remainingSeconds} 秒`;
  }

  if (minutes > 0) {
    return `${minutes} 分 ${remainingSeconds} 秒`;
  }

  return `${remainingSeconds} 秒`;
};
