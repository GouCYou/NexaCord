package cn.cctstudio.nexacord.dto.websocket;

import lombok.Data;

@Data
public class DirectCallSignalMessage {
    private Long channelId;
    private DirectCallType type;
    private UserInfo caller;
    private UserInfo callee;
    private String reason;

    public enum DirectCallType {
        DIRECT_CALL_REQUEST,
        DIRECT_CALL_ACCEPT,
        DIRECT_CALL_DECLINE,
        DIRECT_CALL_CANCEL,
        DIRECT_CALL_END
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
