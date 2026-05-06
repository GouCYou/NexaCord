package cn.cctstudio.nexacord.dto;

import cn.cctstudio.nexacord.model.ServerInvite;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ServerInviteResponse {
    private Long id;
    private String code;
    private Long serverId;
    private String serverName;
    private String serverIconUrl;
    private String creatorName;
    private Instant expiresAt;
    private Integer maxUses;
    private Integer useCount;
    private Instant createdAt;

    public static ServerInviteResponse from(ServerInvite invite) {
        return ServerInviteResponse.builder()
                .id(invite.getId())
                .code(invite.getCode())
                .serverId(invite.getServer().getId())
                .serverName(invite.getServer().getName())
                .serverIconUrl(invite.getServer().getIconUrl())
                .creatorName(invite.getCreator().getUsername())
                .expiresAt(invite.getExpiresAt())
                .maxUses(invite.getMaxUses())
                .useCount(invite.getUseCount())
                .createdAt(invite.getCreatedAt())
                .build();
    }
}
