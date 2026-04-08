package cn.cctstudio.nexacord.dto.websocket;

import lombok.Data;

@Data
public class WebSocketMessage {
    private Long id;
    private Long channelId;
    private String content;
    private UserInfo author;
    private String timestamp;
    private WebSocketMessageType type;

    public enum WebSocketMessageType {
        CHAT, TYPING, STOP_TYPING, USER_JOIN, USER_LEAVE, ONLINE_STATUS, ERROR
    }

    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String avatarUrl;
        private String status;
    }
}