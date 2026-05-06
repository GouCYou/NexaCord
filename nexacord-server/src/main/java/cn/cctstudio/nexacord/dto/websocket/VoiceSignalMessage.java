package cn.cctstudio.nexacord.dto.websocket;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VoiceSignalMessage {
    private Long channelId;
    private VoiceSignalType type;
    private Long targetUserId;
    private UserInfo sender;
    private List<UserInfo> participants;
    private Map<String, Object> description;
    private Map<String, Object> candidate;

    public enum VoiceSignalType {
        VOICE_JOIN,
        VOICE_LEAVE,
        VOICE_OFFER,
        VOICE_ANSWER,
        VOICE_ICE_CANDIDATE
    }

    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String displayName;
        private String avatarUrl;
        private String status;
    }
}
