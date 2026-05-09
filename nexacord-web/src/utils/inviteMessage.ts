import type { ServerInvite } from '../types';

export type DirectInvitePayload = {
  type: 'server_invite';
  code: string;
  serverId: number;
  serverName: string;
  serverIconUrl: string | null;
  channelId?: number | null;
  channelName?: string | null;
  channelType?: 'TEXT' | 'VOICE' | 'CATEGORY' | null;
  creatorName?: string;
};

const INVITE_PREFIX = 'nexacord:invite:';

export const buildDirectInviteMessage = (invite: ServerInvite): string =>
  `${INVITE_PREFIX}${JSON.stringify({
    type: 'server_invite',
    code: invite.code,
    serverId: invite.serverId,
    serverName: invite.serverName,
    serverIconUrl: invite.serverIconUrl,
    channelId: invite.channelId ?? null,
    channelName: invite.channelName ?? null,
    channelType: invite.channelType ?? null,
    creatorName: invite.creatorName,
  } satisfies DirectInvitePayload)}`;

export const parseDirectInviteMessage = (content: string | null | undefined): DirectInvitePayload | null => {
  if (!content?.startsWith(INVITE_PREFIX)) {
    return null;
  }

  try {
    const payload = JSON.parse(content.slice(INVITE_PREFIX.length)) as DirectInvitePayload;
    if (
      payload?.type !== 'server_invite' ||
      !payload.code ||
      !payload.serverId ||
      !payload.serverName
    ) {
      return null;
    }

    return payload;
  } catch {
    return null;
  }
};
